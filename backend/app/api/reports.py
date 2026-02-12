"""
Reports API endpoints.
Handle hazard report submission and verification.
"""

from fastapi import APIRouter, UploadFile, File, Form, HTTPException, BackgroundTasks
from typing import Optional
from loguru import logger
import uuid
from io import BytesIO

from app.config import get_settings
from app.services.verification import get_verification_service
from app.services.geo_clustering import get_geo_clustering_service
from app.services.confidence import get_confidence_service
from app.services.cloudinary_service import get_cloudinary_service
from app.database import get_supabase

settings = get_settings()
router = APIRouter()


async def upload_to_cloudinary(file: UploadFile) -> str:
    """Upload image to Cloudinary."""
    try:
        cloudinary_service = get_cloudinary_service()
        
        # Generate unique filename
        unique_filename = f"{uuid.uuid4()}_{file.filename}"
        
        # Read file content
        file_content = await file.read()
        file_obj = BytesIO(file_content)
        
        # Upload to Cloudinary
        url = cloudinary_service.upload_image(
            file=file_obj,
            filename=unique_filename,
            folder="hazard_reports"
        )
        
        if not url:
            raise HTTPException(status_code=500, detail="Image upload failed")
        
        logger.info(f"📤 Uploaded to Cloudinary: {url}")
        return url
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Cloudinary upload failed: {e}")
        raise HTTPException(status_code=500, detail="Image upload failed")


async def verify_report_background(report_id: str):
    """Background task to verify report."""
    try:
        verification_service = get_verification_service()
        result = await verification_service.verify_report(report_id)
        
        if result.get("success") and result.get("verdict") == "ACCEPTED":
            # Assign to cluster and update confidence
            geo_service = get_geo_clustering_service()
            confidence_service = get_confidence_service()
            
            # Get report
            supabase = get_supabase()
            report_response = supabase.table("community_reports").select("*").eq(
                "report_id", report_id
            ).execute()
            
            if report_response.data:
                report = report_response.data[0]
                
                # Assign to cluster
                hazard_id = await geo_service.assign_to_cluster(report)
                
                # Update hazard confidence
                confidence_service.update_hazard_confidence(hazard_id)
        
        logger.info(f"✅ Background verification complete for {report_id}")
    
    except Exception as e:
        logger.error(f"Background verification failed: {e}")


@router.post("/reports")
async def submit_report(
    background_tasks: BackgroundTasks,
    user_id: str = Form(...),
    hazard_type: str = Form(...),  # This is incident_type in your schema
    description: str = Form(""),
    latitude: float = Form(...),
    longitude: float = Form(...),
    image: UploadFile = File(...),
    severity: int = Form(1),  # Added to match your schema
    device_fingerprint: str = Form("")
):
    """
    Submit a new hazard report.
    
    - **user_id**: User identifier
    - **hazard_type**: Type of hazard (incident_type in DB)
    - **description**: Optional description
    - **latitude**: GPS latitude
    - **longitude**: GPS longitude
    - **image**: Hazard image file
    - **severity**: Severity level (1-5)
    - **device_fingerprint**: Optional device identifier
    """
    try:
        logger.info(f"📝 New report from {user_id}: {hazard_type} at ({latitude}, {longitude})")
        
        # Upload image to Cloudinary
        image_url = await upload_to_cloudinary(image)
        
        # Create report in database using existing schema
        supabase = get_supabase()
        
        # Generate report ID
        report_id = str(uuid.uuid4())
        
        report_data = {
            "report_id": report_id,
            "user_id": user_id,
            "incident_type": hazard_type,  # Maps to your schema
            "description": description,
            "severity": severity,
            "latitude": float(latitude),
            "longitude": float(longitude),
            "image_url": image_url,
            "status": "Pending"
        }
        
        response = supabase.table("reports_analysis").insert(report_data).execute()
        
        if not response.data:
            raise HTTPException(status_code=500, detail="Failed to create report")
        
        # Trigger async verification
        background_tasks.add_task(verify_report_background, report_id)
        
        logger.info(f"✅ Report created: {report_id}")
        
        return {
            "success": True,
            "report_id": report_id,
            "status": "Pending",
            "message": "Report submitted successfully. Verification in progress."
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Report submission failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/reports/{report_id}")
async def get_report(report_id: str):
    """
    Get report details by ID.
    
    - **report_id**: UUID of the report
    """
    try:
        supabase = get_supabase()
        response = supabase.table("reports_analysis").select("*").eq(
            "report_id", report_id
        ).execute()
        
        if not response.data:
            raise HTTPException(status_code=404, detail="Report not found")
        
        return response.data[0]
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get report: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/reports/user/{user_id}")
async def get_user_reports(
    user_id: str,
    limit: int = 20,
    offset: int = 0
):
    """
    Get reports submitted by a user.
    
    - **user_id**: User identifier
    - **limit**: Number of reports to return (default: 20)
    - **offset**: Pagination offset (default: 0)
    """
    try:
        supabase = get_supabase()
        response = supabase.table("reports_analysis").select("*").eq(
            "user_id", user_id
        ).order("created_at", desc=True).range(offset, offset + limit - 1).execute()
        
        return {
            "reports": response.data,
            "count": len(response.data)
        }
    
    except Exception as e:
        logger.error(f"Failed to get user reports: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/reports/{report_id}/revalidate")
async def revalidate_report(
    report_id: str,
    background_tasks: BackgroundTasks
):
    """
    Manually trigger revalidation of a report.
    
    - **report_id**: UUID of the report
    """
    try:
        # Trigger verification
        background_tasks.add_task(verify_report_background, report_id)
        
        return {
            "success": True,
            "message": "Revalidation triggered"
        }
    
    except Exception as e:
        logger.error(f"Revalidation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
