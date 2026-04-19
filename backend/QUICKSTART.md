# Quick Setup Guide

## 🚀 Getting Started in 5 Minutes

### 1. Install Dependencies

```bash
cd /home/rocroshan/Desktop/2026/Ram/DeepBlueS11/backend
python3.11 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Configure Environment

```bash
cp .env.example .env
nano .env  # or use your preferred editor
```

**Required variables**:
```env
SUPABASE_URL=https://mjsylrqiyjupixopbdez.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here
GEMINI_API_KEY=your_gemini_api_key_here
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret
```

> **Note**: Using Cloudinary instead of Firebase Storage (no premium needed!)

### 3. Database Setup

**Option A: Use Your Existing Schema** (Recommended)

Your existing `reports_analysis` table works out of the box! No migration needed.

**Option B: Add Advanced Features** (Optional)

For geo-clustering, embeddings, and trust scoring:
1. Open Supabase SQL Editor
2. Copy contents of `database/enhanced_schema.sql`
3. Execute the SQL

### 4. Run Server

```bash
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 5. Test API

Open browser: `http://localhost:8000/docs`

---

## 🧪 Quick Test

```bash
# Health check
curl http://localhost:8000/health

# Get hazard types
curl http://localhost:8000/api/v1/hazards/types

# Submit test report (replace with actual image)
curl -X POST "http://localhost:8000/api/v1/reports" \
  -F "user_id=test_user" \
  -F "hazard_type=speed_camera" \
  -F "description=Test camera" \
  -F "latitude=12.9716" \
  -F "longitude=77.5946" \
  -F "image=@test_image.jpg"
```

---

## ⚠️ Troubleshooting

### Models Not Loading
```bash
# Check CUDA
python -c "import torch; print(torch.cuda.is_available())"
# Models will auto-fallback to CPU
```

### Cloudinary Errors
```bash
# Verify credentials in .env
# Check if your internet connection allows upload to Cloudinary
```

### Database Connection Failed
```bash
# Check Supabase URL and key in .env
# Ensure PostGIS extension is enabled
```

---

## 📚 Next Steps

1. Read [README.md](file:///home/rocroshan/Desktop/2026/Ram/DeepBlueS11/backend/README.md) for detailed documentation
2. Review [walkthrough.md](file:///home/rocroshan/.gemini/antigravity/brain/4395d581-160c-4b88-a926-39041a0fbc75/walkthrough.md) for implementation details
3. Check [implementation_plan.md](file:///home/rocroshan/.gemini/antigravity/brain/4395d581-160c-4b88-a926-39041a0fbc75/implementation_plan.md) for architecture overview
