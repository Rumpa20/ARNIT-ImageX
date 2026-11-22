# Cashfree backend starter (ARNIT ImageX)

Starter endpoints:
- POST create-order.php: JSON plan (monthly/yearly/lifetime), phone optional
- POST verify.php: JSON order_id
- GET status.php
- POST webhook.php (configure Cashfree webhook URL)

## Setup
1. Copy config.example.php to config.php on hosting.
2. Set server environment variables CASHFREE_APP_ID, CASHFREE_SECRET_KEY, CASHFREE_WEBHOOK_SECRET, CASHFREE_ENV (sandbox/production).
3. Adjust plan amounts before testing.
4. Use HTTPS only.

## Security blockers before production
This is a starter, NOT production ready. It uses the temporary X-ImageX-User header for user identity; clients can spoof it. Replace with verified authentication (Firebase/Auth JWT or another signed identity) and validate tokens server-side. Move SQLite/private data outside web root and deny public access to config/storage. Review Cashfree webhook signature against their official current verification docs and test with sandbox payloads. Add rate limits, event idempotency, refunds/cancellations, subscription renewal/expiry and reconciliation. Monthly/yearly here are fixed duration entitlements, not true recurring subscriptions. Do not enable production payments until these are completed.

Cashfree API version/field requirements should be verified for your merchant account and current docs before sandbox execution. Never place merchant secrets in the Android app.
