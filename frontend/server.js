const http = require('http');
const fs = require('fs');
const path = require('path');

const DEFAULT_FRONTEND_PORT = 3000;
const BACKEND_PORT = 8001;
const STATUS_OK = 200;
const STATUS_BAD_GATEWAY = 502;
const STATUS_METHOD_NOT_ALLOWED = 405;
const STATUS_FORBIDDEN = 403;
const STATUS_NOT_FOUND = 404;
const LOOPBACK_HOST = '127.0.0.1';
const LISTEN_HOST = '0.0.0.0';
const PORT = Number(process.env.PORT || DEFAULT_FRONTEND_PORT);
const ASSET_DIR = path.join(__dirname, '..', 'src', 'assets');
const MIME = { '.html': 'text/html; charset=utf-8', '.png': 'image/png', '.svg': 'image/svg+xml', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8' };

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store' });
  res.end(body);
}

function proxyApi(req, res) {
  const upstream = http.request({ hostname: LOOPBACK_HOST, port: BACKEND_PORT, path: req.url, method: req.method, headers: req.headers }, response => {
    res.writeHead(response.statusCode || STATUS_BAD_GATEWAY, response.headers);
    response.pipe(res);
  });
  upstream.on('error', () => send(res, STATUS_BAD_GATEWAY, JSON.stringify({ error: 'Backend nicht erreichbar' }), 'application/json; charset=utf-8'));
  req.pipe(upstream);
}

http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (url.pathname.startsWith('/api/')) return proxyApi(req, res);
  if (req.method !== 'GET') return send(res, STATUS_METHOD_NOT_ALLOWED, 'Methode nicht erlaubt');
  const vendorQr = path.join(__dirname, 'node_modules', 'qrcode-generator', 'dist', 'qrcode.js');
  const relative = url.pathname === '/' ? 'code_auditor.html' : decodeURIComponent(url.pathname.replace(/^\/+/, ''));
  const file = url.pathname === '/vendor/qrcode.js' ? vendorQr : path.resolve(ASSET_DIR, relative);
  if (file !== vendorQr && !file.startsWith(ASSET_DIR + path.sep)) return send(res, STATUS_FORBIDDEN, 'Forbidden');
  fs.readFile(file, (error, data) => {
    if (error) return send(res, STATUS_NOT_FOUND, 'Not found');
    send(res, STATUS_OK, data, MIME[path.extname(file)] || 'application/octet-stream');
  });
}).listen(PORT, LISTEN_HOST);