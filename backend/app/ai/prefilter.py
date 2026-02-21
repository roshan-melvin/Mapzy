"""
Pre-filter module for fast, deterministic image validation.
Checks image corruption, resolution, and duplicate detection.
"""

from PIL import Image
import imagehash
from pathlib import Path
from typing import Dict, List, Optional
from loguru import logger
import cv2
import numpy as np


class ImagePrefilter:
    """Fast image validation before expensive AI processing."""
    
    def __init__(self, min_width: int = 640, min_height: int = 480):
        """
        Initialize prefilter with minimum resolution requirements.
        
        Args:
            min_width: Minimum image width in pixels
            min_height: Minimum image height in pixels
        """
        self.min_width = min_width
        self.min_height = min_height
    
    def check_image_corruption(self, image_path: str) -> Dict[str, any]:
        """
        Verify image can be opened and is not corrupted.
        
        Args:
            image_path: Path to image file
            
        Returns:
            Dict with 'valid' (bool) and 'reason' (str)
        """
        try:
            with Image.open(image_path) as img:
                img.verify()  # Verify it's a valid image
            
            # Re-open for actual processing (verify() closes the file)
            with Image.open(image_path) as img:
                img.load()  # Force load to detect truncated images
            
            return {"valid": True, "reason": ""}
        
        except (IOError, SyntaxError, Image.DecompressionBombError) as e:
            logger.warning(f"Image corruption detected: {e}")
            return {"valid": False, "reason": f"Corrupted or invalid image: {str(e)}"}
    
    def check_resolution(self, image_path: str) -> Dict[str, any]:
        """
        Ensure image has sufficient resolution for analysis.
        Checks total pixel count (width*height) instead of strict w/h minimums
        so landscape and portrait mobile photos both pass.
        """
        try:
            with Image.open(image_path) as img:
                width, height = img.size

            total_pixels = width * height
            # 640x480 = 307,200 px minimum — allow any aspect ratio
            min_pixels = self.min_width * self.min_height

            if total_pixels < min_pixels:
                return {
                    "valid": False,
                    "reason": f"Resolution too low: {width}x{height} ({total_pixels} px, minimum: {min_pixels} px)",
                    "width": width,
                    "height": height
                }

            return {
                "valid": True,
                "reason": "",
                "width": width,
                "height": height
            }

        except Exception as e:
            logger.error(f"Resolution check failed: {e}")
            return {"valid": False, "reason": f"Failed to read image dimensions: {str(e)}"}
    
    def compute_image_hash(self, image_path: str, hash_size: int = 16) -> Optional[str]:
        """
        Generate perceptual hash for duplicate detection.
        Uses average hash (aHash) which is robust to minor changes.
        
        Args:
            image_path: Path to image file
            hash_size: Hash size (larger = more precise)
            
        Returns:
            Hex string of image hash, or None if failed
        """
        try:
            with Image.open(image_path) as img:
                # Use average hash - robust to scaling and minor modifications
                img_hash = imagehash.average_hash(img, hash_size=hash_size)
                return str(img_hash)
        
        except Exception as e:
            logger.error(f"Hash computation failed: {e}")
            return None
    
    def is_duplicate(
        self, 
        image_hash: str, 
        existing_hashes: List[str], 
        threshold: int = 5
    ) -> Dict[str, any]:
        """
        Check if image is a duplicate of existing uploads.
        
        Args:
            image_hash: Hash of new image
            existing_hashes: List of existing image hashes
            threshold: Hamming distance threshold (0 = exact match, higher = more lenient)
            
        Returns:
            Dict with 'is_duplicate' (bool), 'closest_distance' (int), 'reason' (str)
        """
        if not image_hash or not existing_hashes:
            return {"is_duplicate": False, "closest_distance": None, "reason": ""}
        
        try:
            new_hash = imagehash.hex_to_hash(image_hash)
            min_distance = float('inf')
            
            for existing_hash_str in existing_hashes:
                try:
                    existing_hash = imagehash.hex_to_hash(existing_hash_str)
                    distance = new_hash - existing_hash  # Hamming distance
                    min_distance = min(min_distance, distance)
                    
                    if distance <= threshold:
                        return {
                            "is_duplicate": True,
                            "closest_distance": distance,
                            "reason": f"Duplicate image detected (distance: {distance})"
                        }
                except Exception as e:
                    logger.warning(f"Failed to compare hash: {e}")
                    continue
            
            return {
                "is_duplicate": False,
                "closest_distance": min_distance if min_distance != float('inf') else None,
                "reason": ""
            }
        
        except Exception as e:
            logger.error(f"Duplicate check failed: {e}")
            return {"is_duplicate": False, "closest_distance": None, "reason": ""}
    
    def check_blur(self, image_path: str, threshold: float = 100.0) -> Dict[str, any]:
        """
        Detect if image is too blurry using Laplacian variance.
        
        Args:
            image_path: Path to image file
            threshold: Variance threshold (lower = blurrier)
            
        Returns:
            Dict with 'is_blurry' (bool), 'blur_score' (float), 'reason' (str)
        """
        try:
            image = cv2.imread(image_path)
            if image is None:
                return {"is_blurry": True, "blur_score": 0, "reason": "Failed to load image"}
            
            gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
            laplacian_var = cv2.Laplacian(gray, cv2.CV_64F).var()
            
            is_blurry = laplacian_var < threshold
            
            return {
                "is_blurry": is_blurry,
                "blur_score": float(laplacian_var),
                "reason": f"Image too blurry (score: {laplacian_var:.2f})" if is_blurry else ""
            }
        
        except Exception as e:
            logger.error(f"Blur detection failed: {e}")
            return {"is_blurry": False, "blur_score": 0, "reason": ""}
    
    def check_all(
        self, 
        image_path: str, 
        existing_hashes: Optional[List[str]] = None,
        check_blur_enabled: bool = True
    ) -> Dict[str, any]:
        """
        Run all pre-filter checks on an image.
        
        Args:
            image_path: Path to image file
            existing_hashes: Optional list of existing image hashes for duplicate detection
            check_blur_enabled: Whether to check for blur
            
        Returns:
            Dict with 'valid' (bool), 'reason' (str), 'details' (dict), 'image_hash' (str)
        """
        results = {
            "valid": True,
            "reason": "",
            "details": {},
            "image_hash": None
        }
        
        # Check 1: Corruption
        corruption_check = self.check_image_corruption(image_path)
        results["details"]["corruption"] = corruption_check
        if not corruption_check["valid"]:
            results["valid"] = False
            results["reason"] = corruption_check["reason"]
            return results
        
        # Check 2: Resolution
        resolution_check = self.check_resolution(image_path)
        results["details"]["resolution"] = resolution_check
        if not resolution_check["valid"]:
            results["valid"] = False
            results["reason"] = resolution_check["reason"]
            return results
        
        # Check 3: Blur (optional)
        if check_blur_enabled:
            blur_check = self.check_blur(image_path)
            results["details"]["blur"] = blur_check
            if blur_check["is_blurry"]:
                results["valid"] = False
                results["reason"] = blur_check["reason"]
                return results
        
        # Check 4: Compute hash
        image_hash = self.compute_image_hash(image_path)
        results["image_hash"] = image_hash
        
        # Check 5: Duplicate detection
        if existing_hashes and image_hash:
            duplicate_check = self.is_duplicate(image_hash, existing_hashes)
            results["details"]["duplicate"] = duplicate_check
            if duplicate_check["is_duplicate"]:
                results["valid"] = False
                results["reason"] = duplicate_check["reason"]
                return results
        
        logger.info(f"✅ Pre-filter passed for {Path(image_path).name}")
        return results


# Singleton instance
_prefilter_instance = None

def get_prefilter(min_width: int = 640, min_height: int = 480) -> ImagePrefilter:
    """Get or create prefilter instance."""
    global _prefilter_instance
    if _prefilter_instance is None:
        _prefilter_instance = ImagePrefilter(min_width, min_height)
    return _prefilter_instance
