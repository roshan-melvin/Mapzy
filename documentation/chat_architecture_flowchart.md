# ZWAP Chat Architecture

## 1. Chat "Working" Flowchart (Logic)
This sequence diagram shows how the app initializes, joins regions, and syncs messages in real-time.

```mermaid
sequenceDiagram
    participant User
    participant App
    participant Firestore

    Note over User, App: 1. Initialization
    User->>App: Opens "Chat" Tab
    App->>Firestore: GET /users/{userId}
    Firestore-->>App: Returns User Profile (including `chat_regions` list)
    
    alt User has NO Regions
        App->>User: Shows "Join Region" Screen
        User->>App: Selects Region (e.g. "Chennai")
        App->>Firestore: UPDATE /users/{userId} (Append "india-chennai")
        App->>App: Reloads UI with new Region
    else User HAS Regions
        App->>User: Shows Sidebar with Region Icons
    end

    Note over User, App: 2. Channel Selection
    User->>App: Clicks Region Icon
    App->>User: Shows Static Threads (Welcome, General, Hazard...)
    User->>App: Clicks "#General" Thread

    Note over User, App: 3. Message Sync
    App->>Firestore: LISTEN /chat/{regionId}/threads/{threadId}/messages
    Firestore-->>App: Real-time Snapshot (Existing Messages)
    App->>User: Displays Chat History
    
    Note over User, App: 4. Sending Text
    User->>App: Types "Hello" & Clicks Send
    App->>Firestore: ADD Document to .../messages
    Firestore-->>App: Real-time Update (New Message Appears)

    Note over User, App: 5. Sending Media (Image/Video)
    User->>App: Clicks "+" -> Selects File (Image/Video)
    App->>App: 1. Adds "Temporary" Local Message (Uploading Spinner)
    App->>Cloudinary: 2. Uploads File (Direct Unsigned)
    Cloudinary-->>App: 3. Returns Secure URL (e4d...mp4)
    App->>App: Detects Type (Image/Video) from URL
    App->>Firestore: 4. ADD Document (text="", type="video", image_url=URL)
    Firestore-->>App: Real-time Update (Replaces Temp Message)
```

## 2. Storage Flowchart (Data Hierarchy)
This diagram shows exactly how data is nested and stored in Firestore.

```mermaid
graph TD
    classDef col fill:#f9f,stroke:#333;
    classDef doc fill:#ccf,stroke:#333;
    classDef field fill:#eee,stroke:#333;

    Root[Firestore Root] --> Users(Users Collection):::col
    Root --> Chat(Chat Collection):::col

    %% Users Branch
    Users --> UserDoc(User Document {uid}):::doc
    UserDoc --> UFields["Fields:
    - username
    - email
    - chat_regions [array]"]:::field

    %% Chat Branch (Renamed from Regions)
    Chat --> RegionDoc(Region Document {regionId}):::doc
    RegionDoc --> Threads(Threads Subcollection):::col
    Threads --> ThreadDoc(Thread Document {threadId}):::doc
    ThreadDoc --> Messages(Messages Subcollection):::col
    Messages --> MsgDoc(Message Document {msgId}):::doc
    MsgDoc --> MFields["Fields:
    - text
    - type ('text'/'image')
    - user_id
    - username
    - created_at"]:::field

    subgraph "Legacy (Unused)"
    Channels[Channels Coll]
    GlobalMsgs[Messages Coll]
    end
```
