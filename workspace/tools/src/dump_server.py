# 静态服务 + 素材倾倒接收（?dump=1 捕获模式用）
# 用法: python tools/src/dump_server.py [root=dist] [dumpfile=buildtmp/hd_dump.json]
import http.server
import json
import os
import socketserver
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'dist'
DUMP = sys.argv[2] if len(sys.argv) > 2 else 'buildtmp/hd_dump.json'


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=ROOT, **k)

    def do_POST(self):
        if self.path != '/dump':
            self.send_error(404)
            return
        n = int(self.headers.get('Content-Length', 0))
        try:
            new = json.loads(self.rfile.read(n))
        except Exception:
            self.send_error(400)
            return
        old = {}
        if os.path.exists(DUMP):
            try:
                with open(DUMP, encoding='utf-8') as f:
                    old = json.load(f)
            except Exception:
                old = {}
        old.update(new)
        os.makedirs(os.path.dirname(DUMP), exist_ok=True)
        with open(DUMP, 'w', encoding='utf-8') as f:
            json.dump(old, f)
        self.send_response(200)
        self.send_header('Content-Length', '2')
        self.end_headers()
        self.wfile.write(b'ok')
        print(f'[dump] +{len(new)} -> total {len(old)}', flush=True)

    def log_message(self, *a):
        pass


socketserver.TCPServer.allow_reuse_address = True
with socketserver.ThreadingTCPServer(('127.0.0.1', 8020), Handler) as srv:
    print(f'serving {ROOT}/ on http://127.0.0.1:8020  (dump -> {DUMP})', flush=True)
    srv.serve_forever()
