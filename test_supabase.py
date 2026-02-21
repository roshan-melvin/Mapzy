import os
from supabase import create_client

supabase = create_client(os.environ["SUPABASE_URL"], os.environ["SUPABASE_KEY"])
res = supabase.table("user_trust_scores").select("*").execute()
print(res.data)
