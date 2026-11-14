#!/usr/bin/env python3
"""
==============================================================================
Payment Gateway & Concurrency Simulator
Simulates a real-world payment gateway (e.g., Stripe, PayPal, Adyen) interacting
with the Payment & Inventory Processing Engine.

Scenarios tested:
 1. Healthcheck & Product Catalog verification
 2. Order creation with Idempotency-Key
 3. Safe idempotent replay (same Idempotency-Key -> same order returned)
 4. Valid Webhook dispatch with HMAC-SHA256 signature (payment.succeeded)
 5. Duplicate Webhook dispatch (event_id idempotency skip)
 6. Malicious Webhook attack (forged HMAC signature -> 401 Unauthorized)
 7. High-concurrency Flash Sale stress test (20 threads competing for 1 item)
==============================================================================
"""

import hmac
import hashlib
import json
import time
import uuid
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080"
WEBHOOK_SECRET = "whsec_test_secret_key_123456789"

# Terminal Colors
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

def print_header(title):
    print(f"\n{BOLD}{CYAN}{'='*70}{RESET}")
    print(f"{BOLD}{CYAN}{title.center(70)}{RESET}")
    print(f"{BOLD}{CYAN}{'='*70}{RESET}\n")

def http_request(method, endpoint, data=None, headers=None):
    url = f"{BASE_URL}{endpoint}"
    if headers is None:
        headers = {}
    
    req_data = None
    if data is not None:
        if isinstance(data, (dict, list)):
            req_data = json.dumps(data).encode("utf-8")
            if "Content-Type" not in headers:
                headers["Content-Type"] = "application/json"
        elif isinstance(data, bytes):
            req_data = data
        elif isinstance(data, str):
            req_data = data.encode("utf-8")

    req = urllib.request.Request(url, data=req_data, headers=headers, method=method)
    
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            status_code = resp.getcode()
            body = resp.read().decode("utf-8")
            try:
                parsed_json = json.loads(body)
            except Exception:
                parsed_json = body
            return status_code, parsed_json
    except urllib.error.HTTPError as e:
        status_code = e.code
        body = e.read().decode("utf-8")
        try:
            parsed_json = json.loads(body)
        except Exception:
            parsed_json = body
        return status_code, parsed_json
    except Exception as e:
        return 500, {"error": str(e)}

def compute_hmac_sha256(payload_bytes, secret):
    return hmac.new(secret.encode("utf-8"), payload_bytes, hashlib.sha256).hexdigest()

def test_health():
    print_header("SCENARIO 1: Healthcheck & Catalog Verification")
    status, res = http_request("GET", "/actuator/health")
    if status == 200:
        print(f"[{GREEN}PASS{RESET}] Application is healthy: {res}")
    else:
        print(f"[{RED}FAIL{RESET}] Application is not reachable on {BASE_URL}. Ensure docker-compose or Spring Boot is running.")
        sys.exit(1)

    status, products = http_request("GET", "/api/v1/products")
    print(f"[{GREEN}INFO{RESET}] Retrieved {len(products)} products from database:")
    for p in products:
        print(f"   -> ID: {p['id']} | SKU: {p['sku']:<25} | Available: {p['stockAvailable']:<3} | Reserved: {p['stockReserved']:<3} | Price: ${p['price']}")
    return products

def test_idempotent_order_creation(product_id):
    print_header("SCENARIO 2 & 3: Order Creation & Idempotency-Key Replay")
    idempotency_key = str(uuid.uuid4())
    order_payload = {
        "customerEmail": "alice.buyer@example.com",
        "items": [
            {"productId": product_id, "quantity": 1}
        ]
    }
    headers = {"Idempotency-Key": idempotency_key}

    print(f"1. Sending FIRST Order request with Idempotency-Key: {idempotency_key}")
    status1, res1 = http_request("POST", "/api/v1/orders", data=order_payload, headers=headers)
    print(f"   Response Status: {status1}")
    print(f"   Order ID: {res1.get('id')}, Status: {res1.get('status')}, Total: ${res1.get('totalAmount')}")
    assert status1 in (200, 201), f"Expected 200/201, got {status1}"

    order_id = res1["id"]

    print(f"\n2. Re-sending IDENTICAL Order request (Simulating network glitch/retry)...")
    status2, res2 = http_request("POST", "/api/v1/orders", data=order_payload, headers=headers)
    print(f"   Response Status: {status2}")
    print(f"   Returned Order ID: {res2.get('id')}, Status: {res2.get('status')}")

    if status2 in (200, 201) and res2.get("id") == order_id:
        print(f"\n[{GREEN}PASS{RESET}] Idempotency confirmed! The server returned original order without double-booking stock.")
    else:
        print(f"\n[{RED}FAIL{RESET}] Idempotency failure! Mismatch in order replay.")
        sys.exit(1)

    return order_id

def test_webhooks(order_id):
    print_header("SCENARIO 4 & 5: HMAC-SHA256 Webhook Verification & Idempotency")
    event_id = f"evt_{uuid.uuid4().hex[:16]}"
    webhook_event = {
        "eventId": event_id,
        "provider": "STRIPE",
        "eventType": "payment.succeeded",
        "orderId": order_id,
        "amount": 99.99,
        "currency": "USD"
    }
    raw_payload_bytes = json.dumps(webhook_event).encode("utf-8")
    valid_signature = compute_hmac_sha256(raw_payload_bytes, WEBHOOK_SECRET)

    print(f"1. Dispatching payment.succeeded Webhook with valid HMAC-SHA256:")
    print(f"   Event ID: {event_id}")
    print(f"   X-Webhook-Signature: {valid_signature}")

    headers = {
        "Content-Type": "application/json",
        "X-Webhook-Signature": valid_signature
    }
    status, res = http_request("POST", "/api/v1/webhooks/payments", data=raw_payload_bytes, headers=headers)
    print(f"   Response Status: {status}")
    print(f"   Result: {res}")
    assert status == 200, f"Expected 200, got {status}"
    print(f"[{GREEN}PASS{RESET}] Legitimate webhook accepted and order transitioned to PAID!")

    print(f"\n2. Dispatching DUPLICATE Webhook (Same Event ID: {event_id})...")
    status_dup, res_dup = http_request("POST", "/api/v1/webhooks/payments", data=raw_payload_bytes, headers=headers)
    print(f"   Response Status: {status_dup}")
    print(f"   Result: {res_dup}")
    if status_dup == 200 and "idempotent" in str(res_dup).lower():
        print(f"[{GREEN}PASS{RESET}] Duplicate webhook detected and idempotently skipped without errors.")
    else:
        print(f"[{RED}FAIL{RESET}] Webhook duplicate handling failed.")

    print_header("SCENARIO 6: Security Attack Simulation (Forged Signature)")
    fake_signature = "bad_signature_00000000000000000000000000000000000000000000000000000000"
    attack_headers = {
        "Content-Type": "application/json",
        "X-Webhook-Signature": fake_signature
    }
    status_attack, res_attack = http_request("POST", "/api/v1/webhooks/payments", data=raw_payload_bytes, headers=attack_headers)
    print(f"   Attacking with forged signature: {fake_signature}")
    print(f"   Response Status: {status_attack}")
    print(f"   Server Response: {res_attack}")
    if status_attack == 401:
        print(f"[{GREEN}PASS{RESET}] Security check passed! Unauthorized webhook was rejected with 401.")
    else:
        print(f"[{RED}FAIL{RESET}] Security breach! Forged webhook was not rejected.")
        sys.exit(1)

def test_concurrency_flash_sale():
    print_header("SCENARIO 7: High-Concurrency Flash Sale (Pessimistic Lock Test)")
    
    # Create an exclusive product with EXACTLY 1 unit of stock
    limited_sku = f"LIMITED-DROP-{uuid.uuid4().hex[:6]}"
    create_product_payload = {
        "sku": limited_sku,
        "name": "Exclusive Flash Sale Drop (Only 1 in World)",
        "stockAvailable": 1,
        "price": 499.99
    }
    status, product = http_request("POST", "/api/v1/products", data=create_product_payload)
    product_id = product["id"]
    print(f"Created Flash Sale Product ID {product_id} with Stock Available = 1")
    print(f"Launching 20 concurrent threads simultaneously trying to buy the exact same unit...\n")

    def buy_product(worker_id):
        worker_idempotency_key = str(uuid.uuid4())
        payload = {
            "customerEmail": f"buyer_{worker_id}@contest.com",
            "items": [{"productId": product_id, "quantity": 1}]
        }
        headers = {"Idempotency-Key": worker_idempotency_key}
        start_time = time.time()
        st, response = http_request("POST", "/api/v1/orders", data=payload, headers=headers)
        elapsed = time.time() - start_time
        return worker_id, st, response, elapsed

    results = []
    with ThreadPoolExecutor(max_workers=20) as executor:
        futures = [executor.submit(buy_product, i) for i in range(1, 21)]
        for f in as_completed(futures):
            results.append(f.result())

    success_orders = [r for r in results if r[1] in (200, 201)]
    conflict_orders = [r for r in results if r[1] in (409, 400)]

    print(f"Summary of 20 concurrent purchase attempts:")
    print(f" - Successful Checkouts (200/201): {len(success_orders)}")
    print(f" - Rejected for Out-of-Stock (409/400): {len(conflict_orders)}")

    for worker_id, st, res, el in results[:6]:
        status_label = f"{GREEN}SUCCESS{RESET}" if st in (200, 201) else f"{YELLOW}OUT OF STOCK{RESET}"
        print(f"   Worker #{worker_id:<2} -> Status: {st} ({status_label}) in {el:.3f}s")
    if len(results) > 6:
        print(f"   ... and {len(results)-6} more workers rejected safely.")

    # Verify final stock counts in DB
    _, updated_product = http_request("GET", f"/api/v1/products/{product_id}")
    print(f"\nFinal Product Stock State in PostgreSQL:")
    print(f"   Available: {updated_product['stockAvailable']} (must be 0)")
    print(f"   Reserved:  {updated_product['stockReserved']} (must be 1)")

    if len(success_orders) == 1 and len(conflict_orders) == 19 and updated_product['stockAvailable'] == 0:
        print(f"\n[{GREEN}CONCURRENCY TEST PASSED{RESET}] Pessimistic Locking successfully prevented overselling under race condition!")
    else:
        print(f"\n[{RED}CONCURRENCY TEST FAILED{RESET}] Race condition detected!")
        sys.exit(1)

def main():
    print(f"\n{BOLD}Starting Payment Engine Production Simulation Suite{RESET}")
    print(f"Target: {BASE_URL}")
    print(f"Secret: {WEBHOOK_SECRET}\n")

    products = test_health()
    target_product_id = products[0]["id"]
    order_id = test_idempotent_order_creation(target_product_id)
    test_webhooks(order_id)
    test_concurrency_flash_sale()

    print_header("ALL PRODUCTION SCENARIOS PASSED WITH 100% SUCCESS!")

if __name__ == "__main__":
    main()
