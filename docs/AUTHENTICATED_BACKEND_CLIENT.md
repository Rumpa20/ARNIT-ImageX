# Authenticated backend client (Android)

The Android client calls the deployed PHP backend at:
`https://abruar.online/imagex/cashfree/`

All calls require a Firebase ID token in `Authorization: Bearer` and the server verifies the token before returning user-specific data.

## Implemented calls
- `GET status.php`: fetches premium entitlement for the signed-in Firebase UID.
- `POST create-order.php`: creates a Cashfree payment order for monthly/yearly/lifetime plans. It returns a payment session ID but does not launch checkout or mark the user premium.

## Privacy
This client sends only the plan selection and optional phone number to create an order. It does not send image bytes, image metadata, or conversion files. Image conversion stays local.

## Server prerequisites
Deploy the PHP backend and its Composer `vendor/` directory, configure `FIREBASE_PROJECT_ID` to match the Android Firebase project, and keep Cashfree secrets on the server. Verify the endpoint URLs and sandbox configuration before testing. Do not enable production payment credentials until sandbox testing is complete.
