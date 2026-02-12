"""
Firebase sync helpers for Firestore profile and report updates.
"""

from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Dict, Optional

from loguru import logger

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
except Exception:  # pragma: no cover - optional dependency
    firebase_admin = None
    credentials = None
    firestore = None


class FirebaseSyncService:
    """Syncs verification and trust data to Firestore when available."""

    def __init__(self) -> None:
        self._db = None
        self._init_firebase()

    def _init_firebase(self) -> None:
        if firebase_admin is None:
            logger.warning("firebase_admin not installed; Firestore sync disabled")
            return

        if firebase_admin._apps:
            self._db = firestore.client()
            return

        cred_path = os.getenv("FIREBASE_CREDENTIAL_PATH")
        if not cred_path:
            base_dir = Path(__file__).resolve().parents[2]
            cred_path = str(base_dir / "serviceAccountKey.json")

        if not os.path.exists(cred_path):
            logger.warning("Firebase credential not found at %s; Firestore sync disabled", cred_path)
            return

        try:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
            self._db = firestore.client()
            logger.info("Firebase sync initialized")
        except Exception as exc:
            logger.error("Failed to initialize Firebase sync: %s", exc)
            self._db = None

    def _normalize_channel(self, incident_type: str) -> str:
        channel = (incident_type or "").strip().lower()
        if channel.startswith("#"):
            channel = channel[1:]
        channel = channel.replace("_", "-").replace(" ", "-")
        return channel

    def update_report_status(
        self,
        report_id: str,
        incident_type: str,
        status: str,
        confidence: float,
        reasoning: str,
        points_awarded: int,
    ) -> None:
        if self._db is None:
            return

        try:
            channel = self._normalize_channel(incident_type)
            doc_ref = (
                self._db.collection("reports")
                .document(channel)
                .collection("threads")
                .document(report_id)
            )
            doc_ref.set(
                {
                    "status": status,
                    "pointsAwarded": points_awarded,
                    "aiVerification": {
                        "verified": status == "Verified",
                        "confidence": round(confidence, 4),
                        "reason": reasoning,
                    },
                    "updated_at": firestore.SERVER_TIMESTAMP,
                },
                merge=True,
            )
        except Exception as exc:
            logger.error("Failed to sync report %s to Firestore: %s", report_id, exc)

    def update_user_trust(self, user_id: str, trust_data: Dict[str, Any]) -> None:
        if self._db is None:
            return

        try:
            summary = {
                "user_id": user_id,
                "trust_score": trust_data.get("trust_score", 50.0),
                "badge_level": trust_data.get("badge_level", "BRONZE"),
                "reward_points": trust_data.get("reward_points", 0),
                "total_reports": trust_data.get("total_reports", 0),
                "accepted_reports": trust_data.get("accepted_reports", 0),
                "rejected_reports": trust_data.get("rejected_reports", 0),
                "accuracy_percentage": trust_data.get("accuracy_percentage"),
                "updated_at": firestore.SERVER_TIMESTAMP,
            }

            user_doc = self._db.collection("users").document(user_id)
            user_doc.set(
                {
                    "points": summary["reward_points"],
                    "reports_count": summary["total_reports"],
                    "verified_reports": summary["accepted_reports"],
                    "trust_score": summary["trust_score"],
                    "badge_level": summary["badge_level"],
                    "updated_at": firestore.SERVER_TIMESTAMP,
                },
                merge=True,
            )

            user_doc.collection("trust").document("summary").set(summary, merge=True)
        except Exception as exc:
            logger.error("Failed to sync trust for %s to Firestore: %s", user_id, exc)


_firebase_sync_service: Optional[FirebaseSyncService] = None


def get_firebase_sync_service() -> FirebaseSyncService:
    """Get or create Firebase sync service instance."""
    global _firebase_sync_service
    if _firebase_sync_service is None:
        _firebase_sync_service = FirebaseSyncService()
    return _firebase_sync_service
