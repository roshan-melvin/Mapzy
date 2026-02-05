// Script to delete the old History collection
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function deleteHistoryCollection() {
  console.log('🗑️  Deleting old History collection...\n');
  
  try {
    const historyRef = db.collection('History');
    const snapshot = await historyRef.get();
    
    if (snapshot.empty) {
      console.log('ℹ️  History collection is already empty or does not exist.');
      process.exit(0);
    }
    
    console.log(`Found ${snapshot.size} documents to delete...`);
    
    const batch = db.batch();
    let count = 0;
    
    snapshot.docs.forEach((doc) => {
      batch.delete(doc.ref);
      count++;
      console.log(`  - Deleting: ${doc.id}`);
    });
    
    await batch.commit();
    
    console.log(`\n✅ Successfully deleted ${count} documents from History collection!`);
    console.log('✅ Old History collection has been removed.\n');
    
    process.exit(0);
  } catch (error) {
    console.error('❌ Error deleting History collection:', error.message);
    process.exit(1);
  }
}

deleteHistoryCollection();
