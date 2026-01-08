class SpeedCamera {
  final int id;
  final double latitude;
  final double longitude;
  final int? speedLimit; // in km/h
  final DateTime fetchedAt;

  SpeedCamera({
    required this.id,
    required this.latitude,
    required this.longitude,
    this.speedLimit,
    required this.fetchedAt,
  });

  factory SpeedCamera.fromOsmJson(Map<String, dynamic> json) {
    int? limit;
    if (json['tags'] != null && json['tags']['maxspeed'] != null) {
      // Try to parse "60", "60 km/h", "50 mph" if needed
      String raw = json['tags']['maxspeed'].toString();
      limit = int.tryParse(raw.replaceAll(RegExp(r'[^0-9]'), ''));
    }

    return SpeedCamera(
      id: json['id'],
      latitude: json['lat'],
      longitude: json['lon'],
      speedLimit: limit,
      fetchedAt: DateTime.now(),
    );
  }
}
