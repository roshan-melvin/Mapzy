# OSM World Map - Speed Cameras & Hazards

A pure frontend web application that uses **Leaflet.js** and the **Overpass API** to display a world map with dynamic overlays for:
- Speed Cameras
- Traffic Signals
- Road Hazards

## How to Run

### Method 1: The Simple Way (Double-Click)
Since this is a client-side application with no build steps, you can simply open the `index.html` file in your preferred web browser.

1.  Navigate to the project folder: `/home/rocroshan/Desktop/2026/MAPS`
2.  Double-click `index.html`.

### Method 2: Local Server (Recommended)
For a better experience (and to avoid potential strict browser security warnings), run a lightweight local server.

**Using Python:**
```bash
python3 -m http.server 8000
```
Then open [http://localhost:8000](http://localhost:8000) in your browser.

**Using Node.js:**
```bash
npx serve .
```

## How to Use
1.  **Zoom In**: The application is optimized for performance and will only load specific data when you zoom in to **Level 12** or higher.
2.  **Explore**: Pan around the map. Data for the new area will be fetched automatically.
3.  **Toggle Layers**: Use the control panel in the top-right corner to show/hide Speed Cameras, Traffic Signals, or Hazards.
4.  **View Details**: Click on any colored circle to see more details about that point.

## Features
- **Smart Data Loading**: Only fetches data for the visible viewport to save bandwidth and API limits.
- **Caching**: Previously loaded areas are cached so they don't reload when you pan back.
- **Overpass API**: Uses real-time data from OpenStreetMap.
