#!/bin/bash

echo "Logging in..."
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser", "password":"password123"}' | jq -r '.accessToken')

echo -e "\n=== Testing Chat Stream ==="
curl -s -X POST http://localhost:8080/chat/stream \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"sessionId": "test-session", "content": "What is ATP?", "sourceIds": []}'

echo -e "\n\n=== Testing Mindmaps ==="
curl -s -X POST http://localhost:8080/mindmaps/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "ATP"}' | jq .

echo -e "\n=== Testing Podcasts ==="
PODCAST_ID=$(curl -s -X POST http://localhost:8080/podcasts/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "The mitochondria"}' | jq -r '.data')
echo "Podcast ID: $PODCAST_ID"

sleep 10
echo -e "\n=== Checking Podcast Status ==="
curl -s -X GET http://localhost:8080/podcasts/$PODCAST_ID/status \
  -H "Authorization: Bearer $TOKEN" | jq .

