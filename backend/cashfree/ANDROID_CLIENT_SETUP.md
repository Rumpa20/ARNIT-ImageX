# Cashfree Android Backend Client (Integration Step 1)

A Firebase-authenticated Android client wrapper has been added for the backend starter. It exposes create-order, verify-order, and premium-status calls. It does **not** launch the Cashfree SDK checkout, and does not grant Premium by itself.

## Build configuration
Set the app-level Gradle property `CASHFREE_BACKEND_URL` to the HTTPS base directory where the PHP endpoints are deployed, for example `https://your-host.example/payments/cashfree`. Do not include credentials in this URL.

Until the URL is configured and the backend is deployed, requests fail safely with a clear configuration message.

## API
- `createOrder(plan, phone?)`: Firebase ID token auth; backend sets plan price.
- `verifyOrder(orderId)`: backend verifies Cashfree order and payment server-side.
- `premiumStatus()`: reads server entitlement.

## Remaining integration
- Add Cashfree SDK checkout launch and Activity callback flow.
- Add authenticated sign-in UI/flow if the app does not yet provide it.
- Map verified server entitlement into PremiumRepository (not implemented by this step).
- Configure the HTTPS backend URL and test all sandbox states.
