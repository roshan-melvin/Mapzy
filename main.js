// --- Configuration ---
const OVERPASS_API_URL = "https://overpass-api.de/api/interpreter";
const MIN_ZOOM_LEVEL = 12;

// Layer Configuration
const LAYERS_CONFIG = {
    cameras: {
        id: "cameras",
        query: `
            node["highway"="speed_camera"](BBOX);
            way["highway"="speed_camera"](BBOX);
            relation["highway"="speed_camera"](BBOX);
        `,
        style: {
            radius: 8,
            fillColor: "#ef4444",
            color: "#fff",
            weight: 2,
            opacity: 1,
            fillOpacity: 0.8
        },
        popup: (props) => `<strong>Speed Camera</strong><br>Max Speed: ${props.maxspeed || 'Unknown'}`
    },
    signals: {
        id: "signals",
        query: `
            node["highway"="traffic_signals"](BBOX);
            way["highway"="traffic_signals"](BBOX);
            relation["highway"="traffic_signals"](BBOX);
        `,
        style: {
            radius: 6,
            fillColor: "#10b981",
            color: "#fff",
            weight: 1,
            opacity: 1,
            fillOpacity: 0.8
        },
        popup: (props) => `<strong>Traffic Signal</strong>`
    },
    hazards: {
        id: "hazards",
        query: `
            node["hazard"](BBOX);
            way["hazard"](BBOX);
            relation["hazard"](BBOX);
            node["traffic_calming"](BBOX);
            way["traffic_calming"](BBOX);
        `,
        style: {
            radius: 7,
            fillColor: "#f59e0b",
            color: "#fff",
            weight: 2,
            opacity: 1,
            fillOpacity: 0.8
        },
        popup: (props) => `<strong>Hazard/Calming</strong><br>Type: ${props.hazard || props.traffic_calming || 'Unknown'}`
    }
};

// --- State ---
const state = {
    processedIds: new Set(), // Track added features to avoid duplicates
    activeRequests: 0,
    loadedAreas: [] // Array of LatLngBounds corresponding to successfully fetched areas
};

// --- Initialization ---

// 1. Initialize Leaflet Map
const map = L.map('map').setView([20, 0], 2); // Start with world view

// 2. Add OSM Tile Layer
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
}).addTo(map);

// 3. Initialize Layer Groups
const layerGroups = {
    cameras: L.layerGroup().addTo(map),
    signals: L.layerGroup().addTo(map),
    hazards: L.layerGroup().addTo(map)
};

// --- DOM Elements ---
const statusText = document.getElementById('status-text');
const toggles = {
    cameras: document.getElementById('toggle-cameras'),
    signals: document.getElementById('toggle-signals'),
    hazards: document.getElementById('toggle-hazards')
};

// --- Event Listeners ---
map.on('moveend', checkZoomAndLoad);

// Layer Toggle Logic
Object.keys(toggles).forEach(key => {
    toggles[key].addEventListener('change', (e) => {
        if (e.target.checked) {
            map.addLayer(layerGroups[key]);
        } else {
            map.removeLayer(layerGroups[key]);
        }
    });
});

// --- Core Logic ---

function checkZoomAndLoad() {
    const zoom = map.getZoom();

    if (zoom < MIN_ZOOM_LEVEL) {
        updateStatus(`Zoom in to level ${MIN_ZOOM_LEVEL} to see data (Current: ${zoom})`);

        // Req 7: Remove or hide layers when zoom < 12
        Object.values(layerGroups).forEach(group => {
            if (map.hasLayer(group)) {
                map.removeLayer(group);
            }
        });
        return;
    }

    // Restore layers if they should be visible (zoom >= 12 and checkbox checked)
    Object.keys(layerGroups).forEach(key => {
        if (toggles[key].checked && !map.hasLayer(layerGroups[key])) {
            map.addLayer(layerGroups[key]);
        }
    });

    const bounds = map.getBounds();

    // Optimization: Check if this area is already loaded
    // We check if the current center or significant portion is covered.
    // Simple check: if current view is contained in ANY loaded area.
    // Note: This is a loose check. For production, we'd use tile-based caching or quadtrees.
    // Here we assume "coverage" if center is covered, but better is to fetch if not *fully* covered.
    // For simplicity / robustness, we will fetch if not fully contained.

    let isFullyLoaded = false;
    for (const area of state.loadedAreas) {
        if (area.contains(bounds)) {
            isFullyLoaded = true;
            break;
        }
    }

    if (isFullyLoaded) {
        updateStatus("Data loaded from cache.");
        return;
    }

    // Not loaded -> Fetch
    fetchDataForBounds(bounds);
}

async function fetchDataForBounds(bounds) {
    if (state.activeRequests > 0) {
        // Debounce/Throttling handled by UI status (and potential abort controller if advanced)
        // For MVP, we allow parallel but warn user.
        updateStatus("Loading data...", true);
    } else {
        updateStatus("Loading data...", true);
    }

    // Format BBox for Overpass: (south, west, north, east)
    const south = bounds.getSouth();
    const west = bounds.getWest();
    const north = bounds.getNorth();
    const east = bounds.getEast();
    const bboxStr = `${south},${west},${north},${east}`;

    // Mark request active
    state.activeRequests++;

    try {
        // Fetch for each enabled layer type 
        // We query ALL types regardless of toggle state, so data is there when they toggle.
        // Or strictly query only what is needed? 
        // User asked for "Load data ONLY when zoom level >= 12".
        // Let's query all 3 categories in one go or separate?
        // Req 4: "Implement separate queries per data type" (Wait, strictly separate requests or queries in one request?)
        // "Implement separate queries per data type" suggests logical separation.
        // Combining into one request is faster/nicer for Overpass server (one connection).
        // BUT user req says "Implement separate queries per data type". 
        // I will do parallel requests for modularity as requested.

        await Promise.all([
            fetchLayerData('cameras', bboxStr),
            fetchLayerData('signals', bboxStr),
            fetchLayerData('hazards', bboxStr)
        ]);

        // Mark area as loaded
        state.loadedAreas.push(bounds);
        updateStatus("Data loaded successfully.");

    } catch (error) {
        console.error("Fetch error:", error);
        updateStatus("Error fetching data. Try panning again.", false, true);
    } finally {
        state.activeRequests--;
        if (state.activeRequests === 0 && !statusText.classList.contains('error')) {
            updateStatus("Data loaded.");
        }
    }
}

async function fetchLayerData(layerKey, bboxStr) {
    const layerConfig = LAYERS_CONFIG[layerKey];

    // Replace BBOX placeholder
    const queryBody = layerConfig.query.replace(/BBOX/g, bboxStr);

    // Construct full Overpass QL
    const overpassQuery = `
        [out:json][timeout:25];
        (
            ${queryBody}
        );
        out body;
        >;
        out skel qt;
    `;

    const body = new URLSearchParams();
    body.append('data', overpassQuery);

    try {
        const response = await fetch(OVERPASS_API_URL, {
            method: 'POST', // Requirement 4: POST requests
            body: body
        });

        if (!response.ok) {
            throw new Error(`Overpass API error: ${response.status}`);
        }

        const osmData = await response.json();

        // Requirement 5: Convert to GeoJSON
        const geoJsonData = osmtogeojson(osmData);

        renderLayer(layerKey, geoJsonData);

    } catch (e) {
        console.warn(`Failed to load ${layerKey}:`, e);
        throw e; // Propagate to main handler
    }
}

function renderLayer(layerKey, geoJson) {
    const config = LAYERS_CONFIG[layerKey];
    const group = layerGroups[layerKey];

    L.geoJSON(geoJson, {
        pointToLayer: function (feature, latlng) {
            return L.circleMarker(latlng, config.style);
        },
        filter: function (feature) {
            // Deduplication
            const id = feature.id; // e.g., "node/123"
            if (state.processedIds.has(id)) {
                return false;
            }
            state.processedIds.add(id);
            return true;
        },
        onEachFeature: function (feature, layer) {
            if (config.popup) {
                layer.bindPopup(config.popup(feature.properties));
            }
        }
    }).addTo(group);
}

// Helper: Status Update
function updateStatus(msg, isLoading = false, isError = false) {
    statusText.textContent = msg;
    statusText.className = 'status'; // Reset
    if (isLoading) statusText.classList.add('loading');
    if (isError) statusText.classList.add('error');
}

// Initial check (in case user refreshes at high zoom)
checkZoomAndLoad();

// --- TEST: Manual Hazard Point ---
// Adds a fake hazard point at the initial map center (Lat: 20, Lon: 0) to verify rendering.
const testHazardGeoJSON = {
    "type": "FeatureCollection",
    "features": [
        {
            "type": "Feature",
            "id": "node/test_hazard_manual",
            "properties": {
                "hazard": "Test Hazard (Manual Add)",
                "description": "This point was manually added to verify layer overlay."
            },
            "geometry": {
                "type": "Point",
                "coordinates": [0, 20] // GeoJSON is [Lon, Lat] -> Maps to Lat 20, Lon 0
            }
        }
    ]
};

// Render the test point into the 'hazards' layer
renderLayer('hazards', testHazardGeoJSON);

console.log("Test hazard added at Lat: 20, Lon: 0");
