#!/bin/bash
nohup mvn spring-boot:run > app.log 2>&1 &
APP_PID=$!
echo "Waiting for app to start..."
while ! grep -q "Started AdaptiveBackendApplication" app.log; do
  sleep 1
done
echo "App started. Curling stream..."
curl -N -s http://localhost:8080/debug/ollama/stream > stream_output.log 2>&1
echo "Curl finished with exit code $?"
kill $APP_PID
cat stream_output.log
