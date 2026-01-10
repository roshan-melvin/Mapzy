import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mappls_gl/mappls_gl.dart';
import 'package:permission_handler/permission_handler.dart';
import 'screens/map_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Mappls SDK
  // Mappls SDK Configuration
  String apiKey = "4f5f56254fc624e2a817b1b2d2de4020";
  MapplsAccountManager.setMapSDKKey(apiKey);
  MapplsAccountManager.setRestAPIKey(apiKey);
  MapplsAccountManager.setAtlasClientId("96dHZVzsAuugadw-3_1eeb4IapToWCSpJypKyJKDGuItzGMH3MU7FBnX-hPym7qFvg7zJ3QN-j4xkE--p_4JoQ==");
  MapplsAccountManager.setAtlasClientSecret("lrFxI-iSEg-JlQ2tDZ-tuTMRtniiUG6rX0JZsAnxSrnuOU1U0v3f8OJqeZ9b_14NjptQRzVmBSOiaNUfrdtCAM2gvc_E3-o5");

  runApp(const ZwapApp());
}

class ZwapApp extends StatelessWidget {
  const ZwapApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Zwap',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MapScreen(),
    );
  }
}
