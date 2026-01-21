# Mapzy - Navigation & Safety App

Web-based navigation app with real-time routing, nearby places discovery, and hazard alerts.

## Why It Matters

Promotes road safety and legal compliance by alerting drivers to speed camera locations and road hazards. Helps citizens stay within speed limits, avoid traffic violations, and navigate safely around construction zones and accidents. Supports government road safety initiatives by encouraging responsible driving behavior.

## Features

- **Turn-by-turn Navigation** - Real-time directions with route calculation
- **Nearby Places** - Find petrol pumps, restaurants, ATMs, hospitals, hotels, cafes, pharmacies, banks within 5km
- **Hazard Alerts** - View speed cameras and road hazards from OpenStreetMap (toggle on/off)
- **Authentication** - Google Sign-In & Email/Password login

## Planned: Gamification System

Users will earn reward points by reporting speed cameras and hazards. A leaderboard will rank users based on genuine reports, encouraging community participation.

## Setup

1. Add Mappls API key to `config.js`
2. Configure Firebase in `firebase-config.js`
3. Set Firestore security rules
4. Open `index.html`

## Tech Stack

- Mappls Web SDK
- Firebase (Auth & Firestore)
- OpenStreetMap
- Vanilla JavaScript

---

**Note:** Mappls works for India only.
