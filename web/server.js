const http = require('http');
const fs = require('fs');
const path = require('path');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.ttf': 'font/ttf',
  '.apk': 'application/vnd.android.package-archive'
};

const server = http.createServer((req, res) => {
  const parsedUrl = req.url.split('?')[0];
  let reqPath = parsedUrl === '/' ? '/index.html' : parsedUrl;
  const safePath = path.normalize(reqPath).replace(/^(\.\.[\/\\])+/, '');
  const filePath = path.join(__dirname, safePath);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';

    const headers = { 'Content-Type': contentType };
    if (ext === '.apk') {
      headers['Content-Disposition'] = 'attachment; filename="nano-player.apk"';
    }

    res.writeHead(200, headers);
    return fs.createReadStream(filePath).pipe(res);
  }

  // Fallback to index.html
  const indexPath = path.join(__dirname, 'index.html');
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  fs.createReadStream(indexPath).pipe(res);
});

server.listen(8080, '127.0.0.1', () => {
  console.log('IPOD_SERVER_READY: http://127.0.0.1:8080');
});
