// Install first: npm install firebase-admin
const admin = require('firebase-admin');

// Download your Firebase service account JSON from Firebase Console
// Project Settings → Service Accounts → Generate Key
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const auth = admin.auth();

async function createTestUsers() {
  try {
    // Test User 1
    const user1 = await auth.createUser({
      email: 'test1@example.com',
      password: 'Test@123456'
    });

    console.log(`✅ Created user1: ${user1.uid} (test1@example.com)`);

    // Add dummy search history for user1
    await db.collection('users').doc(user1.uid).collection('searchHistory').add({
      placeName: 'Taj Mahal',
      placeAddress: 'Dharmapuri, Forest Colony, Tajganj, Agra, Uttar Pradesh 282001',
      mapplsPin: '7E3K5S',
      timestamp: Date.now() - 86400000 // 1 day ago
    });

    await db.collection('users').doc(user1.uid).collection('searchHistory').add({
      placeName: 'Gateway of India',
      placeAddress: 'Apollo Bandar, Colaba, Mumbai, Maharashtra 400001',
      mapplsPin: 'MAMI7H',
      timestamp: Date.now() - 43200000 // 12 hours ago
    });

    await db.collection('users').doc(user1.uid).collection('searchHistory').add({
      placeName: 'India Gate',
      placeAddress: 'New Delhi, Delhi 110001',
      mapplsPin: 'DELI2P',
      timestamp: Date.now() - 3600000 // 1 hour ago
    });

    console.log('✅ Added 3 history items for user1');

    // Test User 2
    const user2 = await auth.createUser({
      email: 'test2@example.com',
      password: 'Test@123456'
    });

    console.log(`✅ Created user2: ${user2.uid} (test2@example.com)`);

    // Add dummy search history for user2
    await db.collection('users').doc(user2.uid).collection('searchHistory').add({
      placeName: 'Hawa Mahal',
      placeAddress: 'Sanganeri Gate, Pink City, Jaipur, Rajasthan 302002',
      mapplsPin: 'JAI5Q8',
      timestamp: Date.now() - 172800000 // 2 days ago
    });

    await db.collection('users').doc(user2.uid).collection('searchHistory').add({
      placeName: 'Meenakshi Temple',
      placeAddress: 'East Tower Street, Madurai, Tamil Nadu 625001',
      mapplsPin: 'MADU3R',
      timestamp: Date.now() - 259200000 // 3 days ago
    });

    console.log('✅ Added 2 history items for user2');

    console.log('\n🎉 Test Data Created Successfully!\n');
    console.log('Test Credentials:');
    console.log('─────────────────────────────────');
    console.log('User 1: test1@example.com / Test@123456');
    console.log('User 2: test2@example.com / Test@123456');
    console.log('─────────────────────────────────\n');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error creating test data:', error);
    process.exit(1);
  }
}

createTestUsers();
