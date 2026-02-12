"""
YOLO-based object detection for hazard verification.

IMPORTANT: Standard YOLO models (COCO) only know 80 generic classes.
They CANNOT identify specific hazards like "speed camera", "pothole", etc.
Instead, YOLO is used for SCENE VALIDATION — checking that the image
looks like a real outdoor/road scene, not a random screenshot or meme.
Gemini handles the actual hazard identification.
"""

from ultralytics import YOLO
from typing import Dict, List, Optional
from pathlib import Path
from loguru import logger
from app.config import get_settings

settings = get_settings()


class HazardObjectDetector:
    """YOLO-based scene validation for road hazard reports."""

    # Classes that indicate an outdoor / road / traffic scene
    ROAD_SCENE_CLASSES = {
        "car", "truck", "bus", "motorcycle", "bicycle",
        "traffic light", "stop sign",
        "person", "dog", "cat", "horse", "cow",
        "fire hydrant", "bench",
    }

    # Classes that indicate the image is NOT a road scene (likely fake/irrelevant)
    INDOOR_IRRELEVANT_CLASSES = {
        "laptop", "keyboard", "mouse", "cell phone", "remote",
        "tv", "monitor", "microwave", "oven", "toaster",
        "refrigerator", "sink", "toilet", "couch", "bed",
        "dining table", "book", "teddy bear", "hair drier",
    }

    # Hazard types that we expect to see specific COCO classes for
    # If these are detected, it boosts confidence
    HAZARD_BONUS_CLASSES = {
        "accident": ["car", "truck", "person", "motorcycle"],
        "traffic_jam": ["car", "truck", "bus"],
        "animal_crossing": ["dog", "cat", "cow", "horse", "elephant"],
        "construction": ["truck", "person"],
    }

    def _normalize_hazard_type(self, hazard_type: str) -> str:
        """Normalize user-entered hazard tags to mapping keys."""
        if not hazard_type:
            return ""
        cleaned = hazard_type.strip().lower()
        if cleaned.startswith("#"):
            cleaned = cleaned[1:]
        cleaned = cleaned.replace("-", "_")
        return cleaned

    def __init__(self, model_path: Optional[str] = None):
        self.model_path = model_path or settings.yolo_model_path
        self.model = None
        self._load_model()

    def _load_model(self):
        """Load YOLO model."""
        try:
            logger.info(f"Loading YOLO model: {self.model_path}")
            if not Path(self.model_path).exists():
                logger.warning(f"Model not found at {self.model_path}, using YOLOv8n")
                self.model = YOLO("yolov8n.pt")  # Small fallback
            else:
                self.model = YOLO(self.model_path)
            logger.info("✅ YOLO model loaded successfully")
        except Exception as e:
            logger.error(f"Failed to load YOLO model: {e}")
            logger.warning("Object detection will be disabled")

    def detect_objects(
        self,
        image_path: str,
        confidence_threshold: Optional[float] = None
    ) -> Dict[str, any]:
        """
        Detect objects in image using YOLO.
        
        Args:
            image_path: Path to image file
            confidence_threshold: Minimum confidence (defaults to config)
            
        Returns:
            Dict with 'detections' (list), 'count' (int), 'classes' (list)
        """
        if self.model is None:
            logger.warning("YOLO model not available, skipping detection")
            return {
                "detections": [],
                "count": 0,
                "classes": [],
                "reason": "Model not loaded"
            }

        threshold = confidence_threshold or settings.yolo_confidence_threshold

        try:
            results = self.model(image_path, conf=threshold, verbose=False)

            detections = []
            detected_classes = set()

            for result in results:
                boxes = result.boxes
                for box in boxes:
                    class_id = int(box.cls[0])
                    class_name = result.names[class_id]
                    confidence = float(box.conf[0])
                    bbox = box.xyxy[0].tolist()

                    detections.append({
                        "class": class_name,
                        "confidence": confidence,
                        "bbox": bbox
                    })
                    detected_classes.add(class_name)

            logger.info(f"🔍 YOLO detected {len(detections)} objects: {list(detected_classes)}")

            return {
                "detections": detections,
                "count": len(detections),
                "classes": list(detected_classes)
            }

        except Exception as e:
            logger.error(f"YOLO detection failed: {e}")
            return {
                "detections": [],
                "count": 0,
                "classes": [],
                "reason": f"Detection failed: {str(e)}"
            }

    def validate_hazard_match(
        self,
        detections: List[Dict],
        hazard_type: str
    ) -> Dict[str, any]:
        """
        Validate the image as a plausible road/outdoor scene.

        Instead of trying to identify the hazard (which COCO YOLO can't do),
        we check:
        1. Are there ANY objects? (image is not blank/abstract)
        2. Are there road-scene objects? (cars, people, traffic lights)
        3. Are there indoor/irrelevant objects? (laptop, toilet = likely fake)
        4. For certain hazard types, do we see bonus matching classes?

        The score reflects scene plausibility, NOT hazard identification.
        Gemini handles the actual hazard verification.
        """
        normalized_type = self._normalize_hazard_type(hazard_type)

        if not detections:
            # No objects detected — this is OKAY for many hazards
            # (speed cameras, potholes, etc. are not COCO classes)
            # Give a neutral score and let Gemini decide
            logger.info(f"No YOLO detections for '{hazard_type}' — deferring to Gemini")
            return {
                "match": True,
                "match_score": 0.5,
                "reason": "No objects detected by YOLO — Gemini will verify the hazard",
                "matched_classes": [],
                "scene_type": "unknown"
            }

        detected_classes = set(d["class"] for d in detections)

        # Check for indoor/irrelevant objects
        indoor_found = detected_classes & self.INDOOR_IRRELEVANT_CLASSES
        if indoor_found and not (detected_classes & self.ROAD_SCENE_CLASSES):
            # Only indoor objects, no road objects at all — suspicious
            logger.warning(f"Image appears indoor/irrelevant: {indoor_found}")
            return {
                "match": False,
                "match_score": 0.15,
                "reason": f"Image appears to be indoor/irrelevant (detected: {list(indoor_found)})",
                "matched_classes": [],
                "scene_type": "indoor"
            }

        # Check for road scene objects
        road_objects = detected_classes & self.ROAD_SCENE_CLASSES
        base_score = 0.5

        if road_objects:
            # Good — we see road-related objects
            base_score = 0.65
            logger.info(f"Road scene objects detected: {road_objects}")

        # Check for hazard-specific bonus classes
        bonus_classes = self.HAZARD_BONUS_CLASSES.get(normalized_type, [])
        bonus_found = [cls for cls in detected_classes if cls in bonus_classes]

        if bonus_found:
            # Extra boost — we see objects relevant to this specific hazard
            avg_conf = sum(
                d["confidence"] for d in detections if d["class"] in bonus_classes
            ) / len([d for d in detections if d["class"] in bonus_classes])
            base_score = max(base_score, 0.6 + avg_conf * 0.3)
            logger.info(f"Bonus hazard classes found: {bonus_found}, score={base_score:.2f}")

        return {
            "match": True,
            "match_score": min(base_score, 1.0),
            "reason": f"Scene validation: detected {list(detected_classes)}. "
                      f"Road objects: {list(road_objects) if road_objects else 'none'}. "
                      f"Hazard match deferred to Gemini.",
            "matched_classes": list(road_objects) + bonus_found,
            "scene_type": "outdoor_road" if road_objects else "outdoor_other"
        }

    def analyze_hazard(
        self,
        image_path: str,
        hazard_type: str
    ) -> Dict[str, any]:
        """Complete analysis: detect objects and validate scene."""
        detection_result = self.detect_objects(image_path)

        validation_result = self.validate_hazard_match(
            detection_result["detections"],
            hazard_type
        )

        return {
            "detections": detection_result["detections"],
            "detected_count": detection_result["count"],
            "detected_classes": detection_result["classes"],
            "match": validation_result["match"],
            "match_score": validation_result["match_score"],
            "reason": validation_result["reason"],
            "matched_classes": validation_result.get("matched_classes", []),
            "scene_type": validation_result.get("scene_type", "unknown")
        }


# Singleton instance
_detector_instance = None

def get_object_detector(model_path: Optional[str] = None) -> HazardObjectDetector:
    """Get or create object detector instance."""
    global _detector_instance
    if _detector_instance is None:
        _detector_instance = HazardObjectDetector(model_path)
    return _detector_instance
