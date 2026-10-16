-- Run once in Neon SQL Editor before testing payment.
CREATE TABLE IF NOT EXISTS orders (
    txn_ref TEXT PRIMARY KEY,
    amount_vnd INTEGER NOT NULL CHECK (amount_vnd > 0),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    vnpay_transaction_no TEXT,
    vnpay_response_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at DESC);
