import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/camera_model.dart';

class OsmService {
  static const String _overpassUrl = 'https://overpass-api.de/api/interpreter';

  Future<List<SpeedCamera>> fetchSpeedCameras(
      double lat, double lon, double radiusMeters) async {
    // Convert radius to approx degrees (1 deg ~= 111111 meters)
    double delta = radiusMeters / 111111;
    
    double south = lat - delta;
    double west = lon - delta;
    double north = lat + delta;
    double east = lon + delta;

    // Overpass QL Query
    // [out:json][timeout:25];
    // node["highway"="speed_camera"](south,west,north,east);
    // out;
    String query = '''
      [out:json][timeout:25];
      node["highway"="speed_camera"]($south,$west,$north,$east);
      out;
    ''';

    try {
      final response = await http.post(
        Uri.parse(_overpassUrl),
        body: {'data': query},
      );

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        if (data['elements'] != null) {
          return (data['elements'] as List)
              .map((e) => SpeedCamera.fromOsmJson(e))
              .toList();
        }
      } else {
        print('Error fetching OSM data: ${response.statusCode}');
      }
    } catch (e) {
      print('Exception fetching OSM data: $e');
    }
    
    return [];
  }
}
