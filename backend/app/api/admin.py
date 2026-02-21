from fastapi import APIRouter, HTTPException, Form
from typing import Optional
from loguru import logger
import datetime

from app.database import get_supabase
from app.services.firebase_sync import get_firebase_sync_service

router = APIRouter()

@router.get("/reports/pending")
async def get_pending_reports():
    """Get all reports with status 'Pending' for admin review."""
    try:
        supabase = get_supabase()
        response = supabase.table("reports_analysis").select(
            "report_id, user_id, incident_type, description, severity, latitude, longitude, image_url, ai_reasoning, verification_confidence, created_at"
        ).eq("status", "Pending").order("created_at", desc=True).execute()
        
        return {
            "reports": response.data,
            "count": len(response.data) if response.data else 0
        }
    except Exception as e:
        logger.error(f"Failed to get pending reports: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/reports/{report_id}/resolve")
async def resolve_pending_report(
    report_id: str,
    action: str = Form(...), # 'approve' or 'reject'
    admin_id: Optional[str] = Form("admin")
):
    """Admin resolves a pending report."""
    try:
        if action not in ["approve", "reject"]:
            raise HTTPException(status_code=400, detail="Action must be 'approve' or 'reject'")
        
        supabase = get_supabase()
        
        # Get the report
        report_response = supabase.table("reports_analysis").select("*").eq("report_id", report_id).execute()
        if not report_response.data:
            raise HTTPException(status_code=404, detail="Report not found")
        
        report = report_response.data[0]
        if report["status"] != "Pending":
            raise HTTPException(status_code=400, detail=f"Report is already {report['status']}")
            
        new_status = "Verified" if action == "approve" else "Rejected"
        confidence = 100.0 if action == "approve" else 0.0
        reasoning = f"Manually {new_status.lower()} by admin."
        
        # Update database
        supabase.table("reports_analysis").update({
            "status": new_status,
            "verification_confidence": confidence,
            "ai_reasoning": reasoning
        }).eq("report_id", report_id).execute()
        
        # Sync to Firebase
        firebase_sync = get_firebase_sync_service()
        points_awarded = 10 if action == "approve" else 0
        firebase_sync.update_report_status(
            report_id=report_id,
            incident_type=report.get("incident_type", ""),
            status=new_status,
            confidence=confidence / 100.0,
            reasoning=reasoning,
            points_awarded=points_awarded,
            user_id=report.get("user_id", ""),
        )
        
        # Process accepted report
        if action == "approve":
            try:
                from app.services.geo_clustering import get_geo_clustering_service
                from app.services.confidence import get_confidence_service
                
                geo_service = get_geo_clustering_service()
                confidence_service = get_confidence_service()
                
                hazard_id = await geo_service.assign_to_cluster(report)
                confidence_service.update_hazard_confidence(hazard_id)
            except Exception as e:
                logger.error(f"Failed to process accepted report for clustering: {e}")
                
        # Update user trust
        try:
            from app.services.trust import get_trust_service
            trust_service = get_trust_service()
            verdict = "ACCEPTED" if action == "approve" else "REJECTED"
            trust_service.update_user_trust(report["user_id"], verdict)
        except Exception as e:
            logger.error(f"Failed to update user trust: {e}")
            
        return {
            "success": True,
            "report_id": report_id,
            "status": new_status,
            "message": f"Report successfully {new_status.lower()}"
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to resolve report: {e}")
        raise HTTPException(status_code=500, detail=str(e))
