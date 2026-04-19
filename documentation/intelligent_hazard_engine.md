# Intelligent Hazard Clustering & Map Sync Architecture

This document outlines the architecture, data flow, and lifecycle of a hazard report within the DeepBlueS11 platform. The engine is designed to intelligently handle duplicate reports, protect against farming abuse, continuously update the map with decaying hazard confidences, and optimally cache map requests for high scalability.

## Architecture & Data Flow

```mermaid
graph TD
    %% Submission Flow
    subgraph Submission_Process [User Submission]
        A[Mobile User] -->|1. Submit Report| B(Reports API endpoint)
    end

    B -->|2. AI Verification| C{Verification Service}
    
    C -->|Fails AI Check| D[Rejected]
    C -->|Passes Pre-filter & AI| E[Extract Image Embeddings]
    
    E --> F{Geo-Clustering Engine}
    
    F -->|Step 1: Check 50m Radius| F1{Exists?}
    
    F1 -->|No| G[✅ Create New Cluster]
    F1 -->|Yes| F2{Step 2: Same User Check}
    
    F2 -->|Yes| H[🚫 Reject: Duplicate Abuse]
    H --> H_Penalty[Trust Penalty Applied]
    
    F2 -->|No| F3{Step 3: Image Similarity > 95%?}
    F3 -->|Yes| I[🚫 Reject: Exact Image Abuse]
    I --> I_Penalty[Severe Trust Penalty Applied]
    
    F3 -->|No| F4{Step 4: Distance < 15m?}
    F4 -->|Yes| J[✅ Merge to Existing Cluster]
    F4 -->|No| K[✅ Create New Cluster]
    
    G --> L[Update Cluster Confidence]
    J --> L
    K --> L
    
    L --> M{Confidence = 100%?}
    M -->|Yes| N[Sync to Firebase map_hazards]
    M -->|No| O[Keep internal / Pending]

    %% Map Consumption Flow
    subgraph Scalable_Map_Consumption [Map Consumption]
        P[Mobile App Map View] -->|Debounced GET Hazards| Q(FastAPI GET /hazards)
        Q -->|1st Request| R[(Supabase PostGIS)]
        R --> S[Cache Response in Memory 60s]
        S --> Q
        Q -->|Next 60s same 111m area| S
        
        P -.->|WebSocket Listener| N
        N -.->|Live Decay/Updates| P
    end

    %% Decay Loop
    subgraph Weekly_Decay_Loop [Weekly Decay & Revalidation]
        T[Weekly Cron / Confidence Check] -->|7 days passed| U[-25 Confidence]
        U --> V{Status Update}
        V --> W[NEEDS_REVALIDATION]
        W -.->|Sync State| N
        
        W --> X[ Nearby user says YES ]
        X --> Y[+25 Confidence]
        Y --> Z{Reaches 100%?}
        Z -->|Yes| AA[CONFIRMED & Timer Resets]
        Z -->|No| W
        
        W --> AB[ Nearby user says NO ]
        AB --> AC[-30 Confidence]
        AC --> AD{Hits 0%?}
        AD -->|Yes| AE[EXPIRED & Delete from Firebase]
    end
```

## Core Components

### 1. Geo-Clustering Engine (`geo_clustering.py`)
Responsible for grouping similar reports based on strict spatial rules (50m search radius, 15m merge rule) to prevent map clutter.

### 2. Anti-Farming & Trust Logic (`anti_farming.py` & `trust.py`)
Prevents users from intentionally farming reward points by analyzing velocity (e.g. 4 reports within 50m in 10 minutes) and rejection rates. Applies dynamic trust penalties, and users with a score below 20.0 are shadow-banned, stripping their community voting rights.

### 3. Confidence Decay Loop (`confidence.py`)
Clusters start at `PENDING`. When they reach 100% confidence via multiple accepted reports, they become `CONFIRMED`.
If a `CONFIRMED` hazard exists unchallenged for a full week (7 days), its confidence automatically decays by 25 points, putting it in a `NEEDS_REVALIDATION` state to solicit user consensus. 

### 4. Hybrid Map Architecture
The map rendering relies on two optimized systems working in tandem:
1. **Supabase (PostGIS) + TTLCache**: The app makes a `GET /hazards` request to pull markers using precise Radius filters. The backend rounds coordinates to a 111-meter precision box and caches the database result in memory for 60 seconds. This blocks database spam.
2. **Firebase live syncing (`firebase_sync.py`)**: Real-time Firebase listeners wait for the `map_hazards` collection documents to change (e.g., decaying confidence color changes), updating active app screens immediately without a full refresh. Expired hazards are strictly deleted from this collection.
