const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { Pool } = require("pg");

admin.initializeApp();

// 1. Configure your PostgreSQL Connection
// These should ideally be set as environment variables: 
// firebase functions:config:set db.user="user" db.pass="pass" ...
const pool = new Pool({
    user: functions.config().db.user,
    host: functions.config().db.host,
    database: functions.config().db.name,
    password: functions.config().db.pass,
    port: 5432,
});

/**
 * Trigger: Firestore "onCreate"
 * Listens for new documents in 'reports' collection (or subcollections)
 * 
 * Note: If using subcollections like `reports/{channel}/threads/{reportId}`,
 * the wildcard path should be: 'reports/{channelId}/threads/{reportId}'
 */
exports.syncReportToPostgres = functions.firestore
    .document("reports/{channelId}/threads/{reportId}")
    .onCreate(async (snap, context) => {

        // 2. Get the new data
        const newData = snap.data();
        const reportId = context.params.reportId;
        const channelId = context.params.channelId;

        console.log(`Syncing new report ${reportId} from channel ${channelId} to Postgres...`);

        // 3. Prepare SQL Query
        // We map Firestore fields to Postgres columns
        const queryText = `
      INSERT INTO reports_analysis (
        report_id, 
        user_id, 
        incident_type, 
        description, 
        severity, 
        latitude, 
        longitude, 
        image_url, 
        status, 
        created_at
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      ON CONFLICT (report_id) DO NOTHING; -- Prevent duplicates
    `;

        // 4. Map Values
        // Handle potential missing fields (null safety)
        const values = [
            reportId,
            newData.userId || "unknown",
            newData.incidentType || "general",
            newData.description || "",
            newData.severity || 1,
            newData.latitude || 0.0,
            newData.longitude || 0.0,
            newData.imageUrl || null,
            newData.status || "Pending",
            // Convert Firestore Timestamp to JS Date for Postgres
            newData.createdAt ? newData.createdAt.toDate() : new Date()
        ];

        try {
            // 5. Execute Sync
            const client = await pool.connect();
            await client.query(queryText, values);
            client.release();

            console.log(`Successfully synced report ${reportId} to Postgres.`);
            return null;

        } catch (error) {
            console.error("Failed to sync to Postgres:", error);
            // Optional: Write error back to Firestore to flag for retry
            // await snap.ref.set({ sync_error: error.message }, { merge: true });
            return null;
        }
    });
