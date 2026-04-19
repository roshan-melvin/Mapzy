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

## 6. Database Tables: When They Are Used

This section maps each table to the stage in which it is read or written.

### A. Core Report Submission and Verification

1. **reports_analysis**
    - **Stage: Report submission** (insert new report)
    - **Stage: Verification** (update verdict, confidence, reasoning, status)

2. **user_trust_scores**
    - **Stage 8: Compute final confidence** (read trust score)
    - **Stage 14: Update user trust** (write updated trust after verdict)

### B. Post-Acceptance Clustering (Only After ACCEPTED)

3. **hazard_clusters**
    - **Stage: Assign to cluster / create cluster**
    - **Stage: Confidence lifecycle updates** (confirm, revalidate, expire)

4. **hazard_embeddings**
    - **Stage: Embedding similarity check**
    - **Stage: Link report embedding to hazard cluster**

5. **community_reports**
    - **Stage: Link report to hazard cluster**
    - **Stage: Read in hazard details API**

### C. Revalidation Flow

6. **revalidation_requests**
    - **Stage: Revalidation response submission** (insert new response)

### D. PostGIS System Tables (Auto-Managed)

7. **geography_columns**, **geometry_columns**, **spatial_ref_sys**
    - **System tables** managed by PostGIS; not directly written by app code.

### E. Currently Unused by Code

8. **active_hazards**
    - **No references found in backend code** (reserved or future use).

## 7. Full Flowchart: Report Submission to Public Display

```mermaid
flowchart TD
    %% 1. Submission Phase
    subgraph Submission ["1. Submission"]
        User([Mobile User]) --> SubmitReport["Submit Report + Image"]
        SubmitReport --> App[Mobile App]
        App --> UploadCloudinary["Upload to Cloudinary"]
        UploadCloudinary --> Cloudinary[(Cloudinary)]
        App --> InsertReport["Insert to reports_analysis"]
        InsertReport --> Reports[(reports_analysis)]
    end

    %% 2. Verification Phase
    subgraph Verification ["2. Verification Engine"]
        Reports --> FetchReport["Fetch Report"]
        FetchReport --> Verify[Verification Service]
        Verify --> DownloadImage["Download Image"]
        DownloadImage --> TempImage[Temp Image File]
        Verify --> GetHashes["Get Existing Hashes"]
        GetHashes --> Hashes[Duplicate Hash Check]

        Verify --> Stage1["Stage 1: Prefilter"]
        Stage1 --> Prefilter[Prefilter Checks]
        Prefilter -->|Fail| Reject([REJECTED])

        Prefilter -->|Pass| Stage2["Stage 2: AI Check"]
        Stage2 --> FakeDetect[AI-Generated Detector]
        FakeDetect -->|Fail| Reject

        FakeDetect -->|Pass| Analysis[Analysis Logic]
        Analysis --> YOLO[YOLO Object Detector]
        Analysis --> Gemini[Gemini VLM Reasoning]
        Analysis --> GetTrust["Get User Trust"]
        GetTrust --> Trust[(user_trust_scores)]
    end

    %% 3. Decision Phase
    subgraph Decision ["3. Decision Logic"]
        YOLO & Gemini & Trust --> FinalVerdict{Verdict}
        FinalVerdict -->|ACCEPTED| Accepted([ACCEPTED])
        FinalVerdict -->|REJECTED| Reject
        FinalVerdict -->|UNCERTAIN| Review([PENDING REVIEW])

        FinalVerdict --> UpdateReports["Update reports_analysis"]
        UpdateReports --> Reports
        FinalVerdict --> UpdateTrust["Update user_trust_scores"]
        UpdateTrust --> Trust
    end

    %% 4. Post-Acceptance Phase
    subgraph PostAcceptance ["4. Post-Acceptance"]
        Accepted --> GenEmbeddings["Generate Embeddings"]
        GenEmbeddings --> Embeddings[(hazard_embeddings)]
        GenEmbeddings --> AssignCluster["Assign Cluster"]
        AssignCluster --> Cluster[Geo Clustering]
        Cluster --> FindClusters["Find or Create Clusters"]
        FindClusters --> Clusters[(hazard_clusters)]
        Cluster --> LinkReport["Link Report"]
        LinkReport --> Community[(community_reports)]

        Clusters --> ConfidenceService[Confidence Service]
        ConfidenceService -->|Lifecycle Update| Clusters
    end

    %% 5. Display & Revalidation
    subgraph Display ["5. Display and Revalidation"]
        Clusters --> GetHazards["GET Hazards API"]
        Community --> GetHazards
        GetHazards --> Map[Community Map]

        Clusters --> NeedsReval["Needs Revalidation"]
        NeedsReval --> RevalAPI["GET Revalidation API"]
        RevalAPI --> RevalReq[(revalidation_requests)]
    end

    %% Cleanup
    Verify -.->|Background| Cleanup[Delete Temp Image]
