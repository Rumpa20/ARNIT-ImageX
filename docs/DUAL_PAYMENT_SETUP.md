# ARNIT ImageX Dual Payment Setup

This document defines the two payment options requested for ARNIT ImageX. The current app is intentionally offline and has no INTERNET permission, so this is a payment integration plan, not a claim that checkout is live.

## Payment options

### 1. Google Play Billing (Play Store distribution)
- Monthly subscription: `premium_monthly`
- Yearly subscription: `premium_yearly`
- Lifetime one-time product: `premium_lifetime`
- Checkout, purchase restore, pending purchase state, acknowledgement, and server-side entitlement verification.
- Use Google Play Billing Library 9.1.0, following the current Android Developers integration guide.
- Product prices must be fetched from Play, never hardcoded.

### 2. Cashfree Checkout (direct APK distribution)
- Monthly, yearly, and lifetime plans.
- The app requests an order/session from our backend.
- Cashfree Checkout opens using the session returned by the backend.
- The backend verifies the order/payment status with Cashfree before granting entitlement.
- Do not embed Cashfree App Secret in the Android APK.
- Subscription plans must use a supported Cashfree subscription/recurring setup; do not simulate recurring charges as one-time payments.

## Play Store builds and alternative billing

For India, alternative billing is a regulated/Play program integration. Do not show Cashfree as a payment choice in a Play Store build until the app is enrolled and the required Alternative Billing APIs, reporting, disclosures, and compliance requirements are completed. Direct Cashfree checkout is intended for the direct APK distribution flow unless program eligibility and compliance have been verified.

## Required backend API (Cashfree)

The backend must be hosted on HTTPS and should expose:

- `POST /api/payments/cashfree/create-order`: authenticated or securely scoped request; server maps plan to amount; creates Cashfree order; returns order ID + payment session ID.
- `POST /api/payments/cashfree/verify`: accepts order ID; backend calls Cashfree order/payment API; grants entitlement only after authoritative paid status.
- `POST /api/payments/cashfree/webhook`: validates Cashfree webhook signature using raw body and secret; idempotently processes event; never trusts app callback alone.
- `GET /api/premium/status`: returns current entitlement and expiration/lifetime status.
- Subscription renewal/cancellation/expiry webhooks and reconciliation jobs for monthly/yearly entitlements.

Amounts and product plan mapping must be fixed on the server, not accepted from the app. Add idempotency, rate limiting, order/user association, expiry, audit trail, and refund/revocation handling.

## App integration sequence

1. Keep conversion and image processing offline and local.
2. Add a payment-method selection screen only when both methods are configured for the current distribution channel.
3. For Play Store distribution, integrate Play Billing and server verification.
4. For direct APK distribution, enable Cashfree only after the backend HTTPS endpoints are deployed and tested in sandbox.
5. Do not unlock Premium on redirect, client callback, or test-mode signal alone.
6. Hide unavailable payment methods and show a clear message if billing is not configured.
7. Never put gateway secrets, private keys, or server credentials in source code, Gradle config, or the APK.

## Setup checklist

- [ ] Play Console account and payments profile
- [ ] Play products created and reviewed: monthly/yearly subscriptions + lifetime one-time purchase
- [ ] Google Play Billing backend verification and acknowledgement
- [ ] Cashfree merchant account activated
- [ ] Cashfree sandbox credentials kept server-side
- [ ] HTTPS backend deployed
- [ ] Cashfree sandbox create-order / checkout / verify / webhook tested
- [ ] Subscription expiry, renewals, cancellation, refunds tested
- [ ] Play alternative-billing enrollment and compliance verified before offering Cashfree inside Play-distributed app
- [ ] Privacy policy updated to explain payment data processing
- [ ] App INTERNET permission and privacy copy updated only as part of the actual payment integration

## Important current status

The repository currently has a test-mode billing gateway only. Real Google Play and Cashfree checkout are **not yet active**. This document is the agreed implementation architecture; a backend, merchant account, product configuration, and payment integration work remain necessary before real payments can be accepted.
