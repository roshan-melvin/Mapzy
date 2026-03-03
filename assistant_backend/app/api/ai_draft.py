from fastapi import APIRouter, UploadFile, File, Form
from fastapi.responses import JSONResponse
import logging

from app.services.ai_processing import process_voice_report

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/reports/ai-draft")
async def create_ai_draft(
    image: UploadFile = File(...),
    transcript: str = Form(...),
    user_id: str = Form(default="anonymous"),
    latitude: float = Form(default=0.0),
    longitude: float = Form(default=0.0),
):
    """
    One-Shot Hybrid AI Voice Report endpoint.

    Receives image + transcript + location from the driver.
    Runs the Hybrid AI pipeline (Path A → B → C) and directly:
      - Uploads the image to Cloudinary (once)
      - Inserts the report into Supabase reports_analysis
      - For Path A/B: triggers main backend verification
      - For Path C: pushes a Firebase rejection notification
    Returns the full report details to the app in a single response.
    """
    try:
        image_bytes = await image.read()

        result = await process_voice_report(
            image_bytes=image_bytes,
            transcript=transcript,
            user_id=user_id,
            latitude=latitude,
            longitude=longitude,
        )

        # Always return 200 — the Android app reads `success` and `path` fields
        return JSONResponse(content=result)

    except Exception as e:
        logger.error(f"Error in create_ai_draft: {e}")
        return JSONResponse(
            status_code=500,
            content={"success": False, "error": str(e)}
        )
