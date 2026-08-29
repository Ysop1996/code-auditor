const fs = require('fs');
const path = require('path');

const SOURCE_ASSET_DIR = path.join(__dirname, '..', 'src', 'assets');
const DIST_DIR = path.join(__dirname, 'dist');
const VENDOR_SOURCE = path.join(__dirname, 'node_modules', 'qrcode-generator', 'dist', 'qrcode.js');
const VENDOR_DIR = path.join(DIST_DIR, 'vendor');

if (!fs.existsSync(path.join(SOURCE_ASSET_DIR, 'code_auditor.html'))) {
  throw new Error('Missing frontend entry asset: code_auditor.html');
}
if (!fs.existsSync(VENDOR_SOURCE)) {
  throw new Error('Missing bundled QR dependency: qrcode-generator');
}

fs.rmSync(DIST_DIR, { recursive: true, force: true });
fs.cpSync(SOURCE_ASSET_DIR, DIST_DIR, { recursive: true });
fs.mkdirSync(VENDOR_DIR, { recursive: true });
fs.copyFileSync(VENDOR_SOURCE, path.join(VENDOR_DIR, 'qrcode.js'));