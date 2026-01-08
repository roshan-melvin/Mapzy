import 'package:flutter/material.dart';
import 'package:mappls_gl/mappls_gl.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:geolocator/geolocator.dart';
import '../services/location_service.dart';
import '../services/osm_service.dart';
import '../models/camera_model.dart';

class MapScreen extends StatefulWidget {
  const MapScreen({super.key});

  @override
  State<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends State<MapScreen> {
  MapplsMapController? mapController;
  final LocationService _locationService = LocationService();
  final OsmService _osmService = OsmService();
  
  bool _permissionGranted = false;
  double _currentSpeed = 0.0; // km/h
  
  bool _isCameraLocked = true;
  LatLng? _lastFetchLocation;

  @override
  void initState() {
    super.initState();
    _checkPermissionsAndStart();
  }

  Future<void> _checkPermissionsAndStart() async {
    final status = await Permission.locationWhenInUse.request();
    if (status.isGranted) {
      setState(() {
        _permissionGranted = true;
      });
      _startTracking();
    }
  }

  void _startTracking() {
    _locationService.startTracking();
    _locationService.locationStream.listen((Position position) {
      double speedKmh = position.speed * 3.6;
      if (speedKmh < 0) speedKmh = 0;
      
      setState(() {
        _currentSpeed = speedKmh;
      });

      // Manual Camera Follow
      if (_isCameraLocked && mapController != null) {
        mapController!.animateCamera(CameraUpdate.newCameraPosition(CameraPosition(
          target: LatLng(position.latitude, position.longitude),
          zoom: 18.0,
          tilt: 50.0, // 3D driving view
          bearing: position.heading,
        )));
      }

      // Fetch cameras logic
      LatLng currentLatLng = LatLng(position.latitude, position.longitude);
      if (_lastFetchLocation == null || 
          _calculateDistance(_lastFetchLocation!, currentLatLng) > 2000) {
        _fetchAndShowCameras(currentLatLng);
      }
    });
  }

  double _calculateDistance(LatLng a, LatLng b) {
    return Geolocator.distanceBetween(
      a.latitude, a.longitude, b.latitude, b.longitude);
  }

  Future<void> _fetchAndShowCameras(LatLng center) async {
    _lastFetchLocation = center;
    final cameras = await _osmService.fetchSpeedCameras(
      center.latitude, center.longitude, 5000); // 5km radius
    
    if (mapController != null) {
      await mapController!.clearCircles(); 
      
      for (var camera in cameras) {
        await mapController!.addCircle(CircleOptions(
          geometry: LatLng(camera.latitude, camera.longitude),
          circleColor: "#FF0000",
          circleRadius: 10.0,
          circleStrokeWidth: 2.0,
          circleStrokeColor: "#FFFFFF",
          
        ));
      }
    }
  }

  @override
  void dispose() {
    _locationService.stopTracking();
    super.dispose();
  }

  void _onMapCreated(MapplsMapController controller) {
    mapController = controller;
  }

  void _onStyleLoadedCallback() {
    // Style loaded
  }

  @override
  Widget build(BuildContext context) {
    if (!_permissionGranted) {
      return Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.location_off, size: 64, color: Colors.grey),
              const SizedBox(height: 16),
              const Text('Location permission is required'),
              ElevatedButton(
                onPressed: _checkPermissionsAndStart,
                child: const Text('Grant Permission'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      body: Stack(
        children: [
          MapplsMap(
            initialCameraPosition: const CameraPosition(
              target: LatLng(28.6139, 77.2090), 
              zoom: 18.0,
            ),
            onMapCreated: _onMapCreated,
            myLocationEnabled: true,
            onStyleLoadedCallback: _onStyleLoadedCallback,
            // Detect interaction to unlock camera
            // Note: Mappls GL beta might not expose 'onTouch' perfectly directly on widget
            // but we can try generic GestureDetector if needed, though map consumes gestures.
            // For now, assume user presses recenter button to lock.
          ),
          
          // HUD Overlay
          Positioned(
            bottom: 30,
            left: 20,
            right: 20,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.85),
                borderRadius: BorderRadius.circular(24),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.3),
                    blurRadius: 10,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  Column(
                    children: [
                      const Text('SPEED', 
                        style: TextStyle(color: Colors.grey, fontSize: 12, letterSpacing: 1.2)),
                      Row(
                        crossAxisAlignment: CrossAxisAlignment.baseline,
                        textBaseline: TextBaseline.alphabetic,
                        children: [
                          Text(
                            _currentSpeed.toStringAsFixed(0),
                            style: TextStyle(
                              color: _currentSpeed > 50 ? Colors.redAccent : Colors.white,
                              fontSize: 48,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(width: 4),
                          const Text('km/h', style: TextStyle(color: Colors.grey, fontSize: 16)),
                        ],
                      ),
                    ],
                  ),
                  Container(width: 1, height: 40, color: Colors.grey.withOpacity(0.3)),
                  const Column(
                    children: [
                      Text('LIMIT', 
                        style: TextStyle(color: Colors.grey, fontSize: 12, letterSpacing: 1.2)),
                      Text(
                        '--', 
                        style: TextStyle(color: Colors.white, fontSize: 48, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // Recenter Button
          if (!_isCameraLocked)
            Positioned(
              bottom: 140,
              right: 20,
              child: FloatingActionButton(
                backgroundColor: Colors.white,
                child: const Icon(Icons.navigation, color: Colors.blue),
                onPressed: () {
                  setState(() {
                    _isCameraLocked = true;
                  });
                },
              ),
            ),
        ],
      ),
    );
  }
}
