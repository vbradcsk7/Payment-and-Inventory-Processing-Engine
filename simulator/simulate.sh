#!/usr/bin/env bash
# ==============================================================================
# Quick Terminal Webhook Simulator using OpenSSL and cURL
# ==============================================================================

set -e

BASE_URL="http://localhost:8080"
SECRET="whsec_test_secret_key_123456789"

echo "=== 1. Checking API Health ==="
curl -s -f "$BASE_URL/actuator/health" | grep "UP" >/dev/null && echo "Engine is UP!"

echo -e "\n=== 2. Creating an Order with Idempotency-Key ==="
IDEMPOTENCY_KEY=$(uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid)
ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $IDEMPOTENCY_KEY" \
  -d '{
    "customerEmail": "terminal.shopper@example.com",
    "items": [{"productId": 1, "quantity": 1}]
  }')

echo "Order Response: $ORDER_RESPONSE"
ORDER_ID=$(echo "$ORDER_RESPONSE" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Created Order ID: $ORDER_ID"

echo -e "\n=== 3. Testing HMAC-SHA256 Signed Webhook ==="
EVENT_ID="evt_$(date +%s)"
PAYLOAD=$(cat <<EOF
{"eventId":"$EVENT_ID","provider":"STRIPE","eventType":"payment.succeeded","orderId":"$ORDER_ID","amount":999.99,"currency":"USD"}
EOF
)

# Calculate HMAC-SHA256 signature with openssl
SIGNATURE=$(printf "%s" "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')
echo "Calculated HMAC-SHA256: $SIGNATURE"

WEBHOOK_RESPONSE=$(curl -s -w "\nHTTP Status: %{http_code}\n" -X POST "$BASE_URL/api/v1/webhooks/payments" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIGNATURE" \
  -d "$PAYLOAD")

echo "$WEBHOOK_RESPONSE"

echo -e "\n=== 4. Testing Attack with Invalid Signature ==="
curl -s -w "\nHTTP Status: %{http_code}\n" -X POST "$BASE_URL/api/v1/webhooks/payments" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: 0000000000000000000000000000000000000000000000000000000000000000" \
  -d "$PAYLOAD"
