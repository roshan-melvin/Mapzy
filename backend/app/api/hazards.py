"""
Hazards API endpoints.
Retrieve hazards, handle revalidation, and manage hazard lifecycle.
"""

from fastapi import APIRouter, HTTPException, Form, UploadFile, File
from typing import Optional
from loguru import logger

from app.services.geo_clustering import get_geo_clustering_service
from app.services.confidence import get_confidence_service
from app.database import get_supabase

router = APIRouter()


from cachetools import TTLCache
import time

# Cache for 60 seconds, max 1000 distinct location queries
# This singlehandedly fixes the "drag map spam" and server overload
hazards_cache = TTLCache(maxsize=1000, ttl=60)

@router.get("/hazards")
async def get_hazards(
    latitude: float,
    longitude: float,
    radius_km: float = 5.0,
    hazard_type: Optional[str] = None
):
    """
    Get hazards within radius of a location.
    
    - **latitude**: Center latitude
    - **longitude**: Center longitude
    - **radius_km**: Search radius in kilometers (default: 5.0)
    - **hazard_type**: Optional filter by hazard type
    """
    try:
        # Round to 3 decimal places (~111 meters precision)
        # If user drags map tiny amounts, it still hits the cached center!
        cache_key = f"{round(latitude, 3)}_{round(longitude, 3)}_{radius_km}_{hazard_type}"
        
        if cache_key in hazards_cache:
            logger.info("Serving hazards from memory cache ⚡")
            return hazards_cache[cache_key]
        
        geo_service = get_geo_clustering_service()
        hazards = geo_service.get_hazards_in_radius(latitude, longitude, radius_km)
        
        # Filter by type if specified
        if hazard_type:
            hazards = [h for h in hazards if h["hazard_type"] == hazard_type]
        
        result = {
            "hazards": hazards,
            "count": len(hazards),
            "center": {"latitude": latitude, "longitude": longitude},
            "radius_km": radius_km
        }
        
        # Save to cache
        hazards_cache[cache_key] = result
        return result
    
    except Exception as e:
        logger.error(f"Failed to get hazards: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/hazards/{hazard_id}")
async def get_hazard_details(hazard_id: str):
    """
    Get detailed information about a specific hazard.
    
    - **hazard_id**: UUID of the hazard cluster
    """
    try:
        supabase = get_supabase()
        
        # Get hazard
        hazard_response = supabase.table("hazard_clusters").select("*").eq(
            "hazard_id", hazard_id
        ).execute()
        
        if not hazard_response.data:
            raise HTTPException(status_code=404, detail="Hazard not found")
        
        hazard = hazard_response.data[0]
        
        # Get associated reports
        reports_response = supabase.table("community_reports").select(
            "report_id, user_id, description, image_url, verification_status, ai_confidence, submitted_at"
        ).eq("hazard_id", hazard_id).eq("verification_status", "ACCEPTED").execute()
        
        hazard["reports"] = reports_response.data if reports_response.data else []
        
        return hazard
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get hazard details: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/hazards/revalidation/nearby")
async def get_revalidation_hazards(
    latitude: float,
    longitude: float,
    radius_km: float = 1.0
):
    """
    Get hazards near user that need revalidation.
    
    - **latitude**: User latitude
    - **longitude**: User longitude
    - **radius_km**: Search radius in kilometers (default: 1.0)
    """
    try:
        confidence_service = get_confidence_service()
        hazards = confidence_service.get_hazards_needing_revalidation(
            latitude, longitude, radius_km
        )
        
        return {
            "hazards": hazards,
            "count": len(hazards),
            "message": "Hazards needing revalidation" if hazards else "No hazards need revalidation nearby"
        }
    
    except Exception as e:
        logger.error(f"Failed to get revalidation hazards: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.post("/hazards/{hazard_id}/revalidate")
async def revalidate_hazard(
    hazard_id: str,
    user_id: str = Form(...),
    response: str = Form(...),  # YES, NO, SKIP
    image: Optional[UploadFile] = File(None)
):
    """
    Submit revalidation response for a hazard.
    
    - **hazard_id**: UUID of the hazard
    - **user_id**: User identifier
    - **response**: 'YES', 'NO', or 'SKIP'
    - **image**: Optional new image if response is YES
    """
    try:
        if response not in ["YES", "NO", "SKIP"]:
            raise HTTPException(status_code=400, detail="Response must be YES, NO, or SKIP")
        
        image_url = None
        if image:
            # Upload image to Firebase
            from app.api.reports import upload_to_firebase
            image_url = await upload_to_firebase(image)
        
        # Process revalidation
        confidence_service = get_confidence_service()
        result = await confidence_service.process_revalidation_response(
            hazard_id, user_id, response, image_url
        )
        
        if not result.get("success"):
            raise HTTPException(status_code=500, detail=result.get("reason", "Revalidation failed"))
        
        # Update user trust
        from app.services.trust import get_trust_service
        trust_service = get_trust_service()
        
        if response in ["YES", "NO"]:
            trust_service.update_revalidation_trust(user_id, response)
        
        return {
            "success": True,
            "hazard_id": hazard_id,
            "new_confidence": result.get("new_confidence"),
            "status": result.get("status"),
            "message": "Revalidation recorded successfully"
        }
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Revalidation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/hazards/types")
async def get_hazard_types():
    """Get list of supported hazard types."""
    return {
        "hazard_types": [
            {"value": "speed_camera", "label": "Speed Camera"},
            {"value": "accident", "label": "Accident"},
            {"value": "construction", "label": "Construction"},
            {"value": "waterlogging", "label": "Waterlogging"},
            {"value": "fallen_tree", "label": "Fallen Tree"},
            {"value": "traffic_jam", "label": "Traffic Jam"},
            {"value": "road_damage", "label": "Road Damage"},
            {"value": "animal_crossing", "label": "Animal Crossing"}
        ]
    }


@router.get("/hazards/stats")
async def get_hazard_stats():
    """Get overall hazard statistics."""
    try:
        supabase = get_supabase()
        
        # Total hazards by status
        response = supabase.table("hazard_clusters").select("status").execute()
        
        stats = {
            "total": len(response.data),
            "confirmed": sum(1 for h in response.data if h["status"] == "CONFIRMED"),
            "needs_revalidation": sum(1 for h in response.data if h["status"] == "NEEDS_REVALIDATION"),
            "pending": sum(1 for h in response.data if h["status"] == "PENDING"),
            "expired": sum(1 for h in response.data if h["status"] == "EXPIRED")
        }
        
        # Hazards by type
        type_counts = {}
        for hazard in response.data:
            hazard_type = hazard.get("hazard_type", "unknown")
            type_counts[hazard_type] = type_counts.get(hazard_type, 0) + 1
        
        stats["by_type"] = type_counts
        
        return stats
    
    except Exception as e:
        logger.error(f"Failed to get hazard stats: {e}")
        raise HTTPException(status_code=500, detail=str(e))
