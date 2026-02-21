"""
Main verification service orchestrating the AI pipeline.
Coordinates pre-filter, fake detection, YOLO, and Gemini.
"""

from typing import Dict, Optional, Any
from pathlib import Path
from loguru import logger
import tempfile
import requests

from app.ai.prefilter import get_prefilter
from app.ai.fake_detector import get_fake_detector
from app.ai.object_detector import get_object_detector
from app.ai.gemini_client import get_gemini_client
from app.ai.embeddings import get_embedding_generator
from app.database import get_supabase
from app.config import get_settings
from app.services.firebase_sync import get_firebase_sync_service

settings = get_settings()


class VerificationService:
    """Orchestrates the complete AI verification pipeline."""
    
    def __init__(self):
        """Initialize verification service with all AI components."""
        self.prefilter = get_prefilter()
        self.fake_detector = get_fake_detector()
        self.object_detector = get_object_detector()
        self.gemini_client = get_gemini_client()
        self.embedding_generator = get_embedding_generator()
        self.supabase = get_supabase()
        self.firebase_sync = get_firebase_sync_service()
    
    async def verify_report(self, report_id: str) -> Dict[str, Any]:
        """
        Complete verification pipeline for a report.
        
        Args:
            report_id: UUID of the report to verify
            
        Returns:
            Dict with verification results
        """
        logger.info(f"🔍 Starting verification for report: {report_id}")
        
        try:
            # 1. Fetch report from database
            report = self._get_report(report_id)
            if not report:
                logger.error(f"Report not found: {report_id}")
                return {"success": False, "reason": "Report not found"}
            
            # 2. Download image
            image_path = await self._download_image(report["image_url"])
            if not image_path:
                return self._reject_report(report_id, "Failed to download image", report=report)
            
            # 3. Get existing hashes for duplicate detection
            existing_hashes = self._get_existing_hashes(
                report["user_id"], 
                report["incident_type"]  # Changed from hazard_type
            )
            
            # 4. Stage 1: Pre-filter
            logger.info("📋 Stage 1: Pre-filter checks")
            prefilter_result = self.prefilter.check_all(
                image_path, 
                existing_hashes=existing_hashes
            )
            
            if not prefilter_result["valid"]:
                return self._reject_report(
                    report_id, 
                    prefilter_result["reason"],
                    report=report,
                )
            
            image_hash = prefilter_result["image_hash"]
            
            # 5. Stage 2: AI-generated detection
            logger.info("🤖 Stage 2: AI-generated image detection")
            fake_result = self.fake_detector.detect_ai_generated(image_path)
            
            if fake_result["is_ai_generated"]:
                return self._reject_report(
                    report_id,
                    f"AI-generated image detected ({fake_result['probability']:.2%})",
                    report=report,
                    # ai_gen_probability=fake_result["probability"]  # Column not in DB yet
                )
            
            # 6. Stage 3: YOLO object detection
            logger.info("👁️ Stage 3: Object detection (YOLO)")
            yolo_result = self.object_detector.analyze_hazard(
                image_path,
                report["incident_type"]  # Changed from hazard_type
            )
            
            yolo_score = yolo_result["match_score"]
            
            # 7. Stage 4: Gemini reasoning
            logger.info("🧠 Stage 4: Gemini multimodal reasoning")
            gemini_result = self.gemini_client.analyze_hazard_image(
                image_path,
                report["incident_type"],  # Changed from hazard_type
                report.get("description", ""),
                yolo_result
            )
            
            # 8. Compute final confidence
            user_trust = self._get_user_trust(report["user_id"])
            
            final_confidence = self._compute_final_confidence(
                yolo_score=yolo_score,
                gemini_confidence=gemini_result["confidence"] / 100,
                user_trust=user_trust,
                gemini_verdict=gemini_result["verdict"]
            )
            
            # 9. Make decision
            verdict, status = self._make_verdict(
                final_confidence, 
                gemini_result["verdict"]
            )
            
            # Log detailed verdict reasoning
            self._log_verdict_details(
                verdict=verdict,
                final_confidence=final_confidence,
                yolo_score=yolo_score,
                gemini_verdict=gemini_result["verdict"],
                gemini_confidence=gemini_result["confidence"],
                gemini_reasoning=gemini_result["reasoning"],
                user_trust=user_trust
            )
            
            # 10. Generate embeddings (if accepted)
            image_embedding = None
            text_embedding = None
            
            if verdict == "ACCEPTED":
                logger.info("🎨 Generating embeddings")
                image_embedding = self.embedding_generator.generate_image_embedding(image_path)
                if report.get("description"):
                    text_embedding = self.embedding_generator.generate_text_embedding(
                        report["description"]
                    )
            
            # 11. Update report in database
            self._update_report_verification(
                report_id=report_id,
                verdict=verdict,
                ai_confidence=final_confidence,
                ai_reasoning=gemini_result["reasoning"],
                yolo_detections=yolo_result["detections"],
                gemini_response=gemini_result,
                image_hash=image_hash,
                # ai_gen_probability=fake_result["probability"],  # Column not in DB yet
                prefilter_passed=True
            )

            # Sync status to Firestore for app UI
            status_map = {
                "ACCEPTED": "Verified",
                "REJECTED": "Rejected",
                "UNCERTAIN": "Pending"
            }
            status = status_map.get(verdict, "Pending")
            points_awarded = 10 if status == "Verified" else 0
            uid = report.get("user_id", "")
            logger.info(f"🔔 Pushing Firebase notification to user_id='{uid}', status={status}")
            self.firebase_sync.update_report_status(
                report_id=report_id,
                incident_type=report.get("incident_type", ""),
                status=status,
                confidence=final_confidence,
                reasoning=gemini_result.get("reasoning", ""),
                points_awarded=points_awarded,
                user_id=uid,
            )
            
            # 12. Store embeddings
            if image_embedding is not None:
                self._store_embeddings(
                    report_id=report_id,
                    image_embedding=image_embedding,
                    text_embedding=text_embedding
                )
            
            # 13. Process accepted report (clustering, confidence update)
            if verdict == "ACCEPTED":
                await self._process_accepted_report(report)
            
            # 14. Update user trust
            self._update_user_trust(report["user_id"], verdict)
            
            # Clean up temp file
            Path(image_path).unlink(missing_ok=True)
            
            return {
                "success": True,
                "verdict": verdict,
                "confidence": final_confidence,
                "reasoning": gemini_result["reasoning"]
            }
        
        except Exception as e:
            logger.error(f"Verification failed for {report_id}: {e}")
            return {"success": False, "reason": f"Verification error: {str(e)}"}
    
    def _compute_final_confidence(
        self,
        yolo_score: float,
        gemini_confidence: float,
        user_trust: float,
        gemini_verdict: str
    ) -> float:
        """
        Compute final confidence score using weighted formula.
        
        Formula:
        confidence = (YOLO × 0.3) + (Gemini × 0.4) + (UserTrust × 0.2) + (Base × 0.1)
        
        Args:
            yolo_score: YOLO match score (0-1)
            gemini_confidence: Gemini confidence (0-1)
            user_trust: User trust score (0-1)
            gemini_verdict: Gemini verdict (VALID/INVALID/UNCERTAIN)
        
        Returns:
            Final confidence (0-1)
        """
        # Base score
        base_score = 0.1
        
        # Weighted sum
        confidence = (
            yolo_score * 0.3 +
            gemini_confidence * 0.4 +
            user_trust * 0.2 +
            base_score
        )
        
        # Penalty for INVALID verdict
        if gemini_verdict == "INVALID":
            confidence *= 0.5
        
        # Clip to [0, 1]
        confidence = max(0.0, min(1.0, confidence))
        
        return confidence
    
    def _make_verdict(
        self, 
        final_confidence: float, 
        gemini_verdict: str
    ) -> tuple:
        """
        Make final verdict based on confidence and Gemini.
        
        Args:
            final_confidence: Final confidence score (0-1)
            gemini_verdict: Gemini's verdict
            
        Returns:
            Tuple of (verdict, status)
        """
        # Strong rejection from Gemini
        if gemini_verdict == "INVALID":
            return ("REJECTED", "REJECTED")
        
        # Confidence-based decision (adjusted for community safety)
        if final_confidence >= 0.65:  # Lowered from 0.70
            return ("ACCEPTED", "ACCEPTED")
        elif final_confidence < 0.30:  # Lowered from 0.40 to reduce false rejections
            return ("REJECTED", "REJECTED")
        else:
            # Uncertain - send to manual review (safety first!)
            return ("UNCERTAIN", "PENDING")
    
    def _log_verdict_details(
        self,
        verdict: str,
        final_confidence: float,
        yolo_score: float,
        gemini_verdict: str,
        gemini_confidence: float,
        gemini_reasoning: str,
        user_trust: float
    ) -> None:
        """
        Log detailed verdict information with breakdown of confidence scores.
        
        Args:
            verdict: Final verdict (ACCEPTED/REJECTED/UNCERTAIN)
            final_confidence: Final combined confidence score
            yolo_score: YOLO scene validation score
            gemini_verdict: Gemini's verdict
            gemini_confidence: Gemini's confidence percentage
            gemini_reasoning: Gemini's reasoning
            user_trust: User trust score
        """
        # Determine rejection reason
        if verdict == "REJECTED":
            if gemini_verdict == "INVALID":
                reason = f"Gemini rejected: {gemini_reasoning}"
            elif final_confidence < 0.30:
                reason = f"Confidence too low ({final_confidence:.2%} < 30%)"
            else:
                reason = f"Confidence below acceptance threshold ({final_confidence:.2%} < 65%)"
            
            logger.warning(
                f"❌ REJECTED - {reason}\n"
                f"   Breakdown: YOLO={yolo_score:.2%} | "
                f"Gemini={gemini_confidence:.1f}% ({gemini_verdict}) | "
                f"UserTrust={user_trust:.2%} | "
                f"Final={final_confidence:.2%}"
            )
        
        elif verdict == "ACCEPTED":
            logger.info(
                f"✅ ACCEPTED (confidence: {final_confidence:.2%})\n"
                f"   Breakdown: YOLO={yolo_score:.2%} | "
                f"Gemini={gemini_confidence:.1f}% ({gemini_verdict}) | "
                f"UserTrust={user_trust:.2%} | "
                f"Final={final_confidence:.2%}"
            )
        
        elif verdict == "UNCERTAIN":
            logger.info(
                f"⚠️  UNCERTAIN - Pending manual review (confidence: {final_confidence:.2%})\n"
                f"   Breakdown: YOLO={yolo_score:.2%} | "
                f"Gemini={gemini_confidence:.1f}% ({gemini_verdict}) | "
                f"UserTrust={user_trust:.2%} | "
                f"Final={final_confidence:.2%}"
            )
    
    def _reject_report(
        self, 
        report_id: str, 
        reason: str,
        report: Optional[Dict] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """
        Reject a report and update database. Also pushes a Firebase notification.
        """
        logger.warning(f"🚫 Rejecting report {report_id}: {reason}")
        
        update_data = {
            "status": "Rejected",
            "verification_confidence": 0,
            "ai_reasoning": reason,
            **{k: v for k, v in kwargs.items() if k != "report"},
        }
        
        self.supabase.table("reports_analysis").update(update_data).eq(
            "report_id", report_id
        ).execute()

        # Push rejection notification to Firebase if we have user context
        if report and report.get("user_id"):
            uid = report["user_id"]
            incident = report.get("incident_type", "hazard")
            logger.info(f"🔔 Pushing rejection notification to user_id='{uid}'")
            self.firebase_sync.update_report_status(
                report_id=report_id,
                incident_type=incident,
                status="Rejected",
                confidence=0.0,
                reasoning=reason,
                points_awarded=0,
                user_id=uid,
            )
        
        return {
            "success": True,
            "verdict": "REJECTED",
            "reason": reason
        }
    
    def _get_report(self, report_id: str) -> Optional[Dict]:
        """Fetch report from database."""
        try:
            response = self.supabase.table("reports_analysis").select("*").eq(
                "report_id", report_id
            ).execute()
            
            if response.data:
                return response.data[0]
            return None
        except Exception as e:
            logger.error(f"Failed to fetch report: {e}")
            return None
    
    async def _download_image(self, image_url: str) -> Optional[str]:
        """Download image from URL to temp file."""
        try:
            response = requests.get(image_url, timeout=30)
            response.raise_for_status()
            
            # Create temp file
            suffix = Path(image_url).suffix or ".jpg"
            with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as f:
                f.write(response.content)
                return f.name
        except Exception as e:
            logger.error(f"Failed to download image: {e}")
            return None
    
    def _get_existing_hashes(self, user_id: str, incident_type: str) -> list:
        """Get existing image hashes for duplicate detection."""
        try:
            # Fetch hashes from reports_analysis to prevent duplicates
            response = self.supabase.table("reports_analysis").select("image_hash").eq(
                "incident_type", incident_type
            ).not_.is_("image_hash", "null").limit(100).execute()
            
            if response.data:
                return [r["image_hash"] for r in response.data]
            return []
        except Exception as e:
            logger.debug(f"Failed to fetch hashes (column may not exist): {e}")
            return []
    
    def _get_user_trust(self, user_id: str) -> float:
        """Get user trust score (0-1)."""
        try:
            response = self.supabase.table("user_trust_scores").select("trust_score").eq(
                "user_id", user_id
            ).execute()
            
            if response.data:
                return response.data[0]["trust_score"] / 100
            return 0.5  # Default trust for new users
        except Exception as e:
            logger.error(f"Failed to fetch user trust: {e}")
            return 0.5
    
    def _update_report_verification(self, report_id: str, **kwargs):
        """Update report with verification results and sync to community_reports."""
        try:
            verdict = kwargs.get("verdict")
            status_map = {
                "ACCEPTED": "Verified",
                "REJECTED": "Rejected",
                "UNCERTAIN": "Pending"
            }
            status = status_map.get(verdict, "Pending")
            
            # 1. Update primary reports_analysis table
            update_payload = {
                "status": status,
                "verification_confidence": kwargs.get("ai_confidence", 0) * 100,
                "ai_reasoning": kwargs.get("ai_reasoning"),
                "image_hash": kwargs.get("image_hash")
            }
            
            self.supabase.table("reports_analysis").update(update_payload).eq("report_id", report_id).execute()
            
            # 2. If Accepted, sync to community_reports (Post-Analysis)
            if verdict == "ACCEPTED":
                # Fetch full report data to ensure we have all fields
                report = self._get_report(report_id)
                if report:
                    community_report = {
                        "report_id": report["report_id"],
                        "user_id": report["user_id"],
                        "hazard_type": report.get("incident_type", "hazard"),
                        "description": report.get("description"),
                        "image_url": report["image_url"],
                        "latitude": float(report["latitude"]),
                        "longitude": float(report["longitude"]),
                        # PostGIS expects POINT(longitude latitude)
                        "location": f"POINT({report['longitude']} {report['latitude']})",
                        "verification_status": "ACCEPTED",
                        "ai_verdict": "ACCEPTED",
                        "ai_confidence": kwargs.get("ai_confidence", 0) * 100,
                        "ai_reasoning": kwargs.get("ai_reasoning"),
                        "yolo_detections": kwargs.get("yolo_detections"),
                        "gemini_response": kwargs.get("gemini_response"),
                        "prefilter_passed": kwargs.get("prefilter_passed", True)
                    }
                    
                    self.supabase.table("community_reports").upsert(community_report).execute()
                    logger.info(f"Report {report_id} synced to community_reports")
            
            logger.debug(f"Updated report {report_id} with verification results")
        except Exception as e:
            logger.error(f"Failed to update report and sync: {e}")
    
    def _store_embeddings(
        self, 
        report_id: str, 
        image_embedding, 
        text_embedding
    ):
        """Store embeddings in database for similarity search."""
        try:
            embedding_record = {
                "report_id": report_id,
                # Convert numpy arrays to lists for Supabase
                "image_embedding": image_embedding.tolist() if hasattr(image_embedding, 'tolist') else image_embedding,
                "description_embedding": text_embedding.tolist() if text_embedding is not None and hasattr(text_embedding, 'tolist') else text_embedding
            }
            
            self.supabase.table("hazard_embeddings").upsert(embedding_record).execute()
            logger.info(f"Successfully stored embeddings for report {report_id}")
        except Exception as e:
            logger.error(f"Failed to store embeddings for {report_id}: {e}")
    
    async def _process_accepted_report(self, report: Dict):
        """Process accepted report (clustering, confidence update)."""
        try:
            logger.info(f"Processing accepted report: {report['report_id']}")
            
            # Import services
            from app.services.geo_clustering import get_geo_clustering_service
            from app.services.confidence import get_confidence_service
            
            geo_service = get_geo_clustering_service()
            confidence_service = get_confidence_service()
            
            # Assign to cluster
            hazard_id = await geo_service.assign_to_cluster(report)
            
            # Update hazard confidence
            confidence_service.update_hazard_confidence(hazard_id)
            
            logger.info(f"✅ Report processed and assigned to hazard {hazard_id}")
        except Exception as e:
            logger.error(f"Failed to process accepted report: {e}")
    
    def _update_user_trust(self, user_id: str, verdict: str):
        """Update user trust score based on verdict."""
        try:
            from app.services.trust import get_trust_service
            
            trust_service = get_trust_service()
            trust_service.update_user_trust(user_id, verdict)
            
            logger.debug(f"Updated trust for user {user_id}: {verdict}")
        except Exception as e:
            logger.error(f"Failed to update user trust: {e}")


# Singleton instance
_verification_service = None

def get_verification_service() -> VerificationService:
    """Get or create verification service instance."""
    global _verification_service
    if _verification_service is None:
        _verification_service = VerificationService()
    return _verification_service
