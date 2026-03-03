import os
import logging
import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from dotenv import load_dotenv

# Load environment variables FIRST so all modules see them
load_dotenv()

# ── Configure logging so INFO messages appear in the terminal ─────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s — %(message)s",
    datefmt="%H:%M:%S",
)
# Silence noisy third-party loggers
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
logging.getLogger("urllib3").setLevel(logging.WARNING)
logging.getLogger("multipart").setLevel(logging.WARNING)
# ─────────────────────────────────────────────────────────────────────────────

from app.api.ai_draft import router as ai_draft_router

app = FastAPI(
    title="DeepBlueS11 - Assistant Backend",
    description="Microservice for handling AI voice reporting and ML tasks",
    version="1.0.0"
)

# Allow CORS for app testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(ai_draft_router, prefix="/api/v1")

@app.get("/health")
async def health_check():
    return {"status": "ok", "service": "assistant_backend"}

if __name__ == "__main__":
    port = int(os.getenv("PORT", 8001))
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=True)
