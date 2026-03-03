#!/bin/bash
# Start the assistant backend with the correct virtualenv
cd "$(dirname "$0")"
source ../zwap/bin/activate
echo "✅ NumPy: $(python -c 'import numpy; print(numpy.__version__)')"
echo "✅ Starting Assistant Backend on port 8001..."
python main.py
