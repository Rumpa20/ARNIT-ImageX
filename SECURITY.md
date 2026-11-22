# Security Policy

## Reporting a vulnerability

Please do not publish security vulnerabilities, leaked credentials, payment issues, or private user data in public issues.

Report security concerns privately to the repository maintainer through GitHub's private vulnerability reporting feature (Security tab, if enabled) or direct private contact.

## Secret-handling rules

- Never commit Cashfree app secrets, webhook secrets, production credentials, private keys, signing keystores, passwords, or database/customer data.
- Server configuration such as `backend/cashfree/config.php` must stay outside version control and preferably outside the web root.
- `google-services.json` is Android/Firebase client configuration, not a server secret. It may be committed, but restrict Firebase API keys where applicable and configure Firebase security rules.
- If a secret is accidentally committed, deleting the file is not enough. Rotate/revoke the secret immediately and then clean Git history if necessary.
- Payment status and Premium entitlements must be verified by the backend. Never trust client-side payment success or user IDs sent in headers.

## Deployment status

This repository contains starter payment/authentication code. Do not use it for production payments until Firebase token verification, Cashfree webhook/signature verification, order ownership, rate limiting, idempotency, private storage, and end-to-end tests have been reviewed and validated.
