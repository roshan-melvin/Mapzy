"""
Geo-clustering service for grouping nearby hazard reports.
Uses PostGIS for spatial queries and clustering logic.
"""

from typing import Dict, Optional, Any, List
from loguru import logger
from app.database import get_supabase
from app.config import get_settings
from app.ai.embeddings import get_embedding_generator

settings = get_settings()


class GeoClusteringService:
    """Manages geo-spatial clustering of hazard reports."""
    
    def __init__(self):
        """Initialize geo-clustering service."""
        self.supabase = get_supabase()
        self.embedding_generator = get_embedding_generator()
    
    def find_nearby_hazard(
        self,
        latitude: float,
        longitude: float,
        hazard_type: str,
        radius_meters: Optional[int] = None
    ) -> Optional[Dict]:
        """
        Find existing hazard cluster within radius using PostGIS.
        
        Args:
            latitude: Latitude of new report
            longitude: Longitude of new report
            hazard_type: Type of hazard
            radius_meters: Search radius (defaults to config)
            
        Returns:
            Dict with hazard info or None if not found
        """
        radius = radius_meters or settings.cluster_radius_meters
        
        try:
            # PostGIS query to find nearby hazards
            # Note: Supabase Python client doesn't support raw SQL well,
            # so we'll use RPC function (needs to be created in Supabase)
            
            # Alternative: Use .rpc() with a custom PostgreSQL function
            # For now, we'll fetch all hazards and filter in Python (not optimal for production)
            
            response = self.supabase.table("hazard_clusters").select("*").eq(
                "hazard_type", hazard_type
            ).in_("status", ["CONFIRMED", "NEEDS_REVALIDATION", "PENDING"]).execute()
            
            if not response.data:
                return None
            
            # Calculate distances and find closest
            from math import radians, cos, sin, asin, sqrt
            
            def haversine(lon1, lat1, lon2, lat2):
                """Calculate distance between two points in meters."""
                lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
                dlon = lon2 - lon1
                dlat = lat2 - lat1
                a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
                c = 2 * asin(sqrt(a))
                r = 6371000  # Radius of earth in meters
                return c * r
            
            closest_hazard = None
            min_distance = float('inf')
            
            for hazard in response.data:
                distance = haversine(
                    longitude, latitude,
                    float(hazard["longitude"]), float(hazard["latitude"])
                )
                
                if distance <= radius and distance < min_distance:
                    min_distance = distance
                    closest_hazard = hazard
                    closest_hazard["distance"] = distance
            
            if closest_hazard:
                logger.info(f"Found nearby hazard: {closest_hazard['hazard_id']} ({min_distance:.1f}m away)")
            
            return closest_hazard
        
        except Exception as e:
            logger.error(f"Failed to find nearby hazard: {e}")
            return None
    
    async def check_embedding_similarity(
        self,
        report_id: str,
        hazard_id: str,
        threshold: Optional[float] = None,
        return_score: bool = False
    ) -> Any:
        """
        Check if report image is similar to existing hazard images.
        
        Args:
            report_id: ID of new report
            hazard_id: ID of existing hazard cluster
            threshold: Similarity threshold (defaults to config)
            return_score: If True, returns dict with max_similarity instead of bool
            
        Returns:
            True if similar enough, False otherwise, or dict if return_score=True.
        """
        threshold = threshold or settings.similarity_threshold
        
        try:
            # Get new report's embedding
            new_embedding_response = self.supabase.table("hazard_embeddings").select(
                "image_embedding"
            ).eq("report_id", report_id).execute()
            
            if not new_embedding_response.data:
                logger.warning(f"No embedding found for report {report_id}")
                return {"max_similarity": 0.0, "is_similar": True} if return_score else True
            
            new_embedding = new_embedding_response.data[0]["image_embedding"]
            
            # Get existing embeddings for this hazard
            existing_embeddings_response = self.supabase.table("hazard_embeddings").select(
                "report_id, image_embedding"
            ).eq("hazard_id", hazard_id).execute()
            
            if not existing_embeddings_response.data:
                return {"max_similarity": 0.0, "is_similar": True} if return_score else True
            
            # Compute similarities
            import numpy as np
            import json
            
            if isinstance(new_embedding, str):
                new_embedding = json.loads(new_embedding)
            new_emb = np.array(new_embedding)
            
            max_similarity = 0.0
            for emb_data in existing_embeddings_response.data:
                if emb_data.get("report_id") == report_id:
                    continue
                    
                existing_emb_data = emb_data["image_embedding"]
                if isinstance(existing_emb_data, str):
                    existing_emb_data = json.loads(existing_emb_data)
                existing_emb = np.array(existing_emb_data)
                similarity = self.embedding_generator.compute_similarity(new_emb, existing_emb)
                max_similarity = max(max_similarity, similarity)
            
            is_similar = max_similarity > threshold
            
            if return_score:
                return {"max_similarity": float(max_similarity), "is_similar": is_similar}
            
            # Legacy behavior
            if max_similarity > settings.duplicate_threshold:
                logger.warning(f"Duplicate image detected: similarity {max_similarity:.2%}")
                return False  # Reject duplicate
            
            logger.info(f"Similarity check: {max_similarity:.2%} (threshold: {threshold:.2%}) -> {is_similar}")
            return is_similar
        
        except Exception as e:
            logger.error(f"Similarity check failed: {e}")
            return {"max_similarity": 0.0, "is_similar": True} if return_score else True
    
    async def assign_to_cluster(self, report: Dict) -> str:
        """
        Assign report to existing cluster or create new one based on 4-step logic.
        
        Args:
            report: Report dict with location and type info
            
        Returns:
            hazard_id (UUID) or None if rejected
        """
        try:
            hazard_type = report.get("hazard_type") or report.get("incident_type", "unknown")
            user_id = report.get("user_id")
            report_id = report.get("report_id")
            latitude = float(report["latitude"])
            longitude = float(report["longitude"])
            
            # Anti-farming service
            from app.services.anti_farming import get_anti_farming_service
            anti_farming = get_anti_farming_service()
            
            # Step 1: Find nearby hazard (50m)
            nearby_hazard = self.find_nearby_hazard(
                latitude=latitude,
                longitude=longitude,
                hazard_type=hazard_type,
                radius_meters=settings.cluster_radius_meters
            )
            
            if not nearby_hazard:
                # No cluster -> create new cluster
                logger.info("Step 1: No existing cluster found. Creating new hazard cluster.")
                hazard_id = self._create_hazard_cluster(
                    hazard_type=hazard_type,
                    latitude=latitude,
                    longitude=longitude
                )
                self._assign_db(report_id, hazard_id)
                return hazard_id
                
            hazard_id = nearby_hazard["hazard_id"]
            distance = nearby_hazard.get("distance", 0)
            logger.info(f"Step 1: Found existing cluster {hazard_id} at {distance:.1f}m")
            
            # Step 2: Same User Check
            if anti_farming.check_same_user_cluster_duplicate(user_id, hazard_id):
                logger.warning(f"Step 2: User {user_id} already reported to cluster {hazard_id}. Rejecting.")
                anti_farming.apply_penalty(user_id, "SAME_USER_DUPLICATE", penalty_score=10.0)
                from app.services.verification import get_verification_service
                vs = get_verification_service()
                vs._reject_report(report_id, "You have already submitted a report for this hazard.", report=report)
                return None
                
            # Step 3: Image Similarity Check
            similarity_result = await self.check_embedding_similarity(report_id, hazard_id, return_score=True)
            max_similarity = similarity_result.get("max_similarity", 0.0)
            
            if max_similarity > settings.duplicate_threshold: # > 0.95
                logger.warning(f"Step 3: Exact duplicate image detected ({max_similarity:.2%}). Rejecting.")
                anti_farming.apply_penalty(user_id, "EXACT_DUPLICATE_IMAGE", penalty_score=20.0)
                from app.services.verification import get_verification_service
                vs = get_verification_service()
                vs._reject_report(report_id, f"Duplicate image detected ({max_similarity:.2%}).", report=report)
                return None
                
            elif max_similarity > settings.similarity_threshold: # 0.85 - 0.95
                logger.info(f"Step 3: High similarity ({max_similarity:.2%}). Merging into existing cluster {hazard_id}.")
                # Could apply slight penalty if suspicious, but we'll merge
                self._assign_db(report_id, hazard_id)
                return hazard_id
                
            # Step 4: Fine Distance Merge (<= 15m)
            logger.info(f"Step 4: Image not duplicate ({max_similarity:.2%}). Checking fine distance.")
            if distance <= settings.fine_merge_radius_meters:
                logger.info(f"Step 4: Within {settings.fine_merge_radius_meters}m fine merge radius. Merging.")
                self._assign_db(report_id, hazard_id)
                return hazard_id
            else:
                logger.info("Step 4: Outside fine merge radius. Creating new cluster.")
                new_hazard_id = self._create_hazard_cluster(
                    hazard_type=hazard_type,
                    latitude=latitude,
                    longitude=longitude
                )
                self._assign_db(report_id, new_hazard_id)
                return new_hazard_id
                
        except Exception as e:
            logger.error(f"Cluster assignment failed: {e}")
            raise
            
    def _assign_db(self, report_id: str, hazard_id: str):
        """Helper to quickly update DB assignments."""
        self.supabase.table("community_reports").update({
            "hazard_id": hazard_id
        }).eq("report_id", report_id).execute()
        
        self.supabase.table("hazard_embeddings").update({
            "hazard_id": hazard_id
        }).eq("report_id", report_id).execute()
    
    def _create_hazard_cluster(
        self,
        hazard_type: str,
        latitude: float,
        longitude: float
    ) -> str:
        """
        Create new hazard cluster.
        
        Args:
            hazard_type: Type of hazard
            latitude: Latitude
            longitude: Longitude
            
        Returns:
            hazard_id (UUID)
        """
        try:
            # Create geography point (PostGIS format)
            # Note: PostGIS uses (longitude, latitude) order!
            location = f"POINT({longitude} {latitude})"
            
            response = self.supabase.table("hazard_clusters").insert({
                "hazard_type": hazard_type,
                "latitude": latitude,
                "longitude": longitude,
                "location": location,
                "status": "PENDING",
                "verified_image_count": 0,
                "confidence_score": 0
            }).execute()
            
            hazard_id = response.data[0]["hazard_id"]
            logger.info(f"Created new hazard cluster: {hazard_id}")
            
            return hazard_id
        
        except Exception as e:
            logger.error(f"Failed to create hazard cluster: {e}")
            raise
    
    def get_hazards_in_radius(
        self,
        latitude: float,
        longitude: float,
        radius_km: float = 5.0
    ) -> List[Dict]:
        """
        Get all active hazards within radius.
        
        Args:
            latitude: Center latitude
            longitude: Center longitude
            radius_km: Radius in kilometers
            
        Returns:
            List of hazard dicts
        """
        try:
            # For production, use PostGIS ST_DWithin
            # For now, fetch and filter
            response = self.supabase.table("hazard_clusters").select("*").in_(
                "status", ["CONFIRMED", "NEEDS_REVALIDATION"]
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
                r = 6371  # Radius of earth in km
                return c * r
            
            hazards_in_radius = []
            for hazard in response.data:
                distance = haversine(
                    longitude, latitude,
                    float(hazard["longitude"]), float(hazard["latitude"])
                )
                
                if distance <= radius_km:
                    hazard["distance_km"] = distance
                    
                    # Fetch an image from associated community reports
                    reports_resp = self.supabase.table("community_reports").select("image_url").eq("hazard_id", hazard["hazard_id"]).limit(1).execute()
                    if reports_resp.data:
                        hazard["image_url"] = reports_resp.data[0].get("image_url")
                    else:
                        hazard["image_url"] = None
                        
                    hazards_in_radius.append(hazard)
            
            # Sort by confidence (descending)
            hazards_in_radius.sort(
                key=lambda x: x.get("confidence_score", 0), 
                reverse=True
            )
            
            return hazards_in_radius
        
        except Exception as e:
            logger.error(f"Failed to get hazards in radius: {e}")
            return []


# Singleton instance
_geo_clustering_service = None

def get_geo_clustering_service() -> GeoClusteringService:
    """Get or create geo-clustering service instance."""
    global _geo_clustering_service
    if _geo_clustering_service is None:
        _geo_clustering_service = GeoClusteringService()
    return _geo_clustering_service
