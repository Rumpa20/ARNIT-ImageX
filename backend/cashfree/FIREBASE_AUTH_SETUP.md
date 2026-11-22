# Firebase Authentication setup (Android + PHP backend)

This backend authenticates requests using Firebase ID tokens.

## Firebase project
- Create or use a Firebase project.
- Add an Android app using ImageX's exact application ID.
- Enable a provider in Firebase Authentication (Google or Email/Password).
- Add the Android Firebase configuration file to the app module after configuration. Do not commit service-account keys.

## Hosting backend
- Set environment variable `FIREBASE_PROJECT_ID` to the Firebase Project ID.
- Run `composer install --no-dev --optimize-autoloader` in this directory.
- Deploy `vendor/` and ensure PHP OpenSSL, cURL, and PDO SQLite are enabled.
- Send requests over HTTPS with `Authorization: Bearer <Firebase ID token>`.

Android Firebase Auth signs the user in. The app requests an ID token and sends it to this backend. PHP validates RS256 signature against Firebase's published certificates, checks audience, issuer, expiry, issued-at, and subject. The verified subject becomes the user key. No client-supplied user ID header is trusted.

The old `X-ImageX-User` header is intentionally rejected.
