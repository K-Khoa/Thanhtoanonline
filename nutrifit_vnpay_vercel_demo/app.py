"""NutriFit VNPAY Sandbox - Flask on Vercel + persistent PostgreSQL.

Demonstration only: attach real user identities, operational monitoring,
reconciliation, and merchant approval before handling actual payments.
"""
import hashlib
import hmac
import logging
import os
import uuid
from datetime import datetime, timedelta
from urllib.parse import urlencode
from zoneinfo import ZoneInfo

import psycopg
from psycopg.rows import dict_row
from dotenv import load_dotenv
from flask import Flask, abort, jsonify, redirect, render_template, request, session

load_dotenv()

VNPAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
TMN_CODE = os.environ.get("VNPAY_TMN_CODE", "").strip()
HASH_SECRET = os.environ.get("VNPAY_HASH_SECRET", "").strip()
PUBLIC_BASE_URL = os.environ.get("PUBLIC_BASE_URL", "").strip().rstrip("/")
FLASK_SECRET_KEY = os.environ.get("FLASK_SECRET_KEY", "").strip()
DATABASE_URL = os.environ.get("DATABASE_URL", "").strip()
PRICE_VND = 49000

app = Flask(__name__)
# Unlike SQLite, PostgreSQL keeps orders even after serverless instances restart.
app.secret_key = FLASK_SECRET_KEY or os.urandom(32)
app.config.update(
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
    SESSION_COOKIE_SECURE=bool(os.environ.get("VERCEL")),
)
logging.basicConfig(level=logging.INFO)


def db_connection():
    if not DATABASE_URL:
        raise RuntimeError("DATABASE_URL is missing. Create a Neon DB and set Vercel Environment Variables.")
    return psycopg.connect(DATABASE_URL, connect_timeout=10, row_factory=dict_row)


def now_vietnam():
    return datetime.now(ZoneInfo("Asia/Ho_Chi_Minh"))


def signed_fields(params):
    """VNPAY v2.1.0: sorted fields, urlencode exactly once (quote_plus)."""
    fields = {
        k: str(v) for k, v in params.items()
        if k.startswith("vnp_")
        and k not in ("vnp_SecureHash", "vnp_SecureHashType")
        and v is not None and str(v) != ""
    }
    return urlencode(sorted(fields.items()))


def make_signature(params):
    return hmac.new(
        HASH_SECRET.encode("utf-8"),
        signed_fields(params).encode("utf-8"),
        hashlib.sha512,
    ).hexdigest()


def valid_signature(params):
    supplied = str(params.get("vnp_SecureHash", ""))
    return bool(supplied and HASH_SECRET and
                hmac.compare_digest(make_signature(params), supplied.lower()))


def vnpay_ready():
    return bool(TMN_CODE and HASH_SECRET and DATABASE_URL and
                FLASK_SECRET_KEY and PUBLIC_BASE_URL.startswith("https://"))


@app.get("/")
def index():
    if "csrf" not in session:
        session["csrf"] = uuid.uuid4().hex
    return render_template("index.html", amount=PRICE_VND, csrf=session["csrf"])


@app.post("/pay")
def create_payment():
    sent_csrf = str(request.form.get("csrf", ""))
    real_csrf = str(session.get("csrf", ""))
    if not sent_csrf or not real_csrf or not hmac.compare_digest(sent_csrf, real_csrf):
        abort(403, "Invalid request token")
    if not vnpay_ready():
        abort(503, "Missing payment environment variables. Check Vercel Settings.")

    txn_ref = uuid.uuid4().hex
    now = now_vietnam()
    try:
        with db_connection() as db:
            db.execute(
                "INSERT INTO orders (txn_ref, amount_vnd, status, created_at) VALUES (%s, %s, %s, %s)",
                (txn_ref, PRICE_VND, "PENDING", now),
            )
    except psycopg.Error:
        app.logger.exception("Cannot create payment order")
        abort(503, "Database unavailable. Please try later.")

    params = {
        "vnp_Version": "2.1.0",
        "vnp_Command": "pay",
        "vnp_TmnCode": TMN_CODE,
        "vnp_Amount": str(PRICE_VND * 100),
        "vnp_CreateDate": now.strftime("%Y%m%d%H%M%S"),
        "vnp_ExpireDate": (now + timedelta(minutes=15)).strftime("%Y%m%d%H%M%S"),
        "vnp_CurrCode": "VND",
        "vnp_IpAddr": (request.headers.get("X-Forwarded-For", "").split(",")[0].strip()
                       or request.remote_addr or "127.0.0.1"),
        "vnp_Locale": "vn",
        "vnp_OrderInfo": f"NutriFit Premium {txn_ref}",
        "vnp_OrderType": "other",
        "vnp_ReturnUrl": PUBLIC_BASE_URL + "/vnpay/return",
        "vnp_TxnRef": txn_ref,
    }
    return redirect(
        f"{VNPAY_URL}?{signed_fields(params)}&vnp_SecureHash={make_signature(params)}",
        code=303,
    )


@app.get("/vnpay/ipn")
def vnpay_ipn():
    params = request.args.to_dict(flat=True)
    if not valid_signature(params) or params.get("vnp_TmnCode") != TMN_CODE:
        return jsonify(RspCode="97", Message="Invalid signature or merchant")
    try:
        paid_amount = int(params.get("vnp_Amount", ""))
    except (ValueError, TypeError):
        return jsonify(RspCode="04", Message="Invalid amount")

    ref = params.get("vnp_TxnRef", "")
    try:
        with db_connection() as db:
            # Row-level lock protects against duplicate / concurrent IPNs.
            order = db.execute(
                "SELECT txn_ref, amount_vnd, status FROM orders WHERE txn_ref = %s FOR UPDATE",
                (ref,),
            ).fetchone()
            if order is None:
                return jsonify(RspCode="01", Message="Order not found")
            if paid_amount != order["amount_vnd"] * 100:
                return jsonify(RspCode="04", Message="Invalid amount")
            if order["status"] != "PENDING":
                return jsonify(RspCode="02", Message="Order already confirmed")

            status = ("SUCCESS" if params.get("vnp_ResponseCode") == "00" and
                      params.get("vnp_TransactionStatus") == "00" else "FAILED")
            db.execute(
                """UPDATE orders SET status = %s, vnpay_transaction_no = %s,
                   vnpay_response_code = %s WHERE txn_ref = %s AND status = 'PENDING'""",
                (status, params.get("vnp_TransactionNo"),
                 params.get("vnp_ResponseCode"), ref),
            )
            # To grant Premium, also update the user's entitlement atomically in this DB.
        return jsonify(RspCode="00", Message="Confirm Success")
    except (psycopg.Error, RuntimeError):
        app.logger.exception("Cannot process VNPAY IPN")
        return jsonify(RspCode="99", Message="Database error")


@app.get("/vnpay/return")
def vnpay_return():
    params = request.args.to_dict(flat=True)
    if not valid_signature(params) or params.get("vnp_TmnCode") != TMN_CODE:
        return render_template("result.html", label="Chu ky VNPAY khong hop le",
                               message="Khong the xac minh thanh toan.", ref=""), 400
    ref = params.get("vnp_TxnRef", "")
    try:
        with db_connection() as db:
            order = db.execute(
                "SELECT amount_vnd, status FROM orders WHERE txn_ref = %s",
                (ref,),
            ).fetchone()
    except (psycopg.Error, RuntimeError):
        app.logger.exception("Cannot read payment status")
        return render_template("result.html", label="Chua the kiem tra don hang",
                               message="Vui long thu lai sau.", ref=ref), 503
    if order is None or str(order["amount_vnd"] * 100) != params.get("vnp_Amount"):
        return render_template("result.html", label="Don hang khong hop le",
                               message="Khong tim thay don hang hoac so tien khong khop.", ref=ref), 400
    if order["status"] == "SUCCESS":
        label, message = "Thanh toan thanh cong", "NutriFit da ghi nhan thanh toan."
    elif order["status"] == "FAILED":
        label, message = "Thanh toan khong thanh cong", "Vui long tao don hang moi."
    else:
        label, message = "Dang cho xac nhan", "Dang cho VNPAY IPN; hay tai lai trang sau it phut."
    return render_template("result.html", label=label, message=message, ref=ref)


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=False)
