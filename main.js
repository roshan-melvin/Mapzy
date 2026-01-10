// OSM Live Map - Speed Cameras, Traffic Signals & Hazards
// Uses Leaflet + Overpass API + osmtogeojson

let map;
let camerasLayer, signalsLayer, hazardsLayer;
let isLoading = false;

// Initialize map
function initMap() {
    map = L.map('map').setView([28.6139, 77.2090], 12); // Default: New Delhi

    // OpenStreetMap base tiles
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);

    // Initialize feature layers
    camerasLayer = L.layerGroup().addTo(map);
    signalsLayer = L.layerGroup().addTo(map);
    hazardsLayer = L.layerGroup().addTo(map);

    // Load data on map move/zoom
    map.on('moveend', loadData);
    
    // Initial load
    loadData();

    // Setup toggle controls
    setupToggles();
}

// Setup layer toggles
function setupToggles() {
    document.getElementById('toggle-cameras').addEventListener('change', function(e) {
        if (e.target.checked) {
            map.addLayer(camerasLayer);
        } else {
            map.removeLayer(camerasLayer);
        }
    });

    document.getElementById('toggle-signals').addEventListener('change', function(e) {
        if (e.target.checked) {
            map.addLayer(signalsLayer);
        } else {
            map.removeLayer(signalsLayer);
        }
    });

    document.getElementById('toggle-hazards').addEventListener('change', function(e) {
        if (e.target.checked) {
            map.addLayer(hazardsLayer);
            if (map.getZoom() >= 12) {
                loadHazards();
            }
        } else {
            map.removeLayer(hazardsLayer);
        }
    });
}

// Main data loading function
function loadData() {
    const zoom = map.getZoom();
    
    if (zoom < 12) {
        updateStatus('Zoom in to load data (Level 12+)');
        clearLayers();
        return;
    }

    if (isLoading) return;
    
    updateStatus('Loading...');
    isLoading = true;

    // Load all enabled layers
    if (document.getElementById('toggle-cameras').checked) {
        loadSpeedCameras();
    }
    if (document.getElementById('toggle-signals').checked) {
        loadTrafficSignals();
    }
    if (document.getElementById('toggle-hazards').checked) {
        loadHazards();
    }
}

// Clear all layers
function clearLayers() {
    camerasLayer.clearLayers();
    signalsLayer.clearLayers();
    hazardsLayer.clearLayers();
}

// Update status text
function updateStatus(text) {
    document.getElementById('status-text').textContent = text;
}

// Get current map bounding box for Overpass query
function getBBox() {
    const bounds = map.getBounds();
    const south = bounds.getSouth();
    const west = bounds.getWest();
    const north = bounds.getNorth();
    const east = bounds.getEast();
    return `${south},${west},${north},${east}`;
}

// Load Speed Cameras from OSM
function loadSpeedCameras() {
    const bbox = getBBox();
    const query = `
        [out:json][timeout:25];
        (
            node["highway"="speed_camera"](${bbox});
        );
        out body;
        >;
        out skel qt;
    `;

    fetchOverpassData(query, (geojson) => {
        camerasLayer.clearLayers();
        
        L.geoJSON(geojson, {
            pointToLayer: function(feature, latlng) {
                return L.circleMarker(latlng, {
                    radius: 6,
                    fillColor: '#FF0000',
                    color: '#FFFFFF',
                    weight: 2,
                    opacity: 1,
                    fillOpacity: 0.8
                });
            },
            onEachFeature: function(feature, layer) {
                let popup = '<strong>Speed Camera</strong><br>';
                if (feature.properties.maxspeed) {
                    popup += `Max Speed: ${feature.properties.maxspeed}`;
                }
                layer.bindPopup(popup);
            }
        }).addTo(camerasLayer);
        
        checkLoadingComplete();
    });
}

// Load Traffic Signals from OSM
function loadTrafficSignals() {
    const bbox = getBBox();
    const query = `
        [out:json][timeout:25];
        (
            node["highway"="traffic_signals"](${bbox});
        );
        out body;
        >;
        out skel qt;
    `;

    fetchOverpassData(query, (geojson) => {
        signalsLayer.clearLayers();
        
        L.geoJSON(geojson, {
            pointToLayer: function(feature, latlng) {
                return L.circleMarker(latlng, {
                    radius: 5,
                    fillColor: '#00FF00',
                    color: '#FFFFFF',
                    weight: 2,
                    opacity: 1,
                    fillOpacity: 0.8
                });
            },
            onEachFeature: function(feature, layer) {
                let popup = '<strong>Traffic Signal</strong><br>';
                if (feature.properties.name) {
                    popup += feature.properties.name;
                }
                layer.bindPopup(popup);
            }
        }).addTo(signalsLayer);
        
        checkLoadingComplete();
    });
}

// Load Hazards from OSM (YELLOW POINTS)
function loadHazards() {
    const bbox = getBBox();
    const query = `
        [out:json][timeout:25];
        (
            node["hazard"](${bbox});
            node["traffic_calming"](${bbox});
            node["danger"](${bbox});
            node["barrier"="debris"](${bbox});
            node["barrier"="block"](${bbox});
            way["hazard"](${bbox});
            way["traffic_calming"](${bbox});
            way["danger"](${bbox});
        );
        out body;
        >;
        out skel qt;
    `;

    fetchOverpassData(query, (geojson) => {
        hazardsLayer.clearLayers();
        
        L.geoJSON(geojson, {
            pointToLayer: function(feature, latlng) {
                return L.circleMarker(latlng, {
                    radius: 6,
                    fillColor: '#FFFF00',  // YELLOW
                    color: '#000000',
                    weight: 2,
                    opacity: 1,
                    fillOpacity: 0.9
                });
            },
            onEachFeature: function(feature, layer) {
                let popup = '<strong>⚠️ Hazard</strong><br>';
                
                // Display relevant hazard tags
                if (feature.properties.hazard) {
                    popup += `Type: ${feature.properties.hazard}<br>`;
                }
                if (feature.properties.traffic_calming) {
                    popup += `Traffic Calming: ${feature.properties.traffic_calming}<br>`;
                }
                if (feature.properties.danger) {
                    popup += `Danger: ${feature.properties.danger}<br>`;
                }
                if (feature.properties.barrier) {
                    popup += `Barrier: ${feature.properties.barrier}<br>`;
                }
                if (feature.properties.name) {
                    popup += `Name: ${feature.properties.name}<br>`;
                }
                if (feature.properties.description) {
                    popup += `${feature.properties.description}`;
                }
                
                layer.bindPopup(popup);
            }
        }).addTo(hazardsLayer);
        
        checkLoadingComplete();
    });
}

// Fetch data from Overpass API and convert to GeoJSON
function fetchOverpassData(query, callback) {
    const overpassUrl = 'https://overpass-api.de/api/interpreter';
    
    fetch(overpassUrl, {
        method: 'POST',
        body: query
    })
    .then(response => response.json())
    .then(osmData => {
        // Convert OSM to GeoJSON using osmtogeojson
        const geojson = osmtogeojson(osmData);
        callback(geojson);
    })
    .catch(error => {
        console.error('Overpass API error:', error);
        updateStatus('Error loading data');
        isLoading = false;
    });
}

// Check if all data finished loading
let loadCounter = 0;
function checkLoadingComplete() {
    loadCounter++;
    if (loadCounter >= 3) { // All three layers loaded
        updateStatus('Data loaded');
        isLoading = false;
        loadCounter = 0;
    }
}

// Initialize when page loads
document.addEventListener('DOMContentLoaded', initMap);
