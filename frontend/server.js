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
const METHOD_GET = 'GET';
const API_PREFIX = '/api/';
const VENDOR_QR_PATH = '/vendor/qrcode.js';
const PORT = Number(process.env.PORT || DEFAULT_FRONTEND_PORT);
const DIST_DIR = path.join(__dirname, 'dist');
const SOURCE_ASSET_DIR = path.join(__dirname, '..', 'src', 'assets');
const ASSET_DIR = fs.existsSync(DIST_DIR) ? DIST_DIR : SOURCE_ASSET_DIR;
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

function resolveStaticFile(pathname) {
  const builtVendorQr = path.join(DIST_DIR, 'vendor', 'qrcode.js');
  const vendorQr = fs.existsSync(builtVendorQr) ? builtVendorQr : path.join(__dirname, 'node_modules', 'qrcode-generator', 'dist', 'qrcode.js');
  if (pathname === VENDOR_QR_PATH) return { file: vendorQr, vendorQr };
  const relative = pathname === '/' ? 'code_auditor.html' : decodeURIComponent(pathname.replace(/^\/+/, ''));
  return { file: path.resolve(ASSET_DIR, relative), vendorQr };
}

function serveStaticFile(pathname, res) {
  const { file, vendorQr } = resolveStaticFile(pathname);
  if (file !== vendorQr && !file.startsWith(ASSET_DIR + path.sep)) return send(res, STATUS_FORBIDDEN, 'Forbidden');
  fs.readFile(file, (error, data) => {
    if (error) return send(res, STATUS_NOT_FOUND, 'Not found');
    send(res, STATUS_OK, data, MIME[path.extname(file)] || 'application/octet-stream');
  });
}

function handleRequest(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (url.pathname.startsWith(API_PREFIX)) return proxyApi(req, res);
  if (req.method !== METHOD_GET) return send(res, STATUS_METHOD_NOT_ALLOWED, 'Methode nicht erlaubt');
  return serveStaticFile(url.pathname, res);
}

http.createServer(handleRequest).listen(PORT, LISTEN_HOST);