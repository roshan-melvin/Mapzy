"""
Confidence lifecycle management for hazard clusters.
Handles initial confirmation, time-based decay, and revalidation.
"""

from datetime import datetime, timedelta
from typing import Dict, Optional, List
from loguru import logger
from app.database import get_supabase
from app.config import get_settings

settings = get_settings()


class ConfidenceLifecycleService:
    """Manages dynamic confidence scoring and lifecycle for hazards."""
    
    def __init__(self):
        """Initialize confidence lifecycle service."""
        self.supabase = get_supabase()
    
    def update_hazard_confidence(self, hazard_id: str) -> Dict:
        """
        Recalculate hazard confidence based on reports and time decay.
        
        Args:
            hazard_id: UUID of hazard cluster
            
        Returns:
            Dict with updated confidence info
        """
        try:
            # Get hazard
            hazard = self._get_hazard(hazard_id)
            if not hazard:
                logger.error(f"Hazard not found: {hazard_id}")
                return {"success": False}
            
            # Get accepted reports for this hazard
            accepted_reports = self._get_accepted_reports(hazard_id)
            report_count = len(accepted_reports)
            
            logger.info(f"Updating confidence for {hazard_id}: {report_count} accepted reports")
            
            # Phase 1: Initial Confirmation
            if hazard["status"] == "PENDING" and report_count >= settings.initial_confirmation_count:
                logger.info(f"✅ Initial confirmation reached ({report_count} reports)")
                self._confirm_hazard(hazard_id, report_count)
                return {"success": True, "status": "CONFIRMED", "confidence": 100}
            
            # Not enough reports yet
            if report_count < settings.initial_confirmation_count:
                logger.info(f"Waiting for confirmation: {report_count}/{settings.initial_confirmation_count}")
                return {"success": True, "status": "PENDING", "confidence": 0}
            
            # Phase 2: Time-Based Decay
            if hazard["last_verified_at"]:
                last_verified = datetime.fromisoformat(
                    hazard["last_verified_at"].replace("Z", "+00:00")
                )
                days_since_verification = (datetime.now(last_verified.tzinfo) - last_verified).days
                
                if days_since_verification >= settings.revalidation_days:
                    logger.warning(f"⏰ Hazard needs revalidation ({days_since_verification} days old)")
                    self._mark_needs_revalidation(hazard_id)
                    return {"success": True, "status": "NEEDS_REVALIDATION", "confidence": 50}
            
            # Phase 3: Expiry Check
            current_confidence = hazard.get("confidence_score", 0)
            if current_confidence < settings.expiry_confidence_threshold:
                logger.warning(f"❌ Hazard expired (confidence: {current_confidence}%)")
                self._expire_hazard(hazard_id)
                return {"success": True, "status": "EXPIRED", "confidence": current_confidence}
            
            return {
                "success": True,
                "status": hazard["status"],
                "confidence": current_confidence
            }
        
        except Exception as e:
            logger.error(f"Failed to update hazard confidence: {e}")
            return {"success": False, "error": str(e)}
    
    def _confirm_hazard(self, hazard_id: str, report_count: int):
        """Mark hazard as confirmed with 100% confidence."""
        try:
            self.supabase.table("hazard_clusters").update({
                "status": "CONFIRMED",
                "confidence_score": 100,
                "verified_image_count": report_count,
                "last_verified_at": datetime.now().isoformat()
            }).eq("hazard_id", hazard_id).execute()
            
            logger.info(f"✅ Hazard {hazard_id} confirmed")
        except Exception as e:
            logger.error(f"Failed to confirm hazard: {e}")
    
    def _mark_needs_revalidation(self, hazard_id: str):
        """Mark hazard as needing revalidation with 50% confidence."""
        try:
            self.supabase.table("hazard_clusters").update({
                "status": "NEEDS_REVALIDATION",
                "confidence_score": 50
            }).eq("hazard_id", hazard_id).execute()
            
            logger.info(f"⏰ Hazard {hazard_id} marked for revalidation")
        except Exception as e:
            logger.error(f"Failed to mark for revalidation: {e}")
    
    def _expire_hazard(self, hazard_id: str):
        """Mark hazard as expired."""
        try:
            self.supabase.table("hazard_clusters").update({
                "status": "EXPIRED"
            }).eq("hazard_id", hazard_id).execute()
            
            logger.info(f"❌ Hazard {hazard_id} expired")
        except Exception as e:
            logger.error(f"Failed to expire hazard: {e}")
    
    async def process_revalidation_response(
        self,
        hazard_id: str,
        user_id: str,
        response: str,
        new_image_url: Optional[str] = None
    ) -> Dict:
        """
        Handle user revalidation response.
        
        Args:
            hazard_id: UUID of hazard
            user_id: User ID
            response: 'YES', 'NO', or 'SKIP'
            new_image_url: Optional new image URL
            
        Returns:
            Result dict
        """
        try:
            # Record revalidation request
            self.supabase.table("revalidation_requests").insert({
                "hazard_id": hazard_id,
                "user_id": user_id,
                "response": response,
                "new_image_url": new_image_url
            }).execute()
            
            hazard = self._get_hazard(hazard_id)
            if not hazard:
                return {"success": False, "reason": "Hazard not found"}
            
            current_confidence = hazard.get("confidence_score", 0)
            
            if response == "YES":
                if new_image_url:
                    # Create new report for verification
                    logger.info("📸 User provided new image for revalidation")
                    # TODO: Create report and trigger verification
                    # For now, just boost confidence
                    new_confidence = min(100, current_confidence + 30)
                else:
                    # Trust-based confirmation
                    user_trust = self._get_user_trust(user_id)
                    boost = user_trust * 0.5  # Max 50% boost
                    new_confidence = min(100, current_confidence + boost)
                
                self.supabase.table("hazard_clusters").update({
                    "confidence_score": new_confidence,
                    "last_verified_at": datetime.now().isoformat(),
                    "status": "CONFIRMED" if new_confidence >= 70 else "NEEDS_REVALIDATION"
                }).eq("hazard_id", hazard_id).execute()
                
                logger.info(f"✅ Revalidation YES: confidence {current_confidence}% → {new_confidence}%")
                return {"success": True, "new_confidence": new_confidence}
            
            elif response == "NO":
                # Decrease confidence
                new_confidence = max(0, current_confidence - 30)
                
                new_status = "EXPIRED" if new_confidence < settings.expiry_confidence_threshold else "NEEDS_REVALIDATION"
                
                self.supabase.table("hazard_clusters").update({
                    "confidence_score": new_confidence,
                    "status": new_status
                }).eq("hazard_id", hazard_id).execute()
                
                logger.info(f"❌ Revalidation NO: confidence {current_confidence}% → {new_confidence}%")
                return {"success": True, "new_confidence": new_confidence, "status": new_status}
            
            else:  # SKIP
                logger.info("⏭️ User skipped revalidation")
                return {"success": True, "skipped": True}
        
        except Exception as e:
            logger.error(f"Revalidation processing failed: {e}")
            return {"success": False, "error": str(e)}
    
    def get_hazards_needing_revalidation(
        self,
        latitude: float,
        longitude: float,
        radius_km: float = 1.0
    ) -> List[Dict]:
        """
        Get hazards near user that need revalidation.
        
        Args:
            latitude: User latitude
            longitude: User longitude
            radius_km: Search radius
            
        Returns:
            List of hazards needing revalidation
        """
        try:
            # Get all hazards needing revalidation
            response = self.supabase.table("hazard_clusters").select("*").eq(
                "status", "NEEDS_REVALIDATION"
            ).execute()
            
            if not response.data:
                return []
            
            # Filter by distance
            from math import radians, cos, sin, asin, sqrt
            
            def haversine(lon1, lat1, lon2, lat2):
                lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
                dlon = lon2 - lon1
                dlat = lat2 - lat1
                a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
                c = 2 * asin(sqrt(a))
                r = 6371  # km
                return c * r
            
            nearby_hazards = []
            for hazard in response.data:
                distance = haversine(
                    longitude, latitude,
                    float(hazard["longitude"]), float(hazard["latitude"])
                )
                
                if distance <= radius_km:
                    hazard["distance_km"] = distance
                    
                    # Calculate days since last verification
                    if hazard.get("last_verified_at"):
                        last_verified = datetime.fromisoformat(
                            hazard["last_verified_at"].replace("Z", "+00:00")
                        )
                        days_old = (datetime.now(last_verified.tzinfo) - last_verified).days
                        hazard["days_since_verification"] = days_old
                    
                    nearby_hazards.append(hazard)
            
            # Sort by distance
            nearby_hazards.sort(key=lambda x: x["distance_km"])
            
            return nearby_hazards
        
        except Exception as e:
            logger.error(f"Failed to get revalidation hazards: {e}")
            return []
    
    def _get_hazard(self, hazard_id: str) -> Optional[Dict]:
        """Get hazard by ID."""
        try:
            response = self.supabase.table("hazard_clusters").select("*").eq(
                "hazard_id", hazard_id
            ).execute()
            
            return response.data[0] if response.data else None
        except Exception as e:
            logger.error(f"Failed to get hazard: {e}")
            return None
    
    def _get_accepted_reports(self, hazard_id: str) -> List[Dict]:
        """Get all accepted reports for a hazard."""
        try:
            response = self.supabase.table("community_reports").select("*").eq(
                "hazard_id", hazard_id
            ).eq("verification_status", "ACCEPTED").execute()
            
            return response.data
        except Exception as e:
            logger.error(f"Failed to get accepted reports: {e}")
            return []
    
    def _get_user_trust(self, user_id: str) -> float:
        """Get user trust score (0-100)."""
        try:
            response = self.supabase.table("user_trust_scores").select("trust_score").eq(
                "user_id", user_id
            ).execute()
            
            return response.data[0]["trust_score"] if response.data else 50.0
        except Exception as e:
            logger.error(f"Failed to get user trust: {e}")
            return 50.0


# Singleton instance
_confidence_service = None

def get_confidence_service() -> ConfidenceLifecycleService:
    """Get or create confidence lifecycle service instance."""
    global _confidence_service
    if _confidence_service is None:
        _confidence_service = ConfidenceLifecycleService()
    return _confidence_service
