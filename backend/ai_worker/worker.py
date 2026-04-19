import time
import os
from supabase import create_client, Client
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import google.generativeai as genai

# ================= CONFIGURATION =================
# Get the directory where this script is located
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# 1. Supabase Setup
SUPABASE_URL = "https://mjsylrqiyjupixopbdez.supabase.co"
SUPABASE_KEY = "sb_publishable_pPnGYcgWm5HlRZFARW2kHA_QsrXLtv8" # TODO: Use SERVICE_ROLE key for production!

# 2. Firebase Setup
# You need to download this from Firebase Console -> Project Settings -> Service Accounts
FIREBASE_CREDENTIAL_PATH = os.path.join(SCRIPT_DIR, "serviceAccountKey.json") 

# 3. Gemini AI Setup
GEMINI_API_KEY = "YOUR_GEMINI_API_KEY"

# Initialize Supabase
supabase: Client = create_client(SUPABASE_URL, SUPABASE_KEY)

# Initialize Firebase
db = None
print(f"📂 Looking for credentials at: {FIREBASE_CREDENTIAL_PATH}")
print(f"   File exists: {os.path.exists(FIREBASE_CREDENTIAL_PATH)}")

try:
    # Check if already initialized (for re-runs)
    if firebase_admin._apps:
        print("🔄 Firebase already initialized, reusing...")
        db = firestore.client()
    else:
        cred = credentials.Certificate(FIREBASE_CREDENTIAL_PATH)
        firebase_admin.initialize_app(cred)
        db = firestore.client()
    print("✅ Firebase Connected")
except Exception as e:
    print(f"⚠️ Firebase Connection Failed: {e}")
    print("Did you place the 'serviceAccountKey.json' file in this folder?")

# Initialize Gemini
genai.configure(api_key=GEMINI_API_KEY)
model = genai.GenerativeModel('gemini-pro-vision')

def process_pending_reports():
    print("🔍 Checking for Pending reports...")
    
    # 1. Fetch from Supabase
    response = supabase.table("reports_analysis").select("*").eq("status", "Pending").execute()
    reports = response.data

    if not reports:
        print("   No pending reports.")
        return

    print(f"   Found {len(reports)} reports to verify.")

    for report in reports:
        report_id = report['report_id']
        incident_type = report['incident_type']
        
        print(f"   👉 Processing {report_id} ({incident_type})...")

        # --- AI VERIFICATION LOGIC (Placeholder) ---
        # In real life: Download image_url -> Send to Gemini -> Get Result
        # For now, we Simulate "Verified" after 2 seconds
        time.sleep(1) 
        
        new_status = "Verified"
        confidence = 0.95
        reasoning = "AI detected a clear pothole in the image."
        # -------------------------------------------

        # 2. Update Supabase
        supabase.table("reports_analysis").update({
            "status": new_status,
            "verification_confidence": confidence,
            "ai_reasoning": reasoning
        }).eq("report_id", report_id).execute()

        # 3. Update Firestore (The Critical Step for the App!)
        if db is None:
            print("      ⚠️ Skipping Firestore sync (not connected)")
            continue
            
        try:
            # Normalize channel name from incident type
            channel_name = incident_type.replace("#", "").strip().lower()
            channel_name = channel_name.replace("_", "-").replace(" ", "-")
            
            # Update the specific document in Firestore
            # Path: reports/{channel}/threads/{reportId}
            doc_ref = db.collection("reports").document(channel_name).collection("threads").document(report_id)
            doc_ref.update({
                "status": new_status,
                "aiVerification": {
                    "verified": True,
                    "confidence": confidence,
                    "reason": reasoning
                }
            })
            print(f"      ✅ Synced to Firestore!")
            
        except Exception as e:
            print(f"      ❌ Failed to sync to Firestore: {e}")

def main():
    print("🤖 AI Worker Started. Press Ctrl+C to stop.")
    while True:
        try:
            process_pending_reports()
        except Exception as e:
            print(f"❌ Error in loop: {e}")
        
        # Wait 10 seconds before next check
        time.sleep(10)

if __name__ == "__main__":
    main()
