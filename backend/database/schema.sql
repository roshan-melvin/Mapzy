-- Run this query in your PostgreSQL Query Editor (e.g. Supabase SQL Editor)

CREATE TABLE IF NOT EXISTS reports_analysis (
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
    
    -- AI Verification Fields
    verification_confidence DECIMAL(5,2),
    ai_reasoning TEXT,
    manual_review_needed BOOLEAN DEFAULT FALSE
);

-- Optional: Create an index on status for faster polling by the AI worker
CREATE INDEX idx_reports_status ON reports_analysis(status);
