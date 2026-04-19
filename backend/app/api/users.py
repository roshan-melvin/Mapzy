"""
Users API endpoints.
Handle user statistics, trust scores, and leaderboard.
"""

from fastapi import APIRouter, HTTPException
from loguru import logger

from app.services.trust import get_trust_service

router = APIRouter()


@router.get("/users/{user_id}/stats")
async def get_user_stats(user_id: str):
    """
    Get comprehensive user statistics.
    
    - **user_id**: User identifier
    """
    try:
        trust_service = get_trust_service()
        stats = trust_service.get_user_stats(user_id)
        
        if not stats:
            raise HTTPException(status_code=404, detail="User not found")
        
        return stats
    
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get user stats: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/users/leaderboard")
async def get_leaderboard(limit: int = 10):
    """
    Get top users by trust score.
    
    - **limit**: Number of users to return (default: 10, max: 100)
    """
    try:
        if limit > 100:
            limit = 100
        
        trust_service = get_trust_service()
        leaderboard = trust_service.get_leaderboard(limit)
        
        return {
            "leaderboard": leaderboard,
            "count": len(leaderboard)
        }
    
    except Exception as e:
        logger.error(f"Failed to get leaderboard: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/users/badges")
async def get_badge_info():
    """Get information about badge levels and requirements."""
    return {
        "badges": [
            {
                "level": "BRONZE",
                "name": "Bronze Contributor",
                "required_reports": 0,
                "color": "#CD7F32"
            },
            {
                "level": "SILVER",
                "name": "Silver Guardian",
                "required_reports": 10,
                "color": "#C0C0C0"
            },
            {
                "level": "GOLD",
                "name": "Gold Protector",
                "required_reports": 20,
                "color": "#FFD700"
            },
            {
                "level": "PLATINUM",
                "name": "Platinum Hero",
                "required_reports": 50,
                "color": "#E5E4E2"
            }
        ]
    }
