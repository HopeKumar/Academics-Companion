#!/bin/bash

echo "Logging in..."
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser", "password":"password123"}' | jq -r '.accessToken')

echo -e "\n=== Testing Slides ==="
curl -s -X POST http://localhost:8080/slides/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "Photosynthesis"}' | jq .

echo -e "\n=== Testing Podcasts ==="
PODCAST_ID=$(curl -s -X POST http://localhost:8080/podcasts/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "The mitochondria"}' | jq -r '.id')
echo "Podcast ID: $PODCAST_ID"

sleep 15
echo -e "\n=== Checking Podcast Status ==="
curl -s -X GET http://localhost:8080/podcasts/status/$PODCAST_ID \
  -H "Authorization: Bearer $TOKEN" | jq .

