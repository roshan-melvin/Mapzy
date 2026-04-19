# Explore Architecture Flowchart (Mappls Integration)

This flowchart details the architecture and logic of the "Explore" tab, focusing on the Map implementation, Search functionality, and the Intelligent Hazard Engine overlays.

## 1. Map Initialization & Layering
```mermaid
graph TD
    ExploreFrag[Explore Fragment] --> MapView[Mappls MapView]
    
    subgraph "Mappls SDK Layers"
        MapView --> BaseMap[Base Map Tiles]
        MapView --> TrafficLayer[Traffic Overlay]
        MapView --> Polyline[Navigation Path]
    end
    
    subgraph "Intelligent Hazard Overlays"
        MapView --> HazardMarkers[OSM & Community Markers]
        HazardMarkers --> FirebaseListener[Live Map State Sync]
        FirebaseListener --> VisualAlert[Visual Drop-up Panel]
    end
    
    subgraph "Custom UI Overlays"
        ExploreFrag --> SearchBar[Search Bar / BottomSheet]
        ExploreFrag --> RegionChip[Region/Location Chip]
        ExploreFrag --> NavControls[Navigation Controls]
    end
```

## 2. Dynamic Component Updating
```mermaid
sequenceDiagram
    participant User
    participant MapUI as Mappls Canvas
    participant AlertPanel as Hazard Alert Panel
    participant Firebase as Firebase Firestore
    participant Engine as Intelligent Engine
    
    Note over User, MapUI: 1. Hazard Rendered
    Engine->>Firebase: New hazard validated (Confidence 100)
    Firebase-->>MapUI: Real-time Snapshot fires
    MapUI->>MapUI: Removes old cache marker, re-renders new

    Note over User, AlertPanel: 2. Hazard Proximity
    User->>MapUI: Moves within 300m of Hazard
    MapUI->>AlertPanel: Injects hazard into Active List
    AlertPanel->>User: Drops up visual alert (Elevated layer) & TTS Voice
    
    Note over User, AlertPanel: 3. Hazard CROSSED
    User->>MapUI: Hazard safely crossed
    AlertPanel->>AlertPanel: Distance falls to 0m
    AlertPanel->>User: Removes from active list
```
