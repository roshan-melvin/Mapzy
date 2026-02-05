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

async function createCompleteTestData() {
  try {
    console.log('🚀 Creating test users with complete profiles...\n');

    // Test User 1 - Using unique email with timestamp
    const timestamp = Date.now();
    
    console.log('📝 Creating Test User 1...');
    const user1 = await auth.createUser({
      email: `testuser1_${timestamp}@example.com`,
      password: 'Test@123456'
    });

    console.log(`✅ Created user1: ${user1.uid}`);

    // Create user profile document
    await db.collection('users').doc(user1.uid).set({
      email: `testuser1_${timestamp}@example.com`,
      name: 'Test User One',
      photoURL: 'https://lh3.googleusercontent.com/a/default-user-avatar',
      lastLogin: admin.firestore.Timestamp.now(),
      createdAt: admin.firestore.Timestamp.now()
    });

    console.log('✅ Created user profile for user1');

    // Add search history for user1
    const searchHistory1 = [
      {
        placeName: 'Taj Mahal',
        placeAddress: 'Dharmapuri, Forest Colony, Tajganj, Agra, Uttar Pradesh 282001',
        mapplsPin: '7E3K5S',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 86400000)) // 1 day ago
      },
      {
        placeName: 'Gateway of India',
        placeAddress: 'Apollo Bandar, Colaba, Mumbai, Maharashtra 400001',
        mapplsPin: 'MAMI7H',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 43200000)) // 12 hours ago
      },
      {
        placeName: 'India Gate',
        placeAddress: 'New Delhi, Delhi 110001',
        mapplsPin: 'DELI2P',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 3600000)) // 1 hour ago
      },
      {
        placeName: 'Hawa Mahal',
        placeAddress: 'Sanganeri Gate, Pink City, Jaipur, Rajasthan 302002',
        mapplsPin: 'JAI5Q8',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 172800000)) // 2 days ago
      },
      {
        placeName: 'Red Fort',
        placeAddress: 'Netaji Subhash Marg, New Delhi, Delhi 110006',
        mapplsPin: 'DELI4X',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 259200000)) // 3 days ago
      }
    ];

    for (const history of searchHistory1) {
      await db.collection('users')
        .doc(user1.uid)
        .collection('searchHistory')
        .add(history);
    }

    console.log('✅ Added 5 history items for user1\n');

    // Test User 2
    console.log('📝 Creating Test User 2...');
    const user2 = await auth.createUser({
      email: `testuser2_${timestamp}@example.com`,
      password: 'Test@123456'
    });

    console.log(`✅ Created user2: ${user2.uid}`);

    // Create user profile document
    await db.collection('users').doc(user2.uid).set({
      email: `testuser2_${timestamp}@example.com`,
      name: 'Test User Two',
      photoURL: 'https://lh3.googleusercontent.com/a/default-user-avatar',
      lastLogin: admin.firestore.Timestamp.now(),
      createdAt: admin.firestore.Timestamp.now()
    });

    console.log('✅ Created user profile for user2');

    // Add search history for user2
    const searchHistory2 = [
      {
        placeName: 'Meenakshi Temple',
        placeAddress: 'East Tower Street, Madurai, Tamil Nadu 625001',
        mapplsPin: 'MADU3R',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 259200000)) // 3 days ago
      },
      {
        placeName: 'Jallianwala Bagh',
        placeAddress: 'Jallianwala Bagh, Amritsar, Punjab 143006',
        mapplsPin: 'AMRI2S',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 172800000)) // 2 days ago
      },
      {
        placeName: 'Varanasi Ghats',
        placeAddress: 'Assi Ghat, Varanasi, Uttar Pradesh 221001',
        mapplsPin: 'VARA5K',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 86400000)) // 1 day ago
      }
    ];

    for (const history of searchHistory2) {
      await db.collection('users')
        .doc(user2.uid)
        .collection('searchHistory')
        .add(history);
    }

    console.log('✅ Added 3 history items for user2\n');

    // Test User 3
    console.log('📝 Creating Test User 3...');
    const user3 = await auth.createUser({
      email: `testuser3_${timestamp}@example.com`,
      password: 'Test@123456'
    });

    console.log(`✅ Created user3: ${user3.uid}`);

    // Create user profile document
    await db.collection('users').doc(user3.uid).set({
      email: `testuser3_${timestamp}@example.com`,
      name: 'Test User Three',
      photoURL: 'https://lh3.googleusercontent.com/a/default-user-avatar',
      lastLogin: admin.firestore.Timestamp.now(),
      createdAt: admin.firestore.Timestamp.now()
    });

    console.log('✅ Created user profile for user3');

    // Add search history for user3
    const searchHistory3 = [
      {
        placeName: 'Christ the Redeemer',
        placeAddress: 'Corcovado, Rio de Janeiro, Brazil',
        mapplsPin: 'RIO1M',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 43200000)) // 12 hours ago
      },
      {
        placeName: 'Mysore Palace',
        placeAddress: 'Sayyaji Rao Road, Mysore, Karnataka 570001',
        mapplsPin: 'MYSO2P',
        timestamp: admin.firestore.Timestamp.fromDate(new Date(Date.now() - 3600000)) // 1 hour ago
      }
    ];

    for (const history of searchHistory3) {
      await db.collection('users')
        .doc(user3.uid)
        .collection('searchHistory')
        .add(history);
    }

    console.log('✅ Added 2 history items for user3\n');

    console.log('═══════════════════════════════════════════════════════');
    console.log('🎉 Test Data Created Successfully!\n');
    console.log('Test Credentials:');
    console.log('───────────────────────────────────────────────────────');
    console.log(`User 1: testuser1_${timestamp}@example.com / Test@123456`);
    console.log('        (5 search history items)');
    console.log('');
    console.log(`User 2: testuser2_${timestamp}@example.com / Test@123456`);
    console.log('        (3 search history items)');
    console.log('');
    console.log(`User 3: testuser3_${timestamp}@example.com / Test@123456`);
    console.log('        (2 search history items)');
    console.log('───────────────────────────────────────────────────────');
    console.log('═══════════════════════════════════════════════════════\n');

    console.log('✅ All users have complete profiles with:');
    console.log('   • email');
    console.log('   • name');
    console.log('   • photoURL');
    console.log('   • lastLogin');
    console.log('   • createdAt');
    console.log('   • searchHistory (subcollection)\n');

    process.exit(0);
  } catch (error) {
    console.error('❌ Error creating test data:', error.message);
    process.exit(1);
  }
}

createCompleteTestData();
