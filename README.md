<<<<<<< HEAD
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
=======
<<<<<<< HEAD
# DeepBlueS11 - Interactive Mapping Application

A web-based mapping application for India featuring place search, turn-by-turn navigation, and real-time hazard detection.

## Features

- 🗺️ **Interactive Map** - Powered by Mappls SDK with zoom, 3D view, and fullscreen controls
- 🔍 **Smart Search** - Real-time autocomplete for places, addresses, and landmarks
- 📍 **Current Location** - GPS-based location tracking
- 🧭 **Navigation** - Turn-by-turn directions with distance and duration
- ⚠️ **Hazard Detection** - Real-time display of speed bumps and traffic calming measures from OpenStreetMap

## Setup

1. **Get Mappls API Token**
   - Sign up at [Mappls Console](https://auth.mappls.com/console/)
   - Generate an access token

2. **Configure Application**
   ```bash
   cp config.example.js config.js
   ```
   - Edit `config.js` and add your access token

3. **Run Application**
   - Open `index.html` in a web browser
   - Allow location permissions when prompted

## Usage

- **Search**: Type in the search bar to find places
- **Navigate**: Use the navigation panel to get directions
- **Find Location**: Click the location button to center map on your position
- **View Hazards**: Click the warning button to toggle hazard markers

## Technologies

- Mappls Map SDK v3.0
- OpenStreetMap Overpass API
- Browser Geolocation API

## License

MIT
=======

static code = "agtofjqdydevmnjedjlpintqdxdzbfdiktnb"
>>>>>>> d713560efdfb589373155d91ed286e1ad6920213
>>>>>>> main
