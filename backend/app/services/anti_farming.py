"""
Anti-farming and abuse detection service.
Detects suspicious user behavior and applies trust penalties or shadow bans.
"""

from typing import Dict, Tuple, Optional
from datetime import datetime, timedelta, timezone
from loguru import logger
from app.database import get_supabase
from app.config import get_settings
from app.services.trust import get_trust_service

settings = get_settings()


class AntiFarmingService:
    """Detects and mitigates reward farming and abuse."""
    
    def __init__(self):
        self.supabase = get_supabase()
        self.trust_service = get_trust_service()
        
    def check_farming_behavior(self, user_id: str, latitude: float, longitude: float, hazard_type: str) -> Tuple[bool, str]:
        """
        Check if the user is exhibiting farming behavior for a new submission.
        
        Rules:
        1. > 3 reports within 50m in under 10 minutes.
        2. > 5 rejected reports within 24 hours.
        
        Args:
            user_id: User identifier
            latitude: Report latitude
            longitude: Report longitude
            hazard_type: Type of hazard
            
        Returns:
            Tuple of (is_farming_detected, reason)
        """
        try:
            # 1. Check velocity (rapid reports in small area)
            ten_mins_ago = (datetime.now(timezone.utc) - timedelta(minutes=10)).isoformat()
            
            # Get recent reports by this user
            recent_reports = self.supabase.table("community_reports").select(
                "latitude, longitude"
            ).eq("user_id", user_id).gte("submitted_at", ten_mins_ago).execute()
            
            if recent_reports.data and len(recent_reports.data) >= 3:
                # Calculate how many are within 50m
                nearby_count = 0
                for r in recent_reports.data:
                    dist = self._haversine(latitude, longitude, float(r["latitude"]), float(r["longitude"]))
                    if dist <= settings.cluster_radius_meters:
                        nearby_count += 1
                        
                if nearby_count >= 3:
                    reason = "Too many reports in a small area within 10 minutes."
                    self.apply_penalty(user_id, "FARMING_VELOCITY", penalty_score=15.0)
                    return True, reason
                    
            # 2. Check recent rejections
            one_day_ago = (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()
            rejections = self.supabase.table("community_reports").select(
                "report_id"
            ).eq("user_id", user_id).eq("verification_status", "REJECTED").gte("submitted_at", one_day_ago).execute()
            
            if rejections.data and len(rejections.data) >= 5:
                reason = "Too many rejected reports in the last 24 hours."
                self.apply_penalty(user_id, "HIGH_REJECTION_RATE", penalty_score=20.0)
                return True, reason
                
            return False, ""
            
        except Exception as e:
            logger.error(f"Anti-farming check failed: {e}")
            return False, ""
            
    def check_same_user_cluster_duplicate(self, user_id: str, hazard_id: str) -> bool:
        """
        Check if the user has already reported to this specific cluster.
        Returns True if they have.
        """
        try:
            existing = self.supabase.table("community_reports").select("report_id").eq(
                "user_id", user_id
            ).eq("hazard_id", hazard_id).execute()
            
            return len(existing.data) > 0 if existing.data else False
            
        except Exception as e:
            logger.error(f"Failed to check same user cluster duplicate: {e}")
            return False

    def apply_penalty(self, user_id: str, penalty_type: str, penalty_score: float = 5.0):
        """
        Apply a trust score penalty to a user.
        
        Args:
            user_id: User identifier
            penalty_type: Reason code for penalty
            penalty_score: Amount to deduct from trust
        """
        try:
            # We bypass the standard update_user_trust outcome logic to directly deduct
            response = self.supabase.table("user_trust_scores").select("*").eq("user_id", user_id).execute()
            if not response.data:
                return
                
            user_trust = response.data[0]
            current_score = float(user_trust.get("trust_score", 50.0))
            new_score = max(0.0, current_score - penalty_score)
            
            logger.warning(f"Applying penalty {penalty_type} to user {user_id}. Trust: {current_score} -> {new_score}")
            
            self.supabase.table("user_trust_scores").update({
                "trust_score": new_score
            }).eq("user_id", user_id).execute()
            
            # Check for shadow ban threshold (e.g., < 20 trust score)
            if new_score < 20.0:
                logger.critical(f"User {user_id} trust fell below 20. Shadow ban warranted.")
                # We could set a specific flag here if added to schema
                
        except Exception as e:
            logger.error(f"Failed to apply penalty: {e}")
            
    def _haversine(self, lat1: float, lon1: float, lat2: float, lon2: float) -> float:
        """Calculate distance between two points in meters."""
        from math import radians, cos, sin, asin, sqrt
        lon1, lat1, lon2, lat2 = map(radians, [lon1, lat1, lon2, lat2])
        dlon = lon2 - lon1
        dlat = lat2 - lat1
        a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
        c = 2 * asin(sqrt(a))
        r = 6371000
        return c * r

# Singleton instance
_anti_farming_service = None

def get_anti_farming_service() -> AntiFarmingService:
    global _anti_farming_service
    if _anti_farming_service is None:
        _anti_farming_service = AntiFarmingService()
    return _anti_farming_service
