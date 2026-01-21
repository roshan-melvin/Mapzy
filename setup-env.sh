#!/bin/bash

# 1. Generate config.js for Mappls
echo "var mappls_access_token = '${MAPPLS_ACCESS_TOKEN:-YOUR_DEFAULT_MAPPLS_TOKEN}';" > config.js

# 2. Generate firebase-config.js from template
if [ -f "firebase-config.js.template" ]; then
    cp firebase-config.js.template firebase-config.js
    
    # Replace placeholders with environment variables or defaults
    sed -i "s/__FIREBASE_API_KEY__/${FIREBASE_API_KEY:-AIzaSyBlPxQiAFQX_PCNdWRO9kXolpyqWsJ88LM}/g" firebase-config.js
    sed -i "s/__FIREBASE_AUTH_DOMAIN__/${FIREBASE_AUTH_DOMAIN:-mapzy2.firebaseapp.com}/g" firebase-config.js
    sed -i "s/__FIREBASE_PROJECT_ID__/${FIREBASE_PROJECT_ID:-mapzy2}/g" firebase-config.js
    sed -i "s/__FIREBASE_STORAGE_BUCKET__/${FIREBASE_STORAGE_BUCKET:-mapzy2.firebasestorage.app}/g" firebase-config.js
    sed -i "s/__FIREBASE_MESSAGING_SENDER_ID__/${FIREBASE_MESSAGING_SENDER_ID:-722722178522}/g" firebase-config.js
    sed -i "s/__FIREBASE_APP_ID__/${FIREBASE_APP_ID:-1:722722178522:web:308907fea0d0249536ece3}/g" firebase-config.js
    sed -i "s/__FIREBASE_MEASUREMENT_ID__/${FIREBASE_MEASUREMENT_ID:-G-DJE8NG89Q6}/g" firebase-config.js
    
    echo "firebase-config.js generated successfully."
else
    echo "Error: firebase-config.js.template not found."
fi
