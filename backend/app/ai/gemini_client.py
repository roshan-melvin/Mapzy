"""
Google Gemini client for multimodal hazard verification.
Provides context-aware reasoning for edge cases.
"""

import google.generativeai as genai
from PIL import Image
from typing import Dict, Optional, Any
import json
from loguru import logger
from app.config import get_settings

settings = get_settings()


class GeminiVerificationClient:
    """Gemini-based multimodal reasoning for hazard verification."""
    
    VERIFICATION_PROMPT_TEMPLATE = """You are a road safety expert analyzing a community-reported hazard.

**Hazard Type**: {hazard_type}
**User Description**: {description}
**YOLO Detections**: {yolo_results}

Analyze the image and determine:
1. Is this a valid {hazard_type}?
2. Does the image match the description?
3. Is the context plausible (road environment, lighting, perspective)?
4. Are there any signs of manipulation or staging?

Provide your assessment as JSON:
{{
  "verdict": "VALID" | "INVALID" | "UNCERTAIN",
  "confidence": 0-100,
  "reasoning": "Brief explanation of your decision"
}}

Be strict but fair. Reject only when you are clearly confident the image does NOT match the hazard. If the evidence is weak or unclear, return "UNCERTAIN".
"""
    
    def __init__(self, api_key: Optional[str] = None):
        """
        Initialize Gemini client.
        
        Args:
            api_key: Gemini API key (defaults to config)
        """
        self.api_key = api_key or settings.gemini_api_key
        self.model = None
        self._configure_gemini()
    
    def _configure_gemini(self):
        """Configure Gemini API."""
        try:
            genai.configure(api_key=self.api_key)
            # Use Gemini 2.5 Flash (latest fast model)
            self.model = genai.GenerativeModel('models/gemini-2.5-flash')
            logger.info("✅ Gemini client configured")
        except Exception as e:
            logger.error(f"Failed to configure Gemini: {e}")
            logger.warning("Gemini reasoning will be disabled")
    
    def analyze_hazard_image(
        self,
        image_path: str,
        hazard_type: str,
        description: str,
        yolo_results: Optional[Dict] = None
    ) -> Dict[str, Any]:
        """
        Analyze hazard image using Gemini multimodal reasoning.
        
        Args:
            image_path: Path to image file
            hazard_type: Reported hazard type
            description: User's description
            yolo_results: Optional YOLO detection results
            
        Returns:
            Dict with 'verdict', 'confidence', 'reasoning'
        """
        if self.model is None:
            logger.warning("Gemini not available, returning neutral result")
            return {
                "verdict": "UNCERTAIN",
                "confidence": 50,
                "reasoning": "Gemini API not configured"
            }
        
        try:
            # Load image
            image = Image.open(image_path)
            
            # Format YOLO results for prompt
            yolo_summary = "None"
            if yolo_results and yolo_results.get("detections"):
                classes = yolo_results.get("detected_classes", [])
                yolo_summary = f"{len(yolo_results['detections'])} objects detected: {', '.join(classes)}"
            
            # Create prompt
            prompt = self.VERIFICATION_PROMPT_TEMPLATE.format(
                hazard_type=hazard_type,
                description=description or "No description provided",
                yolo_results=yolo_summary
            )
            
            # Generate response
            logger.info(f"🤖 Sending to Gemini for analysis...")
            response = self.model.generate_content([prompt, image])
            
            # Parse response
            result = self._parse_gemini_response(response.text)
            
            logger.info(f"✅ Gemini verdict: {result['verdict']} (confidence: {result['confidence']}%)")
            
            return result
        
        except Exception as e:
            logger.error(f"Gemini analysis failed: {e}")
            # When Gemini fails, be conservative - don't reject, send to manual review
            return {
                "verdict": "UNCERTAIN",
                "confidence": 60,  # Increased from 50 to avoid auto-rejection
                "reasoning": f"AI analysis failed, needs manual review: {str(e)}"
            }
    
    def _parse_gemini_response(self, response_text: str) -> Dict[str, Any]:
        """
        Parse Gemini's JSON response.
        
        Args:
            response_text: Raw response from Gemini
            
        Returns:
            Parsed dict with verdict, confidence, reasoning
        """
        try:
            # Try to extract JSON from response
            # Gemini might wrap JSON in markdown code blocks
            text = response_text.strip()
            
            # Remove markdown code blocks if present
            if text.startswith("```json"):
                text = text[7:]
            if text.startswith("```"):
                text = text[3:]
            if text.endswith("```"):
                text = text[:-3]
            
            text = text.strip()
            
            # Parse JSON
            result = json.loads(text)
            
            # Validate required fields
            verdict = result.get("verdict", "UNCERTAIN").upper()
            if verdict not in ["VALID", "INVALID", "UNCERTAIN"]:
                verdict = "UNCERTAIN"
            
            confidence = result.get("confidence", 50)
            if not isinstance(confidence, (int, float)) or confidence < 0 or confidence > 100:
                confidence = 50
            
            reasoning = result.get("reasoning", "No reasoning provided")
            
            return {
                "verdict": verdict,
                "confidence": float(confidence),
                "reasoning": reasoning
            }
        
        except json.JSONDecodeError as e:
            logger.warning(f"Failed to parse Gemini JSON: {e}")
            logger.debug(f"Raw response: {response_text}")
            
            # Fallback: extract verdict from text
            text_lower = response_text.lower()
            if "valid" in text_lower and "invalid" not in text_lower:
                verdict = "VALID"
                confidence = 70
            elif "invalid" in text_lower:
                verdict = "INVALID"
                confidence = 70
            else:
                verdict = "UNCERTAIN"
                confidence = 50
            
            return {
                "verdict": verdict,
                "confidence": confidence,
                "reasoning": response_text[:200]  # First 200 chars
            }
        except Exception as e:
            logger.error(f"Unexpected error parsing Gemini response: {e}")
            return {
                "verdict": "UNCERTAIN",
                "confidence": 50,
                "reasoning": "Failed to parse response"
            }


# Singleton instance
_gemini_instance = None

def get_gemini_client(api_key: Optional[str] = None) -> GeminiVerificationClient:
    """Get or create Gemini client instance."""
    global _gemini_instance
    if _gemini_instance is None:
        _gemini_instance = GeminiVerificationClient(api_key)
    return _gemini_instance
