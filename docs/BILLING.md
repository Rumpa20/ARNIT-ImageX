# Billing Integration (Google Play Billing)

This build ships a complete billing **architecture** with a clearly marked
**test-mode gateway**. It never contacts Google Play and never fakes a real
purchase: Premium can only be exercised through the explicit developer toggle
in **Settings → Billing test mode**, and the whole app is labelled
"TEST MODE" while it is on.

## Current architecture

```
billing/BillingGateway.kt          the contract (PlanId, BillingState, gateway interface)
billing/TestModeBillingGateway.kt  test implementation wired by ServiceLocator
premium/PremiumRepository.kt       centralized entitlement + feature gating
ui/screens/PremiumScreen.kt        plans, comparison, restore purchases
ui/AppViewModel.kt                 purchase / restore / toggleTestMode entry points
```

`BillingGateway` exposes:

- `state: StateFlow<BillingState>` - `configured`, `testMode`, `unlocked`,
  `plan`, `products`, `message`
- `suspend purchase(plan: PlanId): BillingState`
- `suspend restore(): BillingState`

`PremiumRepository` collects that state and answers every gating question the
UI has (`outputAllowed`, `inputAllowed`, `qualityAllowed`, `limits()`), so a
real billing implementation never has to touch screens or the engine.

## Swapping in real Google Play Billing

1. Add the dependency in `app/build.gradle.kts`:
   `implementation("com.android.billingclient:billing-ktx:7.1.1")`
2. Add the `com.android.vending.BILLING` permission to the manifest.
3. Create the products in Play Console:
   - subscription `premium_monthly`
   - subscription `premium_yearly` (optionally with an offer)
   - in-app product `premium_lifetime`
4. Implement `BillingGateway` in a new file, e.g.
   `billing/PlayBillingGateway.kt`:

   - In `startConnection`/`onBillingSetupFinished`: query product details for
     the three product IDs, map them to `BillingProductInfo`, and set
     `configured = true`.
   - In `purchase(plan)`: map the plan to its product, build
     `BillingFlowParams`, launch `launchBillingFlow`, and acknowledge/persist
     the purchase from `onPurchasesUpdated`. Persist the plan name through
     `SettingsRepository.setPremiumPlan(...)` (keys `premium_plan` /
     `premium_test_state`) or your own secure storage, and emit the new
     `BillingState`.
   - In `restore()`: call `queryPurchasesAsync`, verify the signature
     server-side or with Play's `PurchasesUpdatedListener` acknowledgement
     flow, then re-emit `BillingState` with `unlocked = true` and the plan.
5. Replace one line in `di/ServiceLocator.kt`:

   ```kotlin
   fun billing(context: Context): BillingGateway =
       billingCache ?: PlayBillingGateway(context.applicationContext).also { billingCache = it }
   ```

6. Keep the test toggle: gate `TestModeBillingGateway` behind
   `BuildConfig.DEBUG` when you ship, or remove
   `ui/screens/SettingsScreen.kt`'s "Billing test mode" card entirely.

## Rules this architecture enforces

- No purchase state is invented. If billing is not configured, `unlocked`
  stays `false` and purchase buttons stay disabled with an honest message.
- Entitlement checks live only in `PremiumRepository`, so free-tier behavior
  (formats quality, sizes) is defined in exactly one place.
- The billing layer never sees image data.
