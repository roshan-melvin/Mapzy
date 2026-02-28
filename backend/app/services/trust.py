"""
User trust scoring and reward system.
Manages reputation, badges, and rewards based on contribution quality.
"""

from typing import Dict, Optional
from loguru import logger
from app.database import get_supabase
from app.services.firebase_sync import get_firebase_sync_service


class TrustScoringService:
    """Manages user trust scores and rewards."""
    
    BADGE_THRESHOLDS = {
        "BRONZE": 0,
        "SILVER": 10,
        "GOLD": 20,
        "PLATINUM": 50
    }
    
    REWARD_POINTS = {
        "ACCEPTED": 10,
        "REJECTED": -5,
        "REVALIDATION_YES": 5,
        "REVALIDATION_NO": 2
    }
    
    def __init__(self):
        """Initialize trust scoring service."""
        self.supabase = get_supabase()
        self.firebase_sync = get_firebase_sync_service()
    
    def update_user_trust(self, user_id: str, report_verdict: str) -> Dict:
        """
        Update user trust score based on report outcome.
        
        Args:
            user_id: User ID
            report_verdict: 'ACCEPTED', 'REJECTED', or 'UNCERTAIN'
            
        Returns:
            Updated user trust info
        """
        try:
            # Get or create user trust record
            user_trust = self._get_or_create_user_trust(user_id)
            
            # Update counts
            user_trust["total_reports"] += 1
            
            if report_verdict == "ACCEPTED":
                user_trust["accepted_reports"] += 1
                points = self.REWARD_POINTS["ACCEPTED"]
            elif report_verdict == "REJECTED":
                user_trust["rejected_reports"] += 1
                points = self.REWARD_POINTS["REJECTED"]
            else:  # UNCERTAIN
                points = 0
            
            # Update reward points
            user_trust["reward_points"] = max(0, user_trust["reward_points"] + points)
            
            # Calculate trust score
            if user_trust["total_reports"] > 0:
                accuracy = user_trust["accepted_reports"] / user_trust["total_reports"]
                user_trust["trust_score"] = round(accuracy * 100, 2)
            else:
                user_trust["trust_score"] = 50.0
            
            # Update badge level
            user_trust["badge_level"] = self._calculate_badge_level(
                user_trust["accepted_reports"]
            )
            
            # Save to database
            self.supabase.table("user_trust_scores").update({
                "total_reports": user_trust["total_reports"],
                "accepted_reports": user_trust["accepted_reports"],
                "rejected_reports": user_trust["rejected_reports"],
                "trust_score": user_trust["trust_score"],
                "reward_points": user_trust["reward_points"],
                "badge_level": user_trust["badge_level"]
            }).eq("user_id", user_id).execute()
            
            logger.info(
                f"Updated trust for {user_id}: "
                f"{user_trust['trust_score']:.1f}% "
                f"({user_trust['accepted_reports']}/{user_trust['total_reports']}) "
                f"Badge: {user_trust['badge_level']}"
            )

            accuracy = 0.0
            if user_trust["total_reports"] > 0:
                accuracy = (user_trust["accepted_reports"] / user_trust["total_reports"]) * 100

            trust_payload = {
                **user_trust,
                "accuracy_percentage": round(accuracy, 2)
            }
            self.firebase_sync.update_user_trust(user_id, trust_payload)
            
            return user_trust
        
        except Exception as e:
            logger.error(f"Failed to update user trust: {e}")
            return {}
    
    def update_revalidation_trust(
        self,
        user_id: str,
        response: str
    ) -> Dict:
        """
        Update trust based on revalidation response.
        
        Args:
            user_id: User ID
            response: 'YES' or 'NO'
            
        Returns:
            Updated user trust info
        """
        try:
            user_trust = self._get_or_create_user_trust(user_id)
            
            if response == "YES":
                points = self.REWARD_POINTS["REVALIDATION_YES"]
            elif response == "NO":
                points = self.REWARD_POINTS["REVALIDATION_NO"]
            else:
                points = 0
            
            user_trust["reward_points"] = max(0, user_trust["reward_points"] + points)
            
            self.supabase.table("user_trust_scores").update({
                "reward_points": user_trust["reward_points"]
            }).eq("user_id", user_id).execute()
            
            logger.info(f"Revalidation reward for {user_id}: +{points} points")

            trust_payload = {
                **user_trust,
                "accuracy_percentage": None
            }
            self.firebase_sync.update_user_trust(user_id, trust_payload)
            
            return user_trust
        
        except Exception as e:
            logger.error(f"Failed to update revalidation trust: {e}")
            return {}
    
    def get_user_stats(self, user_id: str) -> Dict:
        """
        Get comprehensive user statistics.
        
        Args:
            user_id: User ID
            
        Returns:
            Dict with user stats
        """
        try:
            user_trust = self._get_or_create_user_trust(user_id)
            
            # Calculate additional stats
            accuracy = 0.0
            if user_trust["total_reports"] > 0:
                accuracy = (user_trust["accepted_reports"] / user_trust["total_reports"]) * 100
            
            # Get report history
            reports_response = self.supabase.table("community_reports").select(
                "report_id, hazard_type, verification_status, submitted_at"
            ).eq("user_id", user_id).order("submitted_at", desc=True).limit(10).execute()
            
            recent_reports = reports_response.data if reports_response.data else []
            
            # Get unique hazards contributed
            unique_hazards_response = self.supabase.table("community_reports").select(
                "hazard_id"
            ).eq("user_id", user_id).eq("verification_status", "ACCEPTED").execute()
            
            unique_hazards = len(set(
                r["hazard_id"] for r in unique_hazards_response.data 
                if r.get("hazard_id")
            )) if unique_hazards_response.data else 0
            
            is_shadow_banned = user_trust.get("trust_score", 50.0) < 20.0
            
            return {
                "user_id": user_id,
                "trust_score": user_trust["trust_score"],
                "badge_level": user_trust["badge_level"],
                "reward_points": user_trust["reward_points"],
                "total_reports": user_trust["total_reports"],
                "accepted_reports": user_trust["accepted_reports"],
                "rejected_reports": user_trust["rejected_reports"],
                "accuracy_percentage": round(accuracy, 2),
                "unique_hazards_contributed": unique_hazards,
                "recent_reports": recent_reports,
                "is_shadow_banned": is_shadow_banned
            }
        
        except Exception as e:
            logger.error(f"Failed to get user stats: {e}")
            return {}
    
    def get_leaderboard(self, limit: int = 10) -> list:
        """
        Get top users by trust score.
        
        Args:
            limit: Number of users to return
            
        Returns:
            List of top users
        """
        try:
            response = self.supabase.table("user_trust_scores").select(
                "user_id, trust_score, badge_level, reward_points, accepted_reports"
            ).order("trust_score", desc=True).order(
                "accepted_reports", desc=True
            ).limit(limit).execute()
            
            return response.data if response.data else []
        
        except Exception as e:
            logger.error(f"Failed to get leaderboard: {e}")
            return []
    
    def _get_or_create_user_trust(self, user_id: str) -> Dict:
        """Get existing user trust or create new record."""
        try:
            response = self.supabase.table("user_trust_scores").select("*").eq(
                "user_id", user_id
            ).execute()
            
            if response.data:
                return response.data[0]
            
            # Create new user trust record
            new_user = {
                "user_id": user_id,
                "total_reports": 0,
                "accepted_reports": 0,
                "rejected_reports": 0,
                "trust_score": 50.0,
                "reward_points": 0,
                "badge_level": "BRONZE"
            }
            
            self.supabase.table("user_trust_scores").insert(new_user).execute()
            
            logger.info(f"Created new user trust record for {user_id}")
            return new_user
        
        except Exception as e:
            logger.error(f"Failed to get/create user trust: {e}")
            return {
                "user_id": user_id,
                "total_reports": 0,
                "accepted_reports": 0,
                "rejected_reports": 0,
                "trust_score": 50.0,
                "reward_points": 0,
                "badge_level": "BRONZE"
            }
    
    def _calculate_badge_level(self, accepted_reports: int) -> str:
        """Calculate badge level based on accepted reports."""
        if accepted_reports >= self.BADGE_THRESHOLDS["PLATINUM"]:
            return "PLATINUM"
        elif accepted_reports >= self.BADGE_THRESHOLDS["GOLD"]:
            return "GOLD"
        elif accepted_reports >= self.BADGE_THRESHOLDS["SILVER"]:
            return "SILVER"
        else:
            return "BRONZE"


# Singleton instance
_trust_service = None

def get_trust_service() -> TrustScoringService:
    """Get or create trust scoring service instance."""
    global _trust_service
    if _trust_service is None:
        _trust_service = TrustScoringService()
    return _trust_service
