"""
FastAPI main application for DeepBlueS11 backend.
Hazard verification and community intelligence system.
"""

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from loguru import logger
import sys

from app.config import get_settings
from app.api import reports, hazards, users, admin

# Configure logging
logger.remove()
logger.add(
    sys.stdout,
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan> - <level>{message}</level>",
    level="INFO"
)
logger.add(
    "logs/backend.log",
    rotation="500 MB",
    retention="10 days",
    level="DEBUG"
)

settings = get_settings()

# Create FastAPI app
app = FastAPI(
    title="DeepBlueS11 Hazard Verification API",
    description="AI-powered community hazard verification and geo-clustering system",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure properly in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def startup_event():
    """Initialize services on startup."""
    logger.info("🚀 Starting DeepBlueS11 Backend API")
    logger.info(f"Environment: {settings.environment}")
    logger.info("Loading AI models...")
    
    # Pre-load models to avoid cold start
    from app.ai.prefilter import get_prefilter
    from app.ai.fake_detector import get_fake_detector
    from app.ai.object_detector import get_object_detector
    from app.ai.gemini_client import get_gemini_client
    from app.ai.embeddings import get_embedding_generator
    
    get_prefilter()
    get_fake_detector()
    get_object_detector()
    get_gemini_client()
    get_embedding_generator()
    
    logger.info("✅ All services initialized")


@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown."""
    logger.info("👋 Shutting down DeepBlueS11 Backend API")


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "message": "DeepBlueS11 Hazard Verification API",
        "version": "1.0.0",
        "status": "operational",
        "docs": "/docs"
    }


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "environment": settings.environment
    }


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    """Global exception handler."""
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"}
    )


# Include routers
app.include_router(reports.router, prefix="/api/v1", tags=["Reports"])
app.include_router(hazards.router, prefix="/api/v1", tags=["Hazards"])
app.include_router(users.router, prefix="/api/v1", tags=["Users"])
app.include_router(admin.router, prefix="/api/v1/admin", tags=["Admin"])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True if settings.environment == "development" else False
    )
