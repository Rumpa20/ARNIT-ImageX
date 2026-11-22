# Cashfree Sandbox Backend Deployment Checklist

This is a deployment guide for the existing Cashfree starter endpoints. It does **not** mean Android checkout is wired or real payments are ready.

## 1. Server requirements
- HTTPS-enabled PHP 8.1+ hosting
- PHP extensions: cURL, OpenSSL, PDO SQLite
- Composer available locally or on the server
- Firebase Authentication enabled in the Firebase project used by the Android app

## 2. Deploy the backend
Upload the contents of `backend/cashfree/` to a server location. Prefer a directory outside the public web root; if your host only supports public folders, block direct access to `config.php`, `private/`, and `vendor/` with server configuration.

Run:
```sh
composer install --no-dev --optimize-autoloader
```

Copy `config.example.php` to `config.php`. Keep the actual `config.php` out of Git. Set environment values:
- `FIREBASE_PROJECT_ID`
- `CASHFREE_APP_ID`
- `CASHFREE_SECRET_KEY`
- `CASHFREE_WEBHOOK_SECRET`
- `CASHFREE_ENV=sandbox`
- `IMAGEX_RETURN_URL` (replace the placeholder with a real HTTPS return page)

The private SQLite directory must be writable by PHP and not publicly accessible.

## 3. Firebase Auth
Enable the chosen sign-in provider in Firebase Authentication and add the exact Android application ID. Android requests must attach a Firebase ID token as `Authorization: Bearer <token>`. The backend validates the token and derives the UID; never accept a UID from the app as identity.

## 4. Cashfree sandbox
Use sandbox credentials only. Configure the webhook endpoint to the deployed `webhook.php` URL and copy the webhook secret into the server environment. Confirm the webhook can receive POST requests over HTTPS.

## 5. API endpoints in this starter
- `POST create-order.php`: JSON `{"plan":"monthly|yearly|lifetime"}`; requires Firebase Bearer token.
- `POST verify.php`: JSON `{"order_id":"ix_..."}`; requires Firebase Bearer token.
- `GET status.php`: requires Firebase Bearer token.
- `POST webhook.php`: Cashfree signed webhook.

## 6. Important limitations before live use
- Android Cashfree SDK/checkout flow is not yet implemented.
- The app currently has no network permission and payment methods are disabled.
- The backend starter has not been deployed or sandbox end-to-end tested.
- Monthly/yearly plans currently grant fixed 30/365-day access after a one-time payment; they are not auto-renewing subscriptions.
- Verify Cashfree API version, payloads, signature requirements, payment currency, webhook event names and edge cases against current official documentation.
- Add rate limits, stronger order reconciliation, refund/revocation handling, audit logs, database backups, and monitoring before production.
- Never enable production credentials until sandbox tests pass and legal/policy review is complete.

## Sandbox test checklist
1. Sign in through Firebase in the app (after UI integration is complete).
2. Create an order; confirm server-generated amount and currency.
3. Complete a Cashfree sandbox payment.
4. Verify order through the backend, then confirm Premium is enabled.
5. Test failed, cancelled, pending, duplicate webhook, wrong user, wrong amount, and expired entitlement cases.
6. Confirm no Premium unlock occurs from a return URL or client-side callback alone.
