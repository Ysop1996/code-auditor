const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = Number(process.env.PORT || 3000);
const ASSET_DIR = path.join(__dirname, '..', 'src', 'assets');
const MIME = { '.html': 'text/html; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' });
  res.end(body);
}

function proxyApi(req, res) {
  const upstream = http.request({ hostname: '127.0.0.1', port: 8001, path: req.url, method: req.method, headers: req.headers }, response => {
    res.writeHead(response.statusCode || 502, response.headers);
    response.pipe(res);
  });
  upstream.on('error', () => send(res, 502, JSON.stringify({ error: 'Backend nicht erreichbar' }), 'application/json; charset=utf-8'));
  req.pipe(upstream);
}

http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (url.pathname.startsWith('/api/')) return proxyApi(req, res);
  if (req.method !== 'GET') return send(res, 405, 'Methode nicht erlaubt');
  const vendorQr = path.join(__dirname, 'node_modules', 'qrcode-generator', 'dist', 'qrcode.js');
  const relative = url.pathname === '/' ? 'code_auditor.html' : decodeURIComponent(url.pathname.replace(/^\/+/, ''));
  const file = url.pathname === '/vendor/qrcode.js' ? vendorQr : path.resolve(ASSET_DIR, relative);
  if (file !== vendorQr && !file.startsWith(ASSET_DIR + path.sep)) return send(res, 403, 'Forbidden');
  fs.readFile(file, (error, data) => {
    if (error) return send(res, 404, 'Not found');
    send(res, 200, data, MIME[path.extname(file)] || 'application/octet-stream');
  });
}).listen(PORT, '0.0.0.0', () => console.log(`AuditIQ frontend listening on ${PORT}`));