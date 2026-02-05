# AI/ML Contribution Verification System

This document outlines the architecture for verifying user reports (potholes, accidents, etc.) using Generative AI (specifically Vision Language Models) and awarding reliability points.

## 1. The Verification Loop Architecture

Moving the heavy lifting (AI processing) to a backend connected to PostgreSQL is the correct approach. It isolates the expensive operations from the user's mobile app.

```mermaid
flowchart TD
    subgraph Mobile App
        User[User] -->|Submits Report| Firestore[(Firestore DB)]
        Firestore -.->|Real-time Listener| User
    end

    subgraph Backend Services
        Firestore -->|1. Sync Trigger| SyncService[Sync Worker]
        SyncService -->|2. Insert| Postgres[(PostgreSQL DB)]
        
        subgraph "AI Verification Agent"
            Postgres -->|3. Poll 'Pending'| AI_Worker[Python AI Worker]
            AI_Worker -->|4. Analyze Image/Text| LLM[Gemini/Vision Model]
            LLM -->|5. Verdict (True/False)| AI_Worker
        end
        
        AI_Worker -->|6. Update Status & Points| Postgres
    end

    subgraph Feedback Loop
        Postgres -->|7. Sync Back| SyncService
        SyncService -->|8. Update| Firestore
        Firestore -->|9. Notify| Notification[Push Notification]
    end

    style AI_Worker fill:#f9f,stroke:#333,stroke-width:2px
    style LLM fill:#bbf,stroke:#333
    style Postgres fill:#4CAF50,stroke:#333,color:white
```

## 2. Technical Stack (Recommended)

1.  **Database**: PostgreSQL with `pgvector` (optional, if you want to search similar images later).
2.  **AI Worker**: Python script running on a server (AWS Lambda, Google Cloud Run, or a generic VPS).
3.  **Model**:
    *   **Vision Inputs**: Use models like **Gemini Pro Vision** or **GPT-4o**.
    *   **Task**: "Analyze this image. Is it a road hazard? Match it with the description: '{user_description}'. Return confidence score (0-100)."

## 3. Database Schema Extensions

We need to add fields to the PostgreSQL schema to support this AI auditing.

```sql
ALTER TABLE reports_analysis ADD COLUMN verification_confidence DECIMAL(5,2);
ALTER TABLE reports_analysis ADD COLUMN ai_reasoning TEXT;
ALTER TABLE reports_analysis ADD COLUMN manual_review_needed BOOLEAN DEFAULT FALSE;

CREATE TABLE user_trust_scores (
    user_id VARCHAR(64) PRIMARY KEY,
    total_points INT DEFAULT 0,
    reliability_score DECIMAL(3,2) DEFAULT 0.5, -- 0.0 to 1.0
    reports_verified INT DEFAULT 0,
    reports_rejected INT DEFAULT 0
);
```

## 4. Verification Logic (Python Pseudo-code)

```python
def verify_queue():
    pending_reports = db.fetch("SELECT * FROM reports WHERE status = 'Pending'")
    
    for report in pending_reports:
        # 1. AI Analysis
        verdict = ai_model.analyze(image=report.image_url, text=report.description)
        
        if verdict.is_valid_hazard and verdict.confidence > 0.85:
            new_status = 'Verified'
            points = calculate_points(report.type, report.severity)
            
            # Award Points to User
            db.execute("UPDATE user_trust_scores SET total_points = total_points + %s WHERE user_id = %s", (points, report.user_id))
            
        elif verdict.confidence < 0.2:
            new_status = 'Rejected'
            points = -5 # Penalty for spam
            
        else:
            new_status = 'Flagged' # Needs human review
        
        # 2. Update Record
        db.execute("UPDATE reports SET status = %s, ai_reasoning = %s WHERE id = %s", (new_status, verdict.reasoning, report.id))
```

## 5. Point System Strategy

| Incident Type | Integrity Check | Base Points | Bonus (High Quality Media) |
| :--- | :--- | :--- | :--- |
| **Pothole** | Visual Confirmation | +10 | +5 |
| **Accident** | Visual + Traffic Data Cross-ref | +50 | +10 |
| **Waterlogging** | Visual Confirmation | +20 | +5 |
| **Spam/False** | negative check | -20 | N/A |
