# Migration Guide: Adapting to Your Existing Setup

## ✅ Changes Made

### 1. Image Storage: Firebase → Cloudinary

**Why**: You're already using Cloudinary (no Firebase premium needed)

**Changes**:
- ✅ Removed `firebase-admin` dependency
- ✅ Added `cloudinary==1.36.0` to requirements
- ✅ Created `app/services/cloudinary_service.py`
- ✅ Updated `app/api/reports.py` to use Cloudinary upload
- ✅ Updated `.env.example` with Cloudinary credentials

**Configuration Required**:
```env
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

---

### 2. Database Schema: Enhanced → Existing

**Why**: You have an existing `reports_analysis` table

**Your Schema**:
```sql
CREATE TABLE reports_analysis (
    report_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    incident_type VARCHAR(50),
    description TEXT,
    severity INT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    image_url TEXT,
    status VARCHAR(20) DEFAULT 'Pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    verification_confidence DECIMAL(5,2),
    ai_reasoning TEXT,
    manual_review_needed BOOLEAN DEFAULT FALSE
);
```

**Field Mappings**:
| Our Original | Your Schema |
|--------------|-------------|
| `hazard_type` | `incident_type` |
| `verification_status` | `status` |
| `ai_confidence` | `verification_confidence` |
| `submitted_at` | `created_at` |
| `community_reports` table | `reports_analysis` table |

**Changes Made**:
- ✅ Updated `app/api/reports.py` to use `reports_analysis` table
- ✅ Updated `app/services/verification.py` to use existing fields
- ✅ Mapped `hazard_type` → `incident_type`
- ✅ Mapped status values: `ACCEPTED` → `Verified`, `REJECTED` → `Rejected`

---

## 🚀 Quick Setup

### 1. Install Dependencies

```bash
cd backend
pip install -r requirements.txt
```

**New dependency**: `cloudinary==1.36.0`

### 2. Configure Environment

```bash
cp .env.example .env
nano .env
```

**Add Cloudinary credentials**:
```env
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

### 3. Your Existing Schema Works!

No database migration needed. The backend now works with your existing `reports_analysis` table.

---

## 📊 What Works Out of the Box

### ✅ Core Features
- [x] Report submission with Cloudinary upload
- [x] 4-stage AI verification pipeline
- [x] Confidence scoring
- [x] Status updates (`Pending` → `Verified`/`Rejected`)
- [x] AI reasoning storage
- [x] Manual review flagging

### ⚠️ Advanced Features (Optional)

These features require additional tables (can be added later):

- [ ] Geo-clustering (needs `hazard_clusters` table)
- [ ] RAG similarity (needs `hazard_embeddings` table)
- [ ] User trust scoring (needs `user_trust_scores` table)
- [ ] Revalidation (needs `revalidation_requests` table)

**To enable**: Run `database/enhanced_schema.sql` in Supabase

---

## 🧪 Testing

### 1. Start Server

```bash
uvicorn app.main:app --reload
```

### 2. Submit Test Report

```bash
curl -X POST "http://localhost:8000/api/v1/reports" \
  -F "user_id=test_user" \
  -F "hazard_type=speed_camera" \
  -F "description=Test camera" \
  -F "latitude=12.9716" \
  -F "longitude=77.5946" \
  -F "severity=3" \
  -F "image=@test_image.jpg"
```

### 3. Check Report Status

```bash
curl "http://localhost:8000/api/v1/reports/{report_id}"
```

**Expected Response**:
```json
{
  "report_id": "uuid",
  "user_id": "test_user",
  "incident_type": "speed_camera",
  "status": "Verified",  // or "Pending" or "Rejected"
  "verification_confidence": 85.5,
  "ai_reasoning": "Valid speed camera detected...",
  "manual_review_needed": false
}
```

---

## 🔄 Integration with Your Android App

### Current Flow (Assumed)
```
Android App → Firebase Functions → PostgreSQL
```

### New Flow (Recommended)
```
Android App → FastAPI Backend → PostgreSQL
```

### Migration Options

#### Option 1: Parallel Running (Recommended for Testing)
- Keep Firebase Functions for existing features
- Use FastAPI for AI verification
- Both write to same `reports_analysis` table

#### Option 2: Full Migration
- Replace Firebase Functions with FastAPI
- Update Android app to call FastAPI endpoints
- Decommission Firebase Functions

---

## 📝 API Endpoints

### Submit Report
```http
POST /api/v1/reports
Content-Type: multipart/form-data

Fields:
- user_id: string
- hazard_type: string (incident_type)
- description: string
- latitude: float
- longitude: float
- severity: int (1-5)
- image: file
```

### Get Report
```http
GET /api/v1/reports/{report_id}
```

### Get User Reports
```http
GET /api/v1/reports/user/{user_id}?limit=20&offset=0
```

---

## 🎯 What's Different

### Simplified for Your Schema

**Removed** (not in your schema):
- `location` (PostGIS POINT) - using lat/lon directly
- `device_fingerprint` - can be added if needed
- `image_hash` - duplicate detection disabled for now
- `hazard_id` - clustering disabled (optional feature)

**Kept** (matches your schema):
- `report_id` (VARCHAR)
- `user_id` (VARCHAR)
- `incident_type` (VARCHAR)
- `description` (TEXT)
- `severity` (INT)
- `latitude`, `longitude` (DECIMAL)
- `image_url` (TEXT)
- `status` (VARCHAR)
- `verification_confidence` (DECIMAL)
- `ai_reasoning` (TEXT)
- `manual_review_needed` (BOOLEAN)

---

## 🚧 Optional Enhancements

If you want to enable advanced features later:

### 1. Add Geo-Clustering

```sql
-- Add location column
ALTER TABLE reports_analysis 
ADD COLUMN location geography(POINT, 4326);

-- Update existing rows
UPDATE reports_analysis 
SET location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326);

-- Create spatial index
CREATE INDEX idx_reports_location ON reports_analysis USING GIST(location);
```

### 2. Add Embedding Storage

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE hazard_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id VARCHAR(64) REFERENCES reports_analysis(report_id),
    image_embedding vector(512),
    description_embedding vector(384),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

### 3. Add User Trust Scores

```sql
CREATE TABLE user_trust_scores (
    user_id VARCHAR(64) PRIMARY KEY,
    total_reports INT DEFAULT 0,
    accepted_reports INT DEFAULT 0,
    rejected_reports INT DEFAULT 0,
    trust_score DECIMAL(5,2) DEFAULT 50.0,
    reward_points INT DEFAULT 0,
    badge_level VARCHAR(20) DEFAULT 'BRONZE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

## ✅ Summary

**What You Get**:
- ✅ Cloudinary image upload (no Firebase premium)
- ✅ 4-stage AI verification pipeline
- ✅ Works with your existing `reports_analysis` schema
- ✅ No database migration required
- ✅ Drop-in replacement for your current system

**Next Steps**:
1. Install dependencies: `pip install -r requirements.txt`
2. Configure Cloudinary in `.env`
3. Start server: `uvicorn app.main:app --reload`
4. Test with sample report
5. Integrate with Android app

---

## 🆘 Troubleshooting

### Cloudinary Upload Fails
```bash
# Check credentials
echo $CLOUDINARY_CLOUD_NAME
echo $CLOUDINARY_API_KEY

# Test upload manually
python -c "import cloudinary; cloudinary.config(cloud_name='...', api_key='...', api_secret='...'); print('OK')"
```

### Database Connection Issues
```bash
# Check Supabase URL
echo $SUPABASE_URL

# Test connection
python -c "from app.database import get_supabase; print(get_supabase().table('reports_analysis').select('*').limit(1).execute())"
```

### AI Models Not Loading
```bash
# Check CUDA
python -c "import torch; print(torch.cuda.is_available())"

# Models will auto-download on first run
# Or manually download YOLO:
wget https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt -O models/yolov8n.pt
```

---

**Status**: ✅ **Ready to Use**

All changes have been made to work with your existing setup. No breaking changes to your current schema or workflow.
