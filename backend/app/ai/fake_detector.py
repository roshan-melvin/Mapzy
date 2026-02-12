"""
AI-generated image detection module.
Detects synthetic images created by diffusion models, GANs, etc.
"""

import torch
from transformers import AutoFeatureExtractor, AutoModelForImageClassification
from PIL import Image
from typing import Dict, Optional
from loguru import logger
from functools import lru_cache
from app.config import get_settings

settings = get_settings()


class FakeImageDetector:
    """Detect AI-generated images using pretrained models."""
    
    def __init__(self, model_name: Optional[str] = None):
        """
        Initialize fake image detector.
        
        Args:
            model_name: HuggingFace model name (defaults to config)
        """
        self.model_name = model_name or settings.fake_detector_model
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model = None
        self.feature_extractor = None
        self._load_model()
    
    def _load_model(self):
        """Load pretrained model and feature extractor."""
        try:
            logger.info(f"Loading AI-gen detector: {self.model_name}")
            self.feature_extractor = AutoFeatureExtractor.from_pretrained(self.model_name)
            self.model = AutoModelForImageClassification.from_pretrained(self.model_name)
            self.model.to(self.device)
            self.model.eval()
            logger.info(f"✅ AI-gen detector loaded on {self.device}")
        except Exception as e:
            logger.error(f"Failed to load AI-gen detector: {e}")
            logger.warning("AI-generated detection will be disabled")
    
    def detect_ai_generated(self, image_path: str) -> Dict[str, any]:
        """
        Detect if image is AI-generated.
        
        Args:
            image_path: Path to image file
            
        Returns:
            Dict with 'probability' (float 0-1), 'is_ai_generated' (bool), 'confidence' (str)
        """
        if self.model is None:
            logger.warning("AI-gen detector not available, skipping check")
            return {
                "probability": 0.0,
                "is_ai_generated": False,
                "confidence": "unknown",
                "reason": "Detector not loaded"
            }
        
        try:
            # Load and preprocess image
            image = Image.open(image_path).convert("RGB")
            inputs = self.feature_extractor(images=image, return_tensors="pt")
            inputs = {k: v.to(self.device) for k, v in inputs.items()}
            
            # Run inference
            with torch.no_grad():
                outputs = self.model(**inputs)
                logits = outputs.logits
                probabilities = torch.nn.functional.softmax(logits, dim=-1)
            
            # Get AI-generated probability
            # Model outputs: {0: 'artificial', 1: 'human'}
            # So index 0 = AI-generated probability
            ai_prob = float(probabilities[0][0])  # Index 0 = 'artificial' (AI-generated)
            
            # Use configurable threshold for AI detection
            is_ai = ai_prob > settings.ai_gen_threshold
            
            # Confidence level
            if ai_prob > 0.9:
                confidence = "very_high"
            elif ai_prob > 0.7:
                confidence = "high"
            elif ai_prob > 0.5:
                confidence = "medium"
            else:
                confidence = "low"
            
            result = {
                "probability": ai_prob,
                "is_ai_generated": is_ai,
                "confidence": confidence,
                "reason": f"AI-generated probability: {ai_prob:.2%}" if is_ai else ""
            }
            
            if is_ai:
                logger.warning(f"🚫 AI-generated image detected: {ai_prob:.2%}")
            else:
                logger.info(f"✅ Real image (AI prob: {ai_prob:.2%})")
            
            return result
        
        except Exception as e:
            logger.error(f"AI-gen detection failed: {e}")
            return {
                "probability": 0.0,
                "is_ai_generated": False,
                "confidence": "error",
                "reason": f"Detection failed: {str(e)}"
            }


# Singleton instance
_detector_instance = None

def get_fake_detector(model_name: Optional[str] = None) -> FakeImageDetector:
    """Get or create fake detector instance."""
    global _detector_instance
    if _detector_instance is None:
        _detector_instance = FakeImageDetector(model_name)
    return _detector_instance
