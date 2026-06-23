#!/bin/bash

echo "Logging in..."
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser", "password":"password123"}' | jq -r '.accessToken')

echo "Got token: ${TOKEN:0:10}..."

echo -e "\n=== Testing /health/ai ==="
curl -s -X GET http://localhost:8080/health/ai \
  -H "Authorization: Bearer $TOKEN" | jq .

echo -e "\n=== Testing /admin/reindex ==="
curl -s -X POST http://localhost:8080/admin/reindex \
  -H "Authorization: Bearer $TOKEN" | jq .

