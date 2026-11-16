---
name: paystack-subscriptions
description: >-
  Paystack subscription and payment integration skill for amaMomoApp. Use when
  implementing subscription initialization, Paystack Checkout handoff, webhook
  verification, transaction verification, or recurring billing logic.
---

# Paystack Subscriptions (amaMomoApp)

Implement secure, server-verified Paystack subscription workflows for `amaMomoApp`.

## Core Flow

1. **Backend Initializes Payment:** Android requests subscription initialization from the Node.js/Express backend; backend calls Paystack `transaction/initialize` using `PAYSTACK_SECRET_KEY` and returns the `authorization_url` / `reference` to Android.
2. **Android Opens Checkout:** Android opens Paystack Checkout (e.g., Custom Tabs / WebView) and waits for completion.
3. **Webhook & Server Verification:**
   - Backend exposes a Paystack webhook endpoint and verifies the `x-paystack-signature` HMAC-SHA512 header against the raw request body using `PAYSTACK_SECRET_KEY`.
   - Backend also verifies the transaction via Paystack `transaction/verify/:reference` before activating or extending a subscription.
4. **Recurring Billing:** Use automatic recurring billing only when the selected Paystack payment method returns an eligible reusable authorization.

## Security Rules

- **Never** expose `PAYSTACK_SECRET_KEY` in Android source code, Gradle configs, or client-side bundles.
- **Never** trust client-only payment success callbacks without backend verification.
- Make webhook handlers idempotent so duplicate Paystack webhook deliveries do not double-extend subscriptions.
