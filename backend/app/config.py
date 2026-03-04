from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """Application configuration settings."""
    
    # Environment
    environment: str = "development"
    
    # Supabase
    supabase_url: str
    supabase_key: str
    supabase_service_role_key: str
    
    # Cloudinary (Image Storage)
    cloudinary_cloud_name: str
    cloudinary_api_key: str
    cloudinary_api_secret: str
    
    # Gemini API
    gemini_api_key: str
    
    # Redis
    redis_url: str = "redis://localhost:6379/0"
    
    # JWT
    secret_key: str
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 30
    
    # AI Models
    yolo_model_path: str = "./models/yolov11m_renamed.pt"
    fake_detector_model: str = "umm-maybe/AI-image-detector"
    clip_model: str = "ViT-B/32"
    
    # Thresholds
    ai_gen_threshold: float = 0.7
    yolo_confidence_threshold: float = 0.5
    gemini_confidence_threshold: float = 0.6
    similarity_threshold: float = 0.85
    duplicate_threshold: float = 0.95
    
    # Geo-Clustering
    cluster_radius_meters: int = 200
    fine_merge_radius_meters: int = 100
    highway_cluster_radius_meters: int = 500
    
    # Confidence Lifecycle
    initial_confirmation_count: int = 2
    revalidation_days: int = 7
    expiry_confidence_threshold: int = 30
    
    # Rate Limiting
    max_reports_per_user_per_day: int = 20
    max_revalidations_per_user_per_day: int = 50
    
    class Config:
        env_file = ".env"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """Get cached settings instance."""
    return Settings()
