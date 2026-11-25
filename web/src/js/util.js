export const $ = (id) => document.getElementById(id);
export const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
export const time = (m) => { m = +m || 0; const h = Math.floor(m / 60); return h > 0 ? `${h} h ${m % 60} min` : `${m % 60} min`; };
export const safeColor = (c) => (/^#[0-9a-fA-F]{6}$/.test(c) ? c : "#a88d83");
