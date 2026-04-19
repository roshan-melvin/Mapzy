from supabase import create_client, Client
from app.config import get_settings
from loguru import logger

settings = get_settings()

# Initialize Supabase client
try:
    supabase: Client = create_client(
        settings.supabase_url,
        settings.supabase_service_role_key
    )
    logger.info("✅ Supabase client initialized")
except Exception as e:
    logger.error(f"❌ Failed to initialize Supabase client: {e}")
    logger.warning("Database features will be unavailable until valid keys are provided.")
    supabase = None


def get_supabase() -> Client:
    """Get Supabase client instance."""
    if supabase is None:
        logger.warning("Attempted to access uninitialized Supabase client")
    return supabase
