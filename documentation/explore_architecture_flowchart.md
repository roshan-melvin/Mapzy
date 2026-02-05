# Explore Architecture Flowchart (Mappls Integration)

This flowchart details the architecture and logic of the "Explore" tab, focusing on the Map implementation and Search functionality.

## 1. Map Initialization & Layering
```mermaid
graph TD
    ExploreFrag[Explore Fragment] --> MapView[Mappls MapView]
    
    subgraph "Mappls SDK Layers"
        MapView --> BaseMap[Base Map Tiles]
        MapView --> TrafficLayer[Traffic Overlay]
        MapView --> Markers[Marker Annotations]
        MapView --> Polyline[Navigation Path]
    end
    
    subgraph "Custom UI Overlays"
        ExploreFrag --> SearchBar[Search Bar / BottomSheet]
        ExploreFrag --> RegionChip[Region/Location Chip]
        ExploreFrag --> NavControls[Navigation Controls]
    end
```

## 2. Search & Nearby API Flow
```mermaid
sequenceDiagram
    participant User
    participant SearchUI as Search Sheet
    participant App as ExploreFragment
    participant Mappls as Mappls SDK / API
    
    Note over User, App: 1. User Initiates Search
    User->>SearchUI: Types Query (e.g. "Hospital")
    SearchUI->>App: Callback onTextChanged()
    
    Note over App, Mappls: 2. Auto-Suggest (Optional)
    App->>Mappls: MapplsAutoSuggest.builder().query("Hos")...
    Mappls-->>App: Returns Suggestions List
    App->>SearchUI: Updates Recycler View with Suggestions
    
    Note over User, App: 3. Nearby Search (Action)
    User->>SearchUI: Clicks "Search" or Suggestion
    App->>Mappls: MapplsNearby.builder().keyword("Hospital").setLocation(lat, lng)...
    Mappls-->>App: Returns List<NearbyPlace> (eLoc, Name, LatLng)
    
    Note over App, User: 4. Rendering Results
    loop For Each Place
        App->>App: Create Marker Option
        App->>Mappls: Plugin.addMarker(MarkerOption)
    end
    App->>User: Moves Camera to Bounds of Results
```
