# Profile Page Architecture

This diagram visualizes the data flow, UI components, and logic handling for the **Profile ("You") Fragment**.

```mermaid
graph TB
    %% Entry Point
    Fragment[ProfileFragment]
    
    subgraph UI_Components
        Header[Profile Card]
        Stats[Stats Row]
        Menu[Settings Menu]
        SignOut[Sign Out Button]
    end

    subgraph Data_Sources
        Auth[Firebase Auth]
        Store[Firebase Firestore]
    end

    subgraph Controller
        Load[loadUserProfile]
        Setup[setupButtons]
        AuthRedirect[Auth Redirect]
    end

    %% Wiring
    Fragment --> Load
    Fragment --> Setup

    %% Load Flow
    Load --> Auth
    Auth --> Header
    Load --> Store
    Store --> DataBinder{Data Binding}
    DataBinder --> Header
    DataBinder --> Stats

    %% Action Flow
    Setup --> SignOut
    SignOut --> AuthRedirect
    AuthRedirect --> LoginScreen[Auth Activity]
    
    Setup --> Menu
    Menu --> ComingSoon[Placeholder Feed]

    %% Styling
    style Fragment fill:#5865F2,stroke:#fff,stroke-width:2px,color:#fff
    style Auth fill:#FFA000,stroke:#333,stroke-width:1px
    style Store fill:#4CAF50,stroke:#333,stroke-width:1px
    style SignOut fill:#ED4245,stroke:#333,stroke-width:1px,color:#fff
```

### **Architectural Breakdown**

1.  **View Layer (XML)**: Uses `CardView` for elevation and grouping. The primary themes are dark (#202225) with premium blue/red accents for actions.
2.  **Data Persistence**: 
    *   `Firebase Auth` manages the session local state.
    *   `Firestore` serves as the single source of truth for gamified stats (Points/Reports).
3.  **Controller Logic**: 
    *   **Atomic Loading**: Fetches Auth and Stats simultaneously on entry.
    *   **Secure Logouts**: Uses `Intent.FLAG_ACTIVITY_CLEAR_TASK` to prevent users from seeing cached data after logout.
