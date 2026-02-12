"""
Cloudinary integration for image upload and management.
Replaces Firebase Storage with Cloudinary.
"""

import cloudinary
import cloudinary.uploader
from typing import Optional, BinaryIO
from loguru import logger
from app.config import get_settings

settings = get_settings()


class CloudinaryService:
    """Handle image uploads to Cloudinary."""
    
    def __init__(self):
        """Initialize Cloudinary configuration."""
        try:
            cloudinary.config(
                cloud_name=settings.cloudinary_cloud_name,
                api_key=settings.cloudinary_api_key,
                api_secret=settings.cloudinary_api_secret,
                secure=True
            )
            logger.info("✅ Cloudinary configured")
        except Exception as e:
            logger.error(f"Failed to configure Cloudinary: {e}")
    
    def upload_image(
        self,
        file: BinaryIO,
        filename: str,
        folder: str = "hazard_reports"
    ) -> Optional[str]:
        """
        Upload image to Cloudinary.
        
        Args:
            file: File-like object (image data)
            filename: Original filename
            folder: Cloudinary folder name
            
        Returns:
            Public URL of uploaded image or None if failed
        """
        try:
            # Upload to Cloudinary
            result = cloudinary.uploader.upload(
                file,
                folder=folder,
                public_id=filename,
                resource_type="image",
                overwrite=False,
                format="jpg",  # Convert to JPG for consistency
                transformation=[
                    {"quality": "auto:good"},  # Auto quality optimization
                    {"fetch_format": "auto"}   # Auto format selection
                ]
            )
            
            url = result.get("secure_url")
            logger.info(f"📤 Uploaded to Cloudinary: {url}")
            
            return url
        
        except Exception as e:
            logger.error(f"Cloudinary upload failed: {e}")
            return None
    
    def delete_image(self, public_id: str) -> bool:
        """
        Delete image from Cloudinary.
        
        Args:
            public_id: Cloudinary public ID
            
        Returns:
            True if successful, False otherwise
        """
        try:
            result = cloudinary.uploader.destroy(public_id)
            success = result.get("result") == "ok"
            
            if success:
                logger.info(f"🗑️ Deleted from Cloudinary: {public_id}")
            
            return success
        
        except Exception as e:
            logger.error(f"Cloudinary delete failed: {e}")
            return False


# Singleton instance
_cloudinary_service = None

def get_cloudinary_service() -> CloudinaryService:
    """Get or create Cloudinary service instance."""
    global _cloudinary_service
    if _cloudinary_service is None:
        _cloudinary_service = CloudinaryService()
    return _cloudinary_service
