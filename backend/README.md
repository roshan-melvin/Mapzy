# DeepBlueS11 Backend - AI Hazard Verification System

## 🚀 Overview

Production-grade FastAPI backend implementing a **hybrid AI-based community hazard verification system** with:

- **4-Stage AI Verification Pipeline**: Pre-filter → Fake Detection → YOLO → Gemini
- **Geo-Spatial Clustering**: PostGIS-based hazard grouping
- **RAG Similarity Matching**: CLIP embeddings for duplicate detection
- **Confidence Lifecycle**: Time-based decay and community revalidation
- **Trust Scoring**: User reputation and reward system

---

## 📋 Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Mobile App (Android)                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   FastAPI Backend (Port 8000)                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         AI Verification Pipeline                       │ │
│  │  1. Pre-filter (OpenCV, imagehash)                     │ │
│  │  2. AI-Gen Detection (HuggingFace Transformers)        │ │
│  │  3. Object Detection (YOLOv8)                          │ │
│  │  4. Reasoning (Google Gemini 1.5 Flash)                │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         Geo-Clustering (PostGIS)                       │ │
│  │  - 50m radius matching                                 │ │
│  │  - Haversine distance calculation                      │ │
│  └────────────────────────────────────────────────────────┘ │
│  ┌────────────────────────────────────────────────────────┐ │
│  │         RAG Similarity (CLIP + FAISS)                  │ │
│  │  - Image embeddings (512-dim)                          │ │
│  │  - Duplicate detection                                 │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Supabase PostgreSQL + PostGIS                   │
│  - hazard_clusters (geo-indexed)                             │
│  - community_reports (with AI results)                       │
│  - user_trust_scores (reputation)                            │
│  - hazard_embeddings (pgvector)                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

### Core
- **Python 3.11+**
- **FastAPI** - Async API framework
- **Uvicorn** - ASGI server

### AI/ML
- **PyTorch** - Deep learning
- **Transformers (HuggingFace)** - AI-gen detection
- **Ultralytics YOLOv8** - Object detection
- **Google Gemini API** - Multimodal reasoning
- **OpenCLIP** - Image embeddings
- **SentenceTransformers** - Text embeddings

### Database
- **Supabase PostgreSQL** - Primary database
- **PostGIS** - Geo-spatial queries
- **pgvector** - Vector similarity search
- **Cloudinary** - Image storage

### Background Processing
- **Celery** - Task queue (optional)
- **Redis** - Task broker

---

## 📦 Installation

### 1. Clone Repository

```bash
cd /home/rocroshan/Desktop/2026/Ram/DeepBlueS11/backend
```

### 2. Create Virtual Environment

```bash
python3.11 -m venv venv
source venv/bin/activate  # Linux/Mac
# or
venv\Scripts\activate  # Windows
```

### 3. Install Dependencies

```bash
pip install -r requirements.txt
```

### 4. Configure Environment

```bash
cp .env.example .env
# Edit .env with your credentials
```

Required environment variables:
- `SUPABASE_URL` - Your Supabase project URL
- `SUPABASE_SERVICE_ROLE_KEY` - Service role key
- `GEMINI_API_KEY` - Google Gemini API key
- `CLOUDINARY_CLOUD_NAME` - Cloudinary cloud name
- `CLOUDINARY_API_KEY` - Cloudinary API key
- `CLOUDINARY_API_SECRET` - Cloudinary API secret

### 5. Set Up Database

Run the enhanced schema in Supabase SQL Editor:

```bash
# Copy contents of database/enhanced_schema.sql
# Paste into Supabase SQL Editor and execute
```

### 6. Download AI Models

```bash
# YOLO model will auto-download on first run
# Or manually download:
mkdir -p models
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt -O models/yolov8n.pt
```

---

## 🚀 Running the Server

### Development Mode

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### Production Mode

```bash
gunicorn app.main:app -w 4 -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000
```

### Using Python Directly

```bash
python -m app.main
```

---

## 📡 API Endpoints

### Reports

- `POST /api/v1/reports` - Submit new hazard report
- `GET /api/v1/reports/{report_id}` - Get report details
- `GET /api/v1/reports/user/{user_id}` - Get user's reports

### Hazards

- `GET /api/v1/hazards` - Get hazards by location
- `GET /api/v1/hazards/{hazard_id}` - Get hazard details
- `GET /api/v1/hazards/revalidation/nearby` - Get hazards needing revalidation
- `POST /api/v1/hazards/{hazard_id}/revalidate` - Submit revalidation response
- `GET /api/v1/hazards/types` - Get supported hazard types
- `GET /api/v1/hazards/stats` - Get hazard statistics

### Users

- `GET /api/v1/users/{user_id}/stats` - Get user statistics
- `GET /api/v1/users/leaderboard` - Get top users
- `GET /api/v1/users/badges` - Get badge information

### System

- `GET /` - API info
- `GET /health` - Health check
- `GET /docs` - Swagger UI
- `GET /redoc` - ReDoc documentation

---

## 🧪 Testing

### Test Report Submission

```bash
curl -X POST "http://localhost:8000/api/v1/reports" \
  -F "user_id=test_user_1" \
  -F "hazard_type=speed_camera" \
  -F "description=Speed camera on highway" \
  -F "latitude=12.9716" \
  -F "longitude=77.5946" \
  -F "image=@test_image.jpg" \
  -F "device_fingerprint=test_device_123"
```

### Get Nearby Hazards

```bash
curl "http://localhost:8000/api/v1/hazards?latitude=12.9716&longitude=77.5946&radius_km=5"
```

### Get User Stats

```bash
curl "http://localhost:8000/api/v1/users/test_user_1/stats"
```

---

## 🔧 Configuration

### Verification Thresholds

Edit `.env`:

```env
AI_GEN_THRESHOLD=0.7           # AI-generated image rejection threshold
YOLO_CONFIDENCE_THRESHOLD=0.5  # YOLO detection confidence
SIMILARITY_THRESHOLD=0.85      # Embedding similarity for clustering
DUPLICATE_THRESHOLD=0.95       # Duplicate image detection
```

### Geo-Clustering

```env
CLUSTER_RADIUS_METERS=50       # Urban hazard clustering radius
HIGHWAY_CLUSTER_RADIUS_METERS=100  # Highway camera radius
```

### Confidence Lifecycle

```env
INITIAL_CONFIRMATION_COUNT=2   # Reports needed for confirmation
REVALIDATION_DAYS=7            # Days before revalidation needed
EXPIRY_CONFIDENCE_THRESHOLD=30 # Confidence below which hazard expires
```

---

## 📊 Verification Workflow

1. **User submits report** → Image uploaded to Cloudinary
2. **Pre-filter checks** → Corruption, resolution, blur, duplicates
3. **AI-gen detection** → Reject synthetic images (>70% probability)
4. **YOLO object detection** → Verify hazard type match
5. **Gemini reasoning** → Context validation and edge cases
6. **Confidence calculation** → Weighted score from all stages
7. **Verdict decision** → ACCEPTED (≥70%), REJECTED (<40%), UNCERTAIN
8. **Geo-clustering** → Assign to existing cluster or create new
9. **Embedding storage** → Store CLIP embeddings for similarity
10. **Confidence update** → Update cluster confidence
11. **User trust update** → Adjust user reputation

---

## 🏆 Trust Scoring

### Badge Levels

- **Bronze**: 0+ accepted reports
- **Silver**: 10+ accepted reports
- **Gold**: 20+ accepted reports
- **Platinum**: 50+ accepted reports

### Reward Points

- Report accepted: +10 points
- Report rejected: -5 points
- Revalidation YES: +5 points
- Revalidation NO: +2 points

### Trust Score Formula

```
trust_score = (accepted_reports / total_reports) × 100
```

---

## 🔒 Security

- JWT authentication (ready for integration)
- Rate limiting per user
- Device fingerprinting
- Image hash duplicate detection
- AI-generated image rejection
- Embedding similarity anti-spam
- Cloudinary secure uploads

---

## 📈 Performance

- **Async processing** - Non-blocking I/O
- **Background tasks** - Verification runs async
- **Model caching** - Models loaded once at startup
- **Database indexing** - Geo-spatial and vector indexes
- **Batch processing** - Ready for Celery integration

---

## 🐛 Troubleshooting

### Models Not Loading

```bash
# Check CUDA availability
python -c "import torch; print(torch.cuda.is_available())"

# Use CPU if no GPU
# Models will auto-fallback to CPU
```

### Cloudinary Upload Fails

```bash
# Check environment variables
echo $CLOUDINARY_CLOUD_NAME
```

### PostGIS Errors

```sql
-- Enable extensions in Supabase
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;
```

---

## 📝 Development

### Project Structure

```
backend/
├── app/
│   ├── ai/                 # AI models
│   │   ├── prefilter.py
│   │   ├── fake_detector.py
│   │   ├── object_detector.py
│   │   ├── gemini_client.py
│   │   └── embeddings.py
│   ├── api/                # API routes
│   │   ├── reports.py
│   │   ├── hazards.py
│   │   └── users.py
│   ├── services/           # Business logic
│   │   ├── verification.py
│   │   ├── geo_clustering.py
│   │   ├── confidence.py
│   │   └── trust.py
│   ├── config.py
│   ├── database.py
│   └── main.py
├── database/
│   └── enhanced_schema.sql
├── requirements.txt
└── .env.example
```

---

## 🎯 Next Steps

1. **Fine-tune YOLO** on speed camera dataset
2. **Implement Celery** for distributed processing
3. **Add caching layer** (Redis)
4. **Set up monitoring** (Prometheus + Grafana)
5. **Deploy to cloud** (AWS/GCP/Azure)
6. **Add authentication** (JWT)
7. **Implement rate limiting** (per-user quotas)

---

## 📄 License

MIT License - See LICENSE file

---

## 👥 Contributors

- DeepBlueS11 Team

---

## 📞 Support

For issues or questions, please open an issue on GitHub.
