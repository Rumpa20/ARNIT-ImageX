# Cashfree backend starter (ARNIT ImageX)

These endpoints use Firebase Authentication ID tokens to identify users. The old `X-ImageX-User` identity header is not trusted.

## Endpoints
- `POST create-order.php`: JSON `plan` (monthly/yearly/lifetime), optional `phone`
- `POST verify.php`: JSON `order_id`
- `GET status.php`: returns the authenticated user's premium status
- `POST webhook.php`: configure in Cashfree dashboard

Every Android request must send:
`Authorization: Bearer <Firebase ID token>`

## Setup
1. Copy `config.example.php` to `config.php) on the server. Keep `config.php` out of Git and outside public access where possible.
2. Set server environment variables: `FIREBASE_PROJECT_ID`, `CASHFREE_APP_ID`, `CASHFREE_SECRET_KEY`, `CASHFREE_WEBHOOK_SECRET`, and `CASHFREE_ENV` (`sandbox` or `production`).
3. Run `composer install --no-dev --optimize-autoloader` in this directory and deploy `vendor/`.
4. Ensure PHP OpenSSL, cURL, and PDO SQLite extensions are available.
5. Set the Firebase project ID to the exact project ID used by Android Firebase Authentication.
6. Configure Cashfree webhook URL and use HTTPS only.

## Authentication verification
PHP verifies Firebase ID tokens using Firebase's published RS256 signing certificates, then validates the token's project audience, issuer, expiry, issued-at time, and subject. The verified Firebase UID is used as the account key. The backend does not accept client-supplied user IDs.

See `FIREBASE_AUTH_SETUP.md` for Firebase/Android setup.

## Before production
This backend is a starter and has not been fully integration-tested. Verify Cashfree API version/fields and webhook signature requirements against current official documentation, test all payment states in sandbox, and add rate limiting, refund/cancellation handling, reconciliation, and appropriate database backups. Monthly/yearly plans currently grant fixed-duration access, not recurring subscriptions. Do not enable production payments until the remaining review and tests are complete.
