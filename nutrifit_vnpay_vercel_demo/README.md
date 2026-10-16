# NutriFit VNPAY Sandbox on Vercel + Neon (PostgreSQL)

**This ZIP is specifically for Vercel.** Unlike the older SQLite version, it uses external PostgreSQL so that transaction records persist when serverless functions restart.

> This is a Sandbox demo. It does **not** activate a real user's Premium account yet and should not be used as-is for real payments.

## 1. Create a database

1. Open https://vercel.com/marketplace/neon to install Neon and connect it to your Vercel project, or create a Neon database directly at https://neon.tech.
2. Copy the **PostgreSQL connection string** (typically named `DATABASE_URL`); keep it private.
3. Open Neon Console > SQL Editor. Paste the content of `schema.sql` and run it **once** to create the `orders` table.

## 2. Deployment via GitHub (recommended)

1. Create a new GitHub repository, upload the **contents** of this folder to the repository root, including `app.py`, `requirements.txt`, and `templates/`. Do not upload `.env`, `.venv`, or a local database.
2. Open https://vercel.com/new, import your repository, set **Root Directory** to the folder containing `app.py` if needed. Let Vercel auto-detect Flask (no `vercel.json` needed).
3. In Project > Settings > Environment Variables, set these exact 5 variables, applying to **Production**:

   - `VNPAY_TMN_CODE`: your Sandbox merchant code
   - `VNPAY_HASH_SECRET`: your Sandbox merchant secret
   - `PUBLIC_BASE_URL`: your stable Production HTTPS domain, e.g. `https://nutrifit-payment.vercel.app` (NO trailing slash)
   - `FLASK_SECRET_KEY`: random long hexadecimal string. Generate: `py -c "import secrets; print(secrets.token_hex(32))"`
   - `DATABASE_URL`: Neon PostgreSQL connection string, e.g. `postgresql://USER:PASSWORD@HOST/neondb?sslmode=require`

   If Neon automatically injected `DATABASE_URL`, do not replace it unless it is wrong.
4. After setting/updating variables, **redeploy Production**. Changing environment variables does not update an already-built deployment.
5. Confirm opening `https://YOUR-PROJECT.vercel.app/` displays the Premium form.

## 3. Set up VNPAY callback addresses

Assuming your public domain is `https://nutrifit-payment.vercel.app`:

- Return URL: `https://nutrifit-payment.vercel.app/vnpay/return`
- IPN URL: `https://nutrifit-payment.vercel.app/vnpay/ipn`

Return URL is set automatically by this Flask app using `PUBLIC_BASE_URL` and `vnp_ReturnUrl`. **You still must register/configure the IPN URL with VNPAY** for your merchant credentials (ask VNPAY support/contact if needed). VNPAY sends the IPN as a `GET` request; opening the IPN URL manually will not mark a payment successful. Merchant-domain restrictions may apply.

## 4. End-to-end Sandbox test

1. Open the **Production domain** and click `Thanh toán qua VNPAY`.
2. Follow VNPAY Sandbox payment steps. Official test cards: https://sandbox.vnpayment.vn/apis/vnpay-demo/
3. After successful payment, the browser returns to `/vnpay/return`. This page displays the order status based on DB, and may initially show `PENDING` until the IPN has been delivered.
4. Open Neon SQL Editor and run:

```sql
SELECT txn_ref, amount_vnd, status, vnpay_response_code, created_at
FROM orders ORDER BY created_at DESC LIMIT 10;
```

When valid IPN was received, a successful transaction should have `status = 'SUCCESS'`.

## 5. If you want to run locally too

PowerShell does **not** require activating `.venv`:

```powershell
py -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
Copy-Item .env.example .env
# Fill `.env` with real Sandbox and Neon values before running
.\.venv\Scripts\python.exe app.py
```

With this project configured for Vercel, testing payments end-to-end via its public domain is much simpler than from `localhost`.

## Common mistakes

- **500 / 503**: Check deployment logs, `DATABASE_URL`, and `schema.sql` (table missing).
- **Invalid signature**: Confirm correct Sandbox HashSecret and VNPAY 2.1.0 encoding rules.
- **Success at VNPAY, PENDING in DB**: IPN not received yet, IPN URL not configured, network, or signature failed. Check Vercel Functions logs.
- **404**: Confirm `app.py` is at Vercel Root Directory and contains the top-level Flask instance named `app`.
- **403 when paying**: Cookies/CSRF or navigating across preview vs production hostnames. Use the same configured Production host from form through redirect.
- **No local `.env` on Vercel**: Correct. Environment Variables must be configured in Vercel Dashboard; don't commit secrets.
- **Forbidden/Authentication required**: If Vercel Deployment Protection is on, ensure VNPAY can access the callback endpoint or use appropriate production configuration.

## Security and production readiness

This is ONLY a demonstration. To implement paid Premium plans in NutriFit: link `txn_ref` to an authenticated `user_id`, ensure exactly-once billing fulfillment, handle idempotent notifications safely, perform reconciliation via VNPAY QueryDR for missing IPNs, protect all secrets, observe logs and failures, and consult VNPAY before accepting real money. Return URL does not activate Premium; it only displays status. Do not grant Premium by trusting browser parameters alone. You may add separate persistent storage for Google Sheets integration if needed, but avoid using a plain Google Sheet as the only source of truth for concurrent financial transactions.

Authoritative documentation:

- https://vercel.com/docs/frameworks/backend/flask
- https://vercel.com/docs/functions/runtimes
- https://vercel.com/docs/postgres
- https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html

Note: A successful deployment does not guarantee the VNPAY Sandbox merchant/IPN configuration is complete. These require your real merchant credentials and an end-to-end Sandbox test.
