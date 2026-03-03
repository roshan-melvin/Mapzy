"""
Hybrid One-Shot AI Voice Report Pipeline
=========================================
Path A – Visual-First : Gemini identifies hazard in pixels.
Path B – Voice Fallback: Pixels unclear, but transcript names a valid hazard.
Path C – Total Reject  : Neither pixels nor transcript identifies a hazard.
           → Uploads image, inserts Rejected record, pushes Firebase notification, stops.
"""

import os
import io
import cv2
import json
import uuid
import logging
import asyncio
import numpy as np
import httpx
import google.generativeai as genai
from PIL import Image
from ultralytics import YOLO
from supabase import create_client, Client
import cloudinary
import cloudinary.uploader

logger = logging.getLogger(__name__)

# ── Gemini ──────────────────────────────────────────────────────────────────
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
gemini_pro = None
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)
    gemini_pro = genai.GenerativeModel("models/gemini-2.5-flash")
    logger.info("✅ Gemini model initialized")
else:
    logger.error("GEMINI_API_KEY not found")

# ── Cloudinary ───────────────────────────────────────────────────────────────
cloudinary.config(
    cloud_name=os.getenv("CLOUDINARY_CLOUD_NAME"),
    api_key=os.getenv("CLOUDINARY_API_KEY"),
    api_secret=os.getenv("CLOUDINARY_API_SECRET"),
)

# ── YOLO (shared model from main backend) ────────────────────────────────────
MODEL_PATH = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../../../backend/models/yolov11m_renamed.pt")
)
try:
    yolo_model = YOLO(MODEL_PATH)
    logger.info("✅ YOLO model loaded")
except Exception as e:
    logger.error(f"Failed to load YOLO: {e}")
    yolo_model = None

# ── Supabase ─────────────────────────────────────────────────────────────────
_supabase: Client = None

def _get_supabase() -> Client:
    global _supabase
    if _supabase is None:
        url = os.getenv("SUPABASE_URL", "")
        key = os.getenv("SUPABASE_KEY", "")
        if url and key:
            _supabase = create_client(url, key)
            logger.info("✅ Supabase client initialized")
        else:
            logger.error("SUPABASE_URL/KEY missing")
    return _supabase

# ── Firebase (for Path C direct rejection notification) ──────────────────────
_firebase_db = None

def _get_firebase():
    global _firebase_db
    if _firebase_db is not None:
        return _firebase_db
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore as fb_store
        if not firebase_admin._apps:
            cred_path = os.getenv(
                "FIREBASE_CREDENTIAL_PATH",
                os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../backend/serviceAccountKey.json"))
            )
            if os.path.exists(cred_path):
                firebase_admin.initialize_app(credentials.Certificate(cred_path))
            else:
                logger.warning("Firebase credential not found — Path C notifications disabled")
                return None
        _firebase_db = fb_store.client()
        logger.info("✅ Firebase client initialized in assistant backend")
    except Exception as e:
        logger.error(f"Firebase init failed: {e}")
    return _firebase_db

# ── Official hazard list ──────────────────────────────────────────────────────
HAZARD_CATEGORIES = [
    "#accident", "#speed-camera", "#road-damage",
    "#pothole", "#traffic", "#police", "#hazard"
]
CATEGORIES_LIST = ", ".join(HAZARD_CATEGORIES)

# ── Main backend base URL (for triggering verification on Path A/B) ──────────
MAIN_BACKEND_URL = os.getenv("MAIN_BACKEND_URL", "http://localhost:8000")


# ─────────────────────────────────────────────────────────────────────────────
def _yolo_crop_and_upload(image_bytes: bytes) -> str:
    """Crop the hazard with YOLO then upload to Cloudinary. Returns secure_url."""
    try:
        if yolo_model:
            nparr = np.frombuffer(image_bytes, np.uint8)
            cv_img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
            results = yolo_model(cv_img, conf=0.15)
            if results and len(results[0].boxes) > 0:
                best_box = max(results[0].boxes, key=lambda b: b.conf[0])
                x1, y1, x2, y2 = map(int, best_box.xyxy[0])
                h, w = cv_img.shape[:2]
                pad_x = int((x2 - x1) * 0.2)
                pad_y = int((y2 - y1) * 0.2)
                x1, y1 = max(0, x1 - pad_x), max(0, y1 - pad_y)
                x2, y2 = min(w, x2 + pad_x), min(h, y2 + pad_y)
                cropped = cv_img[y1:y2, x1:x2]
                _, buf = cv2.imencode(".jpg", cropped)
                upload_bytes = buf.tobytes()
                logger.info("YOLO cropped hazard successfully")
            else:
                logger.warning("YOLO found no boxes — using original image")
                upload_bytes = image_bytes
        else:
            upload_bytes = image_bytes
    except Exception as e:
        logger.error(f"YOLO crop failed: {e}")
        upload_bytes = image_bytes

    res = cloudinary.uploader.upload(upload_bytes, folder="ai_voice_reports")
    url = res.get("secure_url", "")
    logger.info(f"☁️ Uploaded to Cloudinary: {url}")
    return url


def _upload_original(image_bytes: bytes) -> str:
    """Upload original (un-cropped) image to Cloudinary. Returns secure_url."""
    res = cloudinary.uploader.upload(image_bytes, folder="ai_voice_reports")
    url = res.get("secure_url", "")
    logger.info(f"☁️ Uploaded original to Cloudinary: {url}")
    return url


def _insert_report(
    report_id: str,
    user_id: str,
    incident_type: str,
    description: str,
    latitude: float,
    longitude: float,
    image_url: str,
    status: str,
) -> bool:
    """Insert report directly into Supabase reports_analysis. Retries 3× on transient errors."""
    import time
    sb = _get_supabase()
    if sb is None:
        logger.error("Supabase not available — cannot insert report")
        return False

    payload = {
        "report_id": report_id,
        "user_id": user_id,
        "incident_type": incident_type,
        "description": description,
        "severity": 1,
        "latitude": float(latitude),
        "longitude": float(longitude),
        "image_url": image_url,
        "status": status,
    }

    for attempt in range(1, 4):
        try:
            sb.table("reports_analysis").insert(payload).execute()
            logger.info(f"✅ Supabase insert OK: {report_id} [status={status}]")
            return True
        except Exception as e:
            err_str = str(e)
            if attempt < 3:
                logger.warning(f"⚠️  Supabase insert attempt {attempt}/3 failed — retrying in 1s: {err_str[:120]}")
                time.sleep(1)
            else:
                logger.error(f"❌ Supabase insert failed after 3 attempts: {err_str[:200]}")
    return False




def _create_firestore_thread(
    report_id: str,
    user_id: str,
    incident_type: str,
    description: str,
    image_url: str,
    latitude: float,
    longitude: float,
) -> None:
    """
    Path A/B: Create the initial Firestore thread document so the main
    backend's /revalidate endpoint can find and update it.
    Without this, update_report_status (merge=True) only sets partial fields.
    """
    try:
        db = _get_firebase()
        if db is None:
            logger.warning("Firebase not available — skipping Firestore thread creation")
            return
        from firebase_admin import firestore as fb_store

        channel = (incident_type or "hazard").lstrip("#").replace("_", "-")
        logger.info(f"📝 Creating Firestore thread: reports/{channel}/threads/{report_id}")

        db.collection("reports").document(channel) \
            .collection("threads").document(report_id) \
            .set({
                "id": report_id,
                "userId": user_id,
                "incidentType": incident_type,
                "description": description,
                "imageUrl": image_url,
                "latitude": float(latitude),
                "longitude": float(longitude),
                "status": "Pending",
                "pointsAwarded": 0,
                "hazardCondition": "active",
                "aiVerification": {
                    "verified": False,
                    "confidence": 0.0,
                    "reason": "Awaiting AI verification",
                },
                "createdAt": fb_store.SERVER_TIMESTAMP,
            })
        logger.info(f"✅ Firestore thread created at reports/{channel}/threads/{report_id}")
    except Exception as e:
        logger.error(f"Firestore thread creation failed: {e}")




def _push_firebase_rejection(
    report_id: str,
    user_id: str,
    incident_type: str,
    description: str,
    image_url: str,
    latitude: float,
    longitude: float,
) -> None:
    """Path C only: push a rejection notification directly to Firestore."""
    try:
        db = _get_firebase()
        if db is None:
            return
        from firebase_admin import firestore as fb_store

        # Normalize channel (strip "#")
        channel = (incident_type or "hazard").lstrip("#").replace("_", "-")
        type_label = channel.replace("-", " ").title()

        # Create full rejected report status in reports/{channel}/threads/{report_id}
        db.collection("reports").document(channel) \
            .collection("threads").document(report_id) \
            .set({
                "id": report_id,
                "userId": user_id,
                "incidentType": incident_type,
                "description": description,
                "imageUrl": image_url,
                "latitude": float(latitude),
                "longitude": float(longitude),
                "status": "Rejected",
                "pointsAwarded": 0,
                "hazardCondition": "none",
                "aiVerification": {
                    "verified": False,
                    "confidence": 0.0,
                    "reason": "No valid hazard identified in pixels or transcript.",
                },
                "createdAt": fb_store.SERVER_TIMESTAMP,
            })

        # Push user notification
        db.collection("users").document(user_id) \
            .collection("notifications").document() \
            .set({
                "userId": user_id,
                "title": "❌ Report Rejected",
                "message": f"Your {type_label} voice report was rejected — no valid hazard was identified in the image or the voice command.",
                "type": "verification",
                "relatedReportId": report_id,
                "isRead": False,
                "createdAt": fb_store.SERVER_TIMESTAMP,
            })
        logger.info(f"🔔 Path C Firebase rejection pushed → user {user_id}")
    except Exception as e:
        logger.error(f"Firebase Path C notification failed: {e}")


async def _trigger_main_backend_verification(report_id: str) -> None:
    """Path A/B: Async call to main backend to start verification pipeline."""
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            url = f"{MAIN_BACKEND_URL}/api/v1/reports/{report_id}/revalidate"
            resp = await client.post(url)
            logger.info(f"🔄 Triggered verification for {report_id} → {resp.status_code}")
    except Exception as e:
        logger.warning(f"Verification trigger failed (non-critical): {e}")


# ─────────────────────────────────────────────────────────────────────────────
async def process_voice_report(
    image_bytes: bytes,
    transcript: str,
    user_id: str = "anonymous",
    latitude: float = 0.0,
    longitude: float = 0.0,
) -> dict:
    """
    Master Hybrid One-Shot Pipeline.
    Returns a dict ready to serialize as the API response.
    """
    if not gemini_pro:
        return {"success": False, "error": "AI not configured"}

    report_id = str(uuid.uuid4())

    logger.info("━" * 60)
    logger.info(f"📥 NEW VOICE REPORT  user={user_id}  id={report_id}")
    logger.info(f"   📍 Location: ({latitude}, {longitude})")
    logger.info(f"   🎤 Transcript: \"{transcript}\"")
    logger.info("━" * 60)

    # Convert bytes → PIL image once for Gemini
    try:
        pil_img = Image.open(io.BytesIO(image_bytes))
        logger.info(f"🖼️  Image loaded: {pil_img.size[0]}×{pil_img.size[1]} px")
    except Exception:
        logger.error("❌ Invalid image format — aborting")
        return {"success": False, "error": "Invalid image format."}

    # ── PATH A: Visual-First Classification ──────────────────────────────────
    logger.info("─" * 60)
    logger.info("🔍 STEP 1 — Visual Analysis (Gemini scanning pixels)…")
    visual_hazard_type = None
    visual_description = None
    try:
        visual_prompt = f"""
        You are a road safety expert analyzing a dashcam or phone camera photo taken by a driver.

        Look at the image carefully. Your task:
        1. Is there a road hazard, traffic event, or road safety feature clearly visible?
        2. If YES, pick the SINGLE BEST category from: {CATEGORIES_LIST}
        3. Write a short factual description (max 15 words) of what you see.

        Return ONLY a raw JSON object with two keys:
        - "hazard_type": one of the official categories OR "none"
        - "description": factual description of what you see

        Example: {{"hazard_type": "#pothole", "description": "Large pothole in the left lane."}}
        """
        result = gemini_pro.generate_content([visual_prompt, pil_img])
        text = result.text.strip().lstrip("```json").lstrip("```").rstrip("```").strip()
        data = json.loads(text)
        ht = data.get("hazard_type", "none").strip().lower()
        if ht in HAZARD_CATEGORIES and ht != "none":
            visual_hazard_type = ht
            visual_description = data.get("description", "").strip()
            logger.info(f"   ✅ Path A — Gemini sees: {visual_hazard_type}")
            logger.info(f"   📝 Description: {visual_description}")
        else:
            logger.info(f"   ℹ️  Gemini returned '{ht}' — no visual hazard detected")
    except Exception as e:
        logger.warning(f"   ⚠️  Visual classification error: {e}")

    # ── PATH B: Voice Fallback ───────────────────────────────────────────────
    voice_hazard_type = None
    voice_description = None
    if visual_hazard_type is None:
        logger.info("─" * 60)
        logger.info("🎤 STEP 2 — Voice Fallback (Gemini reading transcript)…")
        try:
            voice_prompt = f"""
            A driver said: "{transcript}"

            From the list below, identify if the driver is naming a specific road hazard:
            {CATEGORIES_LIST}

            Rules:
            - If they clearly name a hazard (e.g. "speed camera", "pothole", "accident"), return its matching hashtag.
            - If the statement is unrelated to driving or names no specific road hazard, return "none".
            - Write a short cleaned-up description based on their words (max 15 words).

            Return ONLY a raw JSON object:
            {{"hazard_type": "#pothole" or "none", "description": "..."}}
            """
            result = gemini_pro.generate_content(voice_prompt)
            text = result.text.strip().lstrip("```json").lstrip("```").rstrip("```").strip()
            data = json.loads(text)
            ht = data.get("hazard_type", "none").strip().lower()
            if ht in HAZARD_CATEGORIES and ht != "none":
                voice_hazard_type = ht
                voice_description = data.get("description", transcript).strip()
                logger.info(f"   ✅ Path B — Voice hazard: {voice_hazard_type}")
                logger.info(f"   📝 Description: {voice_description}")
            else:
                logger.info(f"   ℹ️  Transcript yielded '{ht}' — no hazard named")
                logger.info("   🚫 → Falling through to Path C (Rejection)")
        except Exception as e:
            logger.warning(f"   ⚠️  Voice fallback error: {e}")

    # ── Determine final status/type/description ───────────────────────────────
    logger.info("─" * 60)
    if visual_hazard_type:
        path = "A"
        hazard_type = visual_hazard_type
        description = visual_description
        status = "Pending"
        logger.info("✂️  STEP 3 — YOLO crop + Cloudinary upload (Path A)…")
        image_url = _yolo_crop_and_upload(image_bytes)
    elif voice_hazard_type:
        path = "B"
        hazard_type = voice_hazard_type
        description = voice_description
        status = "Pending"
        logger.info("☁️  STEP 3 — Cloudinary upload original (Path B)…")
        image_url = _upload_original(image_bytes)
    else:
        path = "C"
        hazard_type = "#hazard"
        description = "Rejected: No valid hazard identified in pixels or transcript."
        status = "Rejected"
        logger.info("☁️  STEP 3 — Cloudinary upload original (Path C)…")
        image_url = _upload_original(image_bytes)

    logger.info(f"   🔗 Image URL: {image_url}")

    # ── Single Supabase INSERT ────────────────────────────────────────────────
    logger.info("─" * 60)
    logger.info(f"🗄️  STEP 4 — Supabase INSERT  [path={path}  status={status}]")
    _insert_report(
        report_id=report_id,
        user_id=user_id,
        incident_type=hazard_type,
        description=description,
        latitude=latitude,
        longitude=longitude,
        image_url=image_url,
        status=status,
    )

    # ── Selective Handover ────────────────────────────────────────────────────
    logger.info("─" * 60)
    if path in ("A", "B"):
        logger.info(f"🔥 STEP 5 — Firestore thread create  [channel={hazard_type.lstrip('#')}]")
        _create_firestore_thread(
            report_id=report_id,
            user_id=user_id,
            incident_type=hazard_type,
            description=description,
            image_url=image_url,
            latitude=latitude,
            longitude=longitude,
        )
        logger.info(f"🔄 STEP 6 — Triggering Main Backend verification…")
        asyncio.create_task(_trigger_main_backend_verification(report_id))
    else:
        logger.info("🔥 STEP 5 — Firestore rejection push (Path C — no main backend needed)")
        _push_firebase_rejection(
            report_id=report_id,
            user_id=user_id,
            incident_type=hazard_type,
            description=description,
            image_url=image_url,
            latitude=latitude,
            longitude=longitude,
        )

    logger.info("━" * 60)
    logger.info(f"✅ PIPELINE DONE  id={report_id}  path={path}  status={status}")
    logger.info("━" * 60)

    return {
        "success": True,
        "report_id": report_id,
        "hazard_type": hazard_type,
        "description": description,
        "image_url": image_url,
        "status": status,
        "path": path,
    }

