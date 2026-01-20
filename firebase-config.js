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

console.log('firebase-config.js loaded');
