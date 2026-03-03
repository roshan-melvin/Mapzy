import requests

with open("/home/rocroshan/Desktop/2026/Ram/DeepBlueS11/zwap/lib/python3.12/site-packages/sklearn/datasets/images/flower.jpg", "rb") as f:
    res = requests.post(
        "http://localhost:8001/api/v1/reports/ai-draft",
        data={"transcript": "reporting a pothole here"},
        files={"image": f}
    )
print(res.status_code)
try:
    print(res.json())
except:
    print(res.text)
