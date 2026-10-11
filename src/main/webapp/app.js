const vnd = n => n.toLocaleString('vi-VN') + ' ₫';
const getCart = () => { try { return JSON.parse(localStorage.getItem('cart')) || {}; } catch (e) { return {}; } };
const saveCart = c => { try { localStorage.setItem('cart', JSON.stringify(c)); } catch (e) {} badge(); };
function addToCart(id) { const c = getCart(); c[id] = (c[id] || 0) + 1; saveCart(c); }
function badge() {
  const n = Object.values(getCart()).reduce((a, b) => a + b, 0);
  const el = document.getElementById('badge'); if (el) el.textContent = n;
}
function flash(btn) { btn.textContent = 'Đã thêm ✓'; setTimeout(() => btn.textContent = 'Thêm vào giỏ', 900); }
const loadProducts = () => fetch('api/products').then(r => r.json());
document.addEventListener('DOMContentLoaded', badge);
