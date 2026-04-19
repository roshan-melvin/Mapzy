import requests
import sys

res = requests.post("http://localhost:8001/api/v1/reports/ai-draft", data={"transcript": "speed camera on the highway"}, files={"image": open("../backend/test_images/test.jpg", "rb")})
print(res.status_code)
print(res.json())
