# Phase 1: Cashfree Sandbox

This phase is sandbox-only. Do not use production credentials or switch to production.

## Backend deployment
1. Upload the entire `backend/cashfree/` folder to an HTTPS server path, for example `https://abruar.online/imagex/cashfree/`.
2. Copy `config.example.php` to `config.php` on the server. Keep `config.php`, `private/`, and the SQLite database inaccessible to public downloads.
3. Install dependencies with `composer install --no-dev --optimize-autoloader` and deploy `vendor/`.
4. Set server environment values:
   - `FIREBASE_PROJECT_ID`: the exact Android Firebase Auth project ID
   - `CASHFREE_APP_ID` and `CASHFREE_SECRET_KEY`: sandbox credentials only
   - `CASHFREE_WEBHOOK_SECRET`: sandbox webhook secret
   - `CASHFREE_ENV=sandbox`
5. Set `IMAGEX_RETURN_URL` to `https://abruar.online/imagex/payment-return.php` (create this return page before live sandbox testing).
6. Configure the Cashfree sandbox webhook to point to `webhook.php`; use HTTPS.
7. Check `status.php`, `plans.php`, `create-order.php`, and `verify.php` using authenticated requests before testing checkout.

## Android build configuration
Pass the public backend URL (not a credential) as Gradle project property:
`CASHFREE_BACKEND_URL=https://abruar.online/imagex/cashfree`
Do not add Cashfree keys to Android, Git, Gradle properties committed to source, or APK.

## Sandbox test sequence
- Sign in using Firebase Auth.
- Confirm plans load from `plans.php` and match server-side prices.
- Create a sandbox order; verify order amount is controlled by server configuration.
- Complete sandbox checkout.
- Verify payment with `verify.php`; Premium must only activate after authoritative paid status.
- Test cancel, failed, pending, and mismatched amount/order cases.
- Confirm webhook signature validation and idempotency.
- Confirm status/expiry behavior for monthly, yearly, and lifetime.

## Signing/update safety
Phase 1 must not change application ID, keystore, signing configuration, or the Android CI signing workflow. Keep APK updates signed with the same existing key. This branch does not introduce or rotate any signing material.
