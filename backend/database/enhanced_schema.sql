-- =====================================================
-- DeepBlueS11 FULL DATABASE SCHEMA
-- Includes Geo-Clustering, AI Verification, & Legacy Support
-- =====================================================

-- 1. ENABLE EXTENSIONS
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 2. HAZARD CLUSTERS (The intelligence layer for the map)
CREATE TABLE IF NOT EXISTS hazard_clusters (
    hazard_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hazard_type VARCHAR(50) NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    verified_image_count INT DEFAULT 0,
    confidence_score DECIMAL(5, 2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'PENDING',
    last_verified_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hazard_location ON hazard_clusters USING GIST(location);
CREATE INDEX IF NOT EXISTS idx_hazard_status ON hazard_clusters(status);

-- 3. COMMUNITY REPORTS (Detailed analysis for every user upload)
CREATE TABLE IF NOT EXISTS community_reports (
    report_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hazard_id UUID REFERENCES hazard_clusters(hazard_id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL,
    hazard_type VARCHAR(50) NOT NULL,
    description TEXT,
    image_url TEXT NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    latitude DECIMAL(10, 8) NOT NULL,
    longitude DECIMAL(11, 8) NOT NULL,
    verification_status VARCHAR(20) DEFAULT 'PENDING',
    ai_verdict VARCHAR(20),
    ai_confidence DECIMAL(5, 2),
    ai_reasoning TEXT,
    yolo_detections JSONB,
    gemini_response JSONB,
    prefilter_passed BOOLEAN DEFAULT FALSE,
    submitted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_report_hazard ON community_reports(hazard_id);
CREATE INDEX IF NOT EXISTS idx_report_user ON community_reports(user_id);

-- 4. USER TRUST & REWARDS
CREATE TABLE IF NOT EXISTS user_trust_scores (
    user_id VARCHAR(64) PRIMARY KEY,
    total_reports INT DEFAULT 0,
    accepted_reports INT DEFAULT 0,
    rejected_reports INT DEFAULT 0,
    trust_score DECIMAL(5, 2) DEFAULT 50.0,
    reward_points INT DEFAULT 0,
    badge_level VARCHAR(20) DEFAULT 'BRONZE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. HAZARD EMBEDDINGS (For Similarity Matching)
CREATE TABLE IF NOT EXISTS hazard_embeddings (
    embedding_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hazard_id UUID REFERENCES hazard_clusters(hazard_id) ON DELETE CASCADE,
    report_id UUID REFERENCES community_reports(report_id) ON DELETE CASCADE,
    image_embedding VECTOR(512), 
    description_embedding VECTOR(384),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_image_embedding ON hazard_embeddings USING ivfflat (image_embedding vector_cosine_ops) WITH (lists = 100);

-- 6. REVALIDATION REQUESTS (Community voting)
CREATE TABLE IF NOT EXISTS revalidation_requests (
    request_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    hazard_id UUID REFERENCES hazard_clusters(hazard_id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL,
    response VARCHAR(10), -- 'YES', 'NO', 'SKIP'
    new_image_url TEXT,
    responded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. ⚠️ LEGACY SUPPORT: REPORTS ANALYSIS (Primary Entry Table)
CREATE TABLE IF NOT EXISTS reports_analysis (
    report_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(64) NOT NULL,
    incident_type VARCHAR(50),
    description TEXT,
    severity INT DEFAULT 1,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    image_url TEXT,
    status VARCHAR(20) DEFAULT 'Pending',
    verification_confidence DECIMAL(5,2),
    ai_reasoning TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reports_analysis_status ON reports_analysis(status);

-- 8. HELPER FUNCTIONS & TRIGGERS
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_hazard_clusters_updated_at BEFORE UPDATE ON hazard_clusters
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_trust_updated_at BEFORE UPDATE ON user_trust_scores
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- 9. ANALYTICS VIEWS
CREATE OR REPLACE VIEW active_hazards AS
SELECT h.hazard_id, h.hazard_type, h.latitude, h.longitude, h.status
FROM hazard_clusters h
WHERE h.status IN ('CONFIRMED', 'PENDING');

-- 10. SAMPLE DATA
INSERT INTO user_trust_scores (user_id, total_reports, accepted_reports, trust_score, reward_points, badge_level)
VALUES ('admin_test_user', 10, 10, 100.0, 100, 'GOLD')
ON CONFLICT (user_id) DO NOTHING;
