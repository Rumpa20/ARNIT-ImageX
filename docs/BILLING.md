# Billing Integration

ARNIT ImageX currently ships a test-mode billing gateway only. It does not initiate real Google Play or Cashfree payments and never fabricates a paid entitlement.

For the agreed two payment channels (Google Play for Play distribution and Cashfree for direct APK distribution), see [DUAL_PAYMENT_SETUP.md](DUAL_PAYMENT_SETUP.md).

## Current architecture

```
billing/BillingGateway.kt          contract (PlanId, BillingState, gateway interface)
billing/TestModeBillingGateway.kt test implementation wired by ServiceLocator
premium/PremiumRepository.kt       centralized entitlement + feature gating
ui/screens/PremiumScreen.kt       plans, comparison, restore purchases
ui/AppViewModel.kt               purchase / restore / toggleTestMode entry points
```

`BillingGateway` exposes:
- `state: StateFlow<BillingState>` - configured, testMode, unlocked, plan, products, message
- `suspend purchase(plan: PlanId): BillingState`
- `suspend restore(): BillingState`

`PremiumRepository` collects this state and handles feature gating (formats, quality, size and limits).

## Google Play Billing integration

Use the current Google Play Billing Library (9.1.0 at the time this document was updated). Before release:
1. Create product IDs in Play Console: `premium_monthly`, `premium_yearly`, `premium_lifetime`.
2. Integrate BillingClient, query product details, launch purchase flows, process pending purchases, acknowledge purchases and restore.
3. Verify purchase tokens on a secure backend and reconcile subscription status.
4. Do not unlock Premium based only on a client callback.
5. Use Play Store test tracks and license testers before production.

## Cashfree integration

Cashfree is intended for direct APK distribution, with the app communicating to an HTTPS backend. Create orders on the backend, launch Cashfree checkout with the returned session, then verify payment status server-side. Never embed Cashfree App Secret in the APK. See the dual-payment setup document for the required API endpoints and subscription lifecycle handling.

## Alternative billing in Play-distributed apps

Do not present Cashfree as an in-app alternative in Play Store distribution until the app is eligible, enrolled, and fully integrated with the applicable Google alternative billing APIs, user choice screen, reporting, disclosures and policy requirements.

## Current status

- Real Google Play Billing: not integrated.
- Cashfree Android checkout: not integrated.
- Backend order creation, verification, webhooks, entitlements: not deployed.
- Test-mode gateway: available for debug testing only.

No payment is taken and no real Premium purchase can currently be made.
