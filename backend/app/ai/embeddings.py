"""
Embedding generation for RAG-based similarity matching.
Uses CLIP for image embeddings and SentenceTransformers for text.
"""

import torch
import open_clip  # Changed from import clip
from sentence_transformers import SentenceTransformer
from PIL import Image
from typing import List, Optional
import numpy as np
from loguru import logger
from app.config import get_settings

settings = get_settings()


class EmbeddingGenerator:
    """Generate embeddings for images and text."""
    
    def __init__(
        self, 
        clip_model_name: Optional[str] = None,
        text_model_name: str = "all-MiniLM-L6-v2"
    ):
        """
        Initialize embedding models.
        
        Args:
            clip_model_name: CLIP model name (defaults to config)
            text_model_name: SentenceTransformer model name
        """
        # Map model name for OpenCLIP
        self.clip_model_name = clip_model_name or settings.clip_model
        if self.clip_model_name == "ViT-B/32":
            self.clip_model_name = "ViT-B-32"  # OpenCLIP naming convention
            self.pretrained_name = "laion2b_s34b_b79k"  # High quality weights
        else:
            self.pretrained_name = "openai"  # Fallback
            
        self.text_model_name = text_model_name
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        
        self.clip_model = None
        self.clip_preprocess = None
        self.text_model = None
        
        self._load_models()
    
    def _load_models(self):
        """Load CLIP and text embedding models."""
        try:
            # Load OpenCLIP
            logger.info(f"Loading OpenCLIP model: {self.clip_model_name} ({self.pretrained_name})")
            self.clip_model, _, self.clip_preprocess = open_clip.create_model_and_transforms(
                self.clip_model_name, 
                pretrained=self.pretrained_name,
                device=self.device
            )
            logger.info(f"✅ CLIP loaded on {self.device}")
            
            # Load SentenceTransformer
            logger.info(f"Loading text model: {self.text_model_name}")
            self.text_model = SentenceTransformer(self.text_model_name)
            logger.info("✅ Text embedding model loaded")
            
        except Exception as e:
            logger.error(f"Failed to load embedding models: {e}")
            logger.warning("Embedding generation will be disabled")
    
    def generate_image_embedding(self, image_path: str) -> Optional[np.ndarray]:
        """
        Generate CLIP embedding for an image.
        
        Args:
            image_path: Path to image file
            
        Returns:
            NumPy array of shape (512,) or None if failed
        """
        if self.clip_model is None:
            logger.warning("CLIP model not available")
            return None
        
        try:
            # Load and preprocess image
            image = Image.open(image_path).convert("RGB")
            image_input = self.clip_preprocess(image).unsqueeze(0).to(self.device)
            
            # Generate embedding
            with torch.no_grad():
                embedding = self.clip_model.encode_image(image_input)
                embedding = embedding.cpu().numpy().flatten()
            
            # Normalize
            embedding = embedding / np.linalg.norm(embedding)
            
            logger.debug(f"Generated image embedding: shape {embedding.shape}")
            return embedding
        
        except Exception as e:
            logger.error(f"Image embedding generation failed: {e}")
            return None
    
    def generate_text_embedding(self, text: str) -> Optional[np.ndarray]:
        """
        Generate SentenceTransformer embedding for text.
        
        Args:
            text: Input text
            
        Returns:
            NumPy array of shape (384,) or None if failed
        """
        if self.text_model is None:
            logger.warning("Text model not available")
            return None
        
        if not text or not text.strip():
            logger.warning("Empty text provided for embedding")
            return None
        
        try:
            # Generate embedding
            embedding = self.text_model.encode(text, convert_to_numpy=True)
            
            # Normalize
            embedding = embedding / np.linalg.norm(embedding)
            
            logger.debug(f"Generated text embedding: shape {embedding.shape}")
            return embedding
        
        except Exception as e:
            logger.error(f"Text embedding generation failed: {e}")
            return None
    
    def compute_similarity(
        self, 
        embedding1: np.ndarray, 
        embedding2: np.ndarray
    ) -> float:
        """
        Compute cosine similarity between two embeddings.
        
        Args:
            embedding1: First embedding
            embedding2: Second embedding
            
        Returns:
            Similarity score (0-1, higher = more similar)
        """
        try:
            # Ensure embeddings are normalized
            emb1 = embedding1 / np.linalg.norm(embedding1)
            emb2 = embedding2 / np.linalg.norm(embedding2)
            
            # Cosine similarity
            similarity = np.dot(emb1, emb2)
            
            # Clip to [0, 1] range
            similarity = np.clip(similarity, 0, 1)
            
            return float(similarity)
        
        except Exception as e:
            logger.error(f"Similarity computation failed: {e}")
            return 0.0
    
    def find_most_similar(
        self,
        query_embedding: np.ndarray,
        candidate_embeddings: List[np.ndarray],
        top_k: int = 5
    ) -> List[tuple]:
        """
        Find most similar embeddings from candidates.
        
        Args:
            query_embedding: Query embedding
            candidate_embeddings: List of candidate embeddings
            top_k: Number of top results to return
            
        Returns:
            List of (index, similarity_score) tuples, sorted by similarity
        """
        if not candidate_embeddings:
            return []
        
        try:
            similarities = [
                (i, self.compute_similarity(query_embedding, emb))
                for i, emb in enumerate(candidate_embeddings)
            ]
            
            # Sort by similarity (descending)
            similarities.sort(key=lambda x: x[1], reverse=True)
            
            return similarities[:top_k]
        
        except Exception as e:
            logger.error(f"Similarity search failed: {e}")
            return []


# Singleton instance
_embedding_generator = None

def get_embedding_generator(
    clip_model_name: Optional[str] = None,
    text_model_name: str = "all-MiniLM-L6-v2"
) -> EmbeddingGenerator:
    """Get or create embedding generator instance."""
    global _embedding_generator
    if _embedding_generator is None:
        _embedding_generator = EmbeddingGenerator(clip_model_name, text_model_name)
    return _embedding_generator
