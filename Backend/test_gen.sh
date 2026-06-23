#!/bin/bash

TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser", "password":"password123"}' | jq -r '.accessToken')

echo -e "\n=== Testing Flashcards ==="
curl -s -X POST http://localhost:8080/flashcards/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "Photosynthesis"}' | jq .

echo -e "\n=== Testing Slides ==="
curl -s -X POST http://localhost:8080/slides/generate \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"topic": "Cellular Respiration"}' | jq .

