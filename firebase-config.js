// Firebase Configuration and Authentication
// This file handles all Firebase initialization and authentication functions

// Firebase Configuration
const firebaseConfig = {
    apiKey: "AIzaSyBlPxQiAFQX_PCNdWRO9kXolpyqWsJ88LM",
    authDomain: "mapzy2.firebaseapp.com",
    projectId: "mapzy2",
    storageBucket: "mapzy2.firebasestorage.app",
    messagingSenderId: "722722178522",
    appId: "1:722722178522:web:308907fea0d0249536ece3",
    measurementId: "G-DJE8NG89Q6"
};

// Initialize Firebase (will be done after SDK loads)
let auth;
let db;
let currentUser = null;

// Gamification State - Single source of truth
let userStats = {
    rewardPoints: 0,
    totalReports: 0,
    reportBreakdown: {
        accident: 0,
        construction: 0,
        roadblock: 0,
        waterlogging: 0,
        fallenTree: 0,
        speedCamera: 0
    }
};

// Initialize Firebase after SDKs are loaded
function initializeFirebase() {
    try {
        // Initialize Firebase
        firebase.initializeApp(firebaseConfig);

        // Initialize services
        auth = firebase.auth();
        db = firebase.firestore();

        console.log('Firebase initialized successfully');

        // Set up auth state listener
        auth.onAuthStateChanged(onAuthStateChanged);

    } catch (error) {
        console.error('Error initializing Firebase:', error);
    }
}

// Auth state change handler
function onAuthStateChanged(user) {
    if (user) {
        // User is signed in
        currentUser = user;
        console.log('User signed in:', user.email);

        // Update UI
        updateProfileUI(user);

        // Load search history
        loadSearchHistory();

        // Load user stats (gamification)
        loadUserStats();

    } else {
        // User is signed out
        currentUser = null;
        console.log('User signed out');

        // Update UI
        updateProfileUI(null);

        // Clear search history
        clearSearchHistoryUI();
    }
}

// Sign in with Google
async function signInWithGoogle() {
    try {
        const provider = new firebase.auth.GoogleAuthProvider();
        const result = await auth.signInWithPopup(provider);

        // User signed in successfully
        const user = result.user;
        console.log('Sign in successful:', user.email);

        // Save user profile to Firestore
        await saveUserProfile(user);

        // Close sidebar after sign in
        closeSidebar();

        return user;

    } catch (error) {
        console.error('Error signing in:', error);
        alert('Failed to sign in: ' + error.message);
        return null;
    }
}

// Sign in with Email/Password
async function signInWithEmail(email, password) {
    try {
        const result = await auth.signInWithEmailAndPassword(email, password);

        // User signed in successfully
        const user = result.user;
        console.log('Email sign in successful:', user.email);

        // Save user profile to Firestore
        await saveUserProfile(user);

        // Clear input fields
        document.getElementById('emailInput').value = '';
        document.getElementById('passwordInput').value = '';

        // Close sidebar after sign in
        closeSidebar();

        return user;

    } catch (error) {
        console.error('Error signing in with email:', error);

        // User-friendly error messages
        let errorMessage = 'Failed to sign in';
        if (error.code === 'auth/user-not-found') {
            errorMessage = 'No account found with this email. Please sign up first.';
        } else if (error.code === 'auth/wrong-password') {
            errorMessage = 'Incorrect password. Please try again.';
        } else if (error.code === 'auth/invalid-email') {
            errorMessage = 'Invalid email address.';
        } else {
            errorMessage = error.message;
        }

        alert(errorMessage);
        return null;
    }
}

// Sign up with Email/Password
async function signUpWithEmail(email, password) {
    try {
        // Validate password length
        if (password.length < 6) {
            alert('Password must be at least 6 characters long');
            return null;
        }

        const result = await auth.createUserWithEmailAndPassword(email, password);

        // User created successfully
        const user = result.user;
        console.log('Sign up successful:', user.email);

        // Save user profile to Firestore
        await saveUserProfile(user);

        // Clear input fields
        document.getElementById('emailInput').value = '';
        document.getElementById('passwordInput').value = '';

        // Close sidebar after sign up
        closeSidebar();

        alert('Account created successfully! Welcome to Mapzy.');

        return user;

    } catch (error) {
        console.error('Error signing up:', error);

        // User-friendly error messages
        let errorMessage = 'Failed to create account';
        if (error.code === 'auth/email-already-in-use') {
            errorMessage = 'An account with this email already exists. Please sign in instead.';
        } else if (error.code === 'auth/invalid-email') {
            errorMessage = 'Invalid email address.';
        } else if (error.code === 'auth/weak-password') {
            errorMessage = 'Password is too weak. Please use a stronger password.';
        } else {
            errorMessage = error.message;
        }

        alert(errorMessage);
        return null;
    }
}

// Sign out
async function signOut() {
    try {
        await auth.signOut();
        console.log('Sign out successful');

        // Close sidebar after sign out
        closeSidebar();

    } catch (error) {
        console.error('Error signing out:', error);
        alert('Failed to sign out: ' + error.message);
    }
}

// Save user profile to Firestore
async function saveUserProfile(user) {
    try {
        await db.collection('users').doc(user.uid).set({
            name: user.displayName,
            email: user.email,
            photoURL: user.photoURL,
            lastLogin: firebase.firestore.FieldValue.serverTimestamp()
        }, { merge: true });

        console.log('User profile saved');
    } catch (error) {
        console.error('Error saving user profile:', error);
    }
}

// Save search to history
async function saveSearchToHistory(searchData) {
    if (!currentUser) {
        console.log('User not logged in, search not saved');
        return;
    }

    try {
        await db.collection('users').doc(currentUser.uid)
            .collection('searchHistory')
            .add({
                query: searchData.query,
                placeName: searchData.placeName,
                eLoc: searchData.eLoc,
                placeAddress: searchData.placeAddress || '',
                timestamp: firebase.firestore.FieldValue.serverTimestamp()
            });

        console.log('Search saved to history');

        // Reload search history
        loadSearchHistory();

    } catch (error) {
        console.error('Error saving search:', error);
    }
}

// Load search history
async function loadSearchHistory() {
    if (!currentUser) {
        return [];
    }

    try {
        const snapshot = await db.collection('users').doc(currentUser.uid)
            .collection('searchHistory')
            .orderBy('timestamp', 'desc')
            .limit(10)
            .get();

        const history = [];
        snapshot.forEach(doc => {
            history.push({
                id: doc.id,
                ...doc.data()
            });
        });

        console.log('Search history loaded:', history.length, 'items');

        // Update UI with search history
        updateSearchHistoryUI(history);

        return history;

    } catch (error) {
        console.error('Error loading search history:', error);
        return [];
    }
}

// Save search to history (or update timestamp if duplicate)
async function saveSearchHistory(query, placeData = null) {
    if (!currentUser || !query || query.trim() === '') {
        return;
    }

    try {
        const normalizedQuery = query.trim().toLowerCase();
        
        // Check if this query already exists
        const existingSnapshot = await db.collection('users').doc(currentUser.uid)
            .collection('searchHistory')
            .where('queryLower', '==', normalizedQuery)
            .limit(1)
            .get();

        if (!existingSnapshot.empty) {
            // Update timestamp of existing entry
            const docId = existingSnapshot.docs[0].id;
            await db.collection('users').doc(currentUser.uid)
                .collection('searchHistory')
                .doc(docId)
                .update({
                    timestamp: firebase.firestore.FieldValue.serverTimestamp()
                });
            console.log('Search history updated:', query);
        } else {
            // Create new entry
            await db.collection('users').doc(currentUser.uid)
                .collection('searchHistory')
                .add({
                    query: query.trim(),
                    queryLower: normalizedQuery,
                    placeData: placeData || null,
                    timestamp: firebase.firestore.FieldValue.serverTimestamp()
                });
            console.log('Search history saved:', query);
        }

        // Reload search history to update UI
        await loadSearchHistory();

    } catch (error) {
        console.error('Error saving search history:', error);
    }
}

// Update profile UI
function updateProfileUI(user) {
    const profileSection = document.getElementById('profileSection');
    const signInBtn = document.getElementById('signInBtn');
    const emailAuthForm = document.getElementById('emailAuthForm');
    const profileInfo = document.getElementById('profileInfo');
    const profilePhoto = document.getElementById('profilePhoto');
    const profileName = document.getElementById('profileName');
    const profileEmail = document.getElementById('profileEmail');
    const logoutBtn = document.getElementById('logoutBtn');

    if (user) {
        // User is logged in
        signInBtn.style.display = 'none';
        emailAuthForm.style.display = 'none';
        profileInfo.style.display = 'block';

        profilePhoto.src = user.photoURL || 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0MCIgaGVpZ2h0PSI0MCIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJub25lIiBzdHJva2U9IiM2NjY2NjYiIHN0cm9rZS13aWR0aD0iMiI+PGNpcmNsZSBjeD0iMTIiIGN5PSI4IiByPSI0Ii8+PHBhdGggZD0iTTQgMjB2LTJhNCA0IDAgMCAxIDQtNGg4YTQgNCAwIDAgMSA0IDR2MiIvPjwvc3ZnPg==';
        profileName.textContent = user.displayName || user.email.split('@')[0];
        profileEmail.textContent = user.email;

    } else {
        // User is logged out
        signInBtn.style.display = 'block';
        emailAuthForm.style.display = 'block';
        profileInfo.style.display = 'none';
    }
}

// Update search history UI (will be integrated with autocomplete)
function updateSearchHistoryUI(history) {
    // Store in global variable for autocomplete to access
    window.searchHistory = history;
}

// Clear search history UI
function clearSearchHistoryUI() {
    window.searchHistory = [];
}

// ========== GAMIFICATION FUNCTIONS ==========

// Load user stats from Firestore
async function loadUserStats() {
    if (!currentUser) {
        // Reset to defaults if not logged in
        userStats = {
            rewardPoints: 0,
            totalReports: 0,
            reportBreakdown: {
                accident: 0,
                construction: 0,
                roadblock: 0,
                waterlogging: 0,
                fallenTree: 0,
                speedCamera: 0
            }
        };
        return;
    }

    try {
        const userDoc = await db.collection('users').doc(currentUser.uid).get();

        if (userDoc.exists) {
            const data = userDoc.data();
            userStats = {
                rewardPoints: data.rewardPoints || 0,
                totalReports: data.totalReports || 0,
                reportBreakdown: data.reportBreakdown || {
                    accident: 0,
                    construction: 0,
                    roadblock: 0,
                    waterlogging: 0,
                    fallenTree: 0,
                    speedCamera: 0
                }
            };
        } else {
            // Initialize for new user
            userStats = {
                rewardPoints: 0,
                totalReports: 0,
                reportBreakdown: {
                    accident: 0,
                    construction: 0,
                    roadblock: 0,
                    waterlogging: 0,
                    fallenTree: 0,
                    speedCamera: 0
                }
            };
            await saveUserStats();
        }

        console.log('User stats loaded:', userStats);
        updateRewardPointsUI();
        updateStatsOverlayUI(); // Ensure overlay is also updated

    } catch (error) {
        console.error('Error loading user stats:', error);
    }
}

// Save user stats to Firestore
async function saveUserStats() {
    if (!currentUser) return;

    try {
        await db.collection('users').doc(currentUser.uid).set({
            rewardPoints: userStats.rewardPoints,
            totalReports: userStats.totalReports,
            reportBreakdown: userStats.reportBreakdown
        }, { merge: true });

        console.log('User stats saved');
    } catch (error) {
        console.error('Error saving user stats:', error);
    }
}

// Submit a report to global collection
async function submitReport(type, hazardType, location, imageUrl = null) {
    if (!currentUser) {
        alert('Please sign in to submit reports');
        return false;
    }

    try {
        // Prepare report data - all reports go under 'hazards' channel
        // Speed cameras are now a hazard type, not separate
        const reportData = {
            type: 'hazard', // All reports are hazards (including speed cameras)
            hazardType: hazardType || 'speedCamera', // speedCamera is now a hazard type
            reportedBy: currentUser.uid,
            reportedByName: currentUser.displayName || currentUser.email.split('@')[0],
            reportedByEmail: currentUser.email,
            latitude: location.lat,
            longitude: location.lng,
            imageUrl: imageUrl, // Cloudinary URL
            timestamp: firebase.firestore.FieldValue.serverTimestamp(),
            status: 'pending'
        };

        // Add report to hierarchical structure: /reports/hazards/threads/{reportId}
        await db.collection('reports')
            .doc('hazards')
            .collection('threads')
            .add(reportData);

        // Determine which stat to increment (hazardType includes speedCamera now)
        const statKey = hazardType || 'speedCamera';

        // Update user stats
        userStats.totalReports += 1;
        userStats.reportBreakdown[statKey] = (userStats.reportBreakdown[statKey] || 0) + 1;
        userStats.rewardPoints += 1;

        // Save to Firebase
        await saveUserStats();

        // Update UI instantly
        updateRewardPointsUI();
        updateStatsOverlayUI();

        console.log('Report submitted:', type, hazardType, location);
        return true;

    } catch (error) {
        console.error('Error submitting report:', error);
        alert('Failed to submit report. Please try again.');
        return false;
    }
}

// Update reward points display in UI
function updateRewardPointsUI() {
    const rewardPointsElement = document.getElementById('rewardPoints');
    if (rewardPointsElement) {
        rewardPointsElement.textContent = userStats.rewardPoints;
    }
}

// Update stats overlay UI
function updateStatsOverlayUI() {
    const totalReportsElement = document.getElementById('statsTotalReports');
    const hazardReportsElement = document.getElementById('statsHazardReports');
    const speedCameraReportsElement = document.getElementById('statsSpeedCameraReports');
    const othersReportsElement = document.getElementById('statsOthersReports');

    if (totalReportsElement) totalReportsElement.textContent = userStats.totalReports;
    if (hazardReportsElement) hazardReportsElement.textContent = userStats.reportBreakdown.hazard;
    if (speedCameraReportsElement) speedCameraReportsElement.textContent = userStats.reportBreakdown.speedCamera;
    if (othersReportsElement) othersReportsElement.textContent = userStats.reportBreakdown.others;
}


// ========================================
// CHAT SYSTEM FUNCTIONS
// ========================================

// Current region and channel state
let currentRegion = null;
let currentChannel = 'general';
let messageUnsubscribe = null;

// Predefined channels matching Android app
const CHAT_CHANNELS = {
    welcome: { name: 'Welcome', icon: '👋', description: 'Welcome to the region!' },
    general: { name: 'General', icon: '💬', description: 'General discussion' },
    hazards: { name: 'Hazards', icon: '⚠️', description: 'Report and discuss hazards' },
    traffic: { name: 'Traffic', icon: '🚦', description: 'Traffic updates' },
    speedCameras: { name: 'Speed Cameras', icon: '📷', description: 'Speed camera locations' }
};

// Join a regional chat server
async function joinRegionalChat(country, state, city) {
    if (!currentUser) {
        throw new Error('Please sign in to join chat');
    }

    const regionId = `${country}-${state}-${city}`.toLowerCase().replace(/\s+/g, '-');

    currentRegion = {
        id: regionId,
        country: country,
        state: state,
        city: city,
        displayName: `${city}, ${state}, ${country}`
    };

    // Save to localStorage
    localStorage.setItem('currentRegion', JSON.stringify(currentRegion));

    console.log('Joined regional chat:', currentRegion.displayName);
    return currentRegion;
}

// Get current region from localStorage
function getCurrentRegion() {
    // ALWAYS read from storage to prevent stale state issues
    // The previous caching (if (currentRegion) return currentRegion) caused desync
    try {
        const stored = localStorage.getItem('currentRegion');
        if (stored) {
            currentRegion = JSON.parse(stored); // Update memory mirror just in case
            return currentRegion;
        }
    } catch (e) {
        console.error("Error parsing currentRegion", e);
    }
    return null;
}

// Subscribe to a channel's messages
function subscribeToChannel(channelId, onMessageUpdate) {
    // Unsubscribe from previous channel
    if (messageUnsubscribe) {
        messageUnsubscribe();
    }

    const region = getCurrentRegion();
    console.log('[SUBSCRIBE] Channel:', channelId, 'Region:', region);
    if (!region) {
        console.error('No region selected');
        return null;
    }

    // Safety check: Firebase might not be initialized yet
    if (!db) {
        console.warn('Firebase not initialized yet. Deferring channel subscription.');
        return null;
    }

    currentChannel = channelId;

    // Listen to messages in real-time
    messageUnsubscribe = db.collection('chat')
        .doc(region.id)
        .collection('threads')
        .doc(channelId)
        .collection('messages')
        .orderBy('created_at', 'asc')
        .onSnapshot((snapshot) => {
            const messages = [];
            snapshot.forEach((doc) => {
                messages.push({
                    id: doc.id,
                    ...doc.data()
                });
            });
            onMessageUpdate(messages);
        }, (error) => {
            console.error('Error listening to messages:', error);
        });

    return messageUnsubscribe;
}

// Send a chat message
async function sendChatMessage(text, mediaUrl = null) {
    if (!currentUser) {
        throw new Error('Please sign in to send messages');
    }

    const region = getCurrentRegion();
    if (!region) {
        throw new Error('Please join a region first');
    }

    if (!text && !mediaUrl) {
        throw new Error('Message cannot be empty');
    }

    if (!db) {
        throw new Error('Firebase not initialized. Please wait and try again.');
    }

    const messageData = {
        user_id: currentUser.uid,
        username: currentUser.displayName || currentUser.email.split('@')[0],
        user_avatar: currentUser.photoURL || `https://ui-avatars.com/api/?name=${encodeURIComponent(currentUser.displayName || currentUser.email)}&background=random`,
        text: text || '',
        type: mediaUrl ? (mediaUrl.includes('video') ? 'video' : 'image') : 'text',
        image_url: mediaUrl,
        created_at: firebase.firestore.FieldValue.serverTimestamp()
    };

    try {
        await db.collection('chat')
            .doc(region.id)
            .collection('threads')
            .doc(currentChannel)
            .collection('messages')
            .add(messageData);

        console.log('Message sent successfully');
        return true;
    } catch (error) {
        console.error('Error sending message:', error);
        throw error;
    }
}

// Upload chat media to Cloudinary
async function uploadChatMedia(file) {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('upload_preset', 'Mapzy1234');
    formData.append('cloud_name', 'dpca1m8ut');

    // Determine resource type based on file type
    const resourceType = file.type.startsWith('video/') ? 'video' : 'image';

    try {
        const response = await fetch(
            `https://api.cloudinary.com/v1_1/dpca1m8ut/${resourceType}/upload`,
            {
                method: 'POST',
                body: formData
            }
        );

        if (!response.ok) {
            throw new Error('Upload failed');
        }

        const data = await response.json();
        return data.secure_url;
    } catch (error) {
        console.error('Error uploading media:', error);
        throw error;
    }
}

// Leave current region
function leaveRegionalChat() {
    if (messageUnsubscribe) {
        messageUnsubscribe();
        messageUnsubscribe = null;
    }
    currentRegion = null;
    currentChannel = 'general';
    localStorage.removeItem('currentRegion');
}

console.log('Chat functions loaded');
console.log('firebase-config.js loaded');

