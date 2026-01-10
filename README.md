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
