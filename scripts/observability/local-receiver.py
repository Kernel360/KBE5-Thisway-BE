"""Disposable container-only webhook sink and synthetic fault exporter; no external delivery."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
lock = Lock()
events = []
backlog = 0
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        with lock:
            value = f'thisway_evidence_backlog {backlog}\n' if self.path == '/metrics' else json.dumps(events)
        self.send_response(200); self.send_header("Content-Type", "text/plain; version=0.0.4" if self.path == "/metrics" else "application/json"); self.end_headers(); self.wfile.write(value.encode())
    def do_POST(self):
        global backlog
        body = self.rfile.read(min(int(self.headers.get('Content-Length', 0)), 65536))
        with lock:
            if self.path == '/fault/on': backlog = 80
            elif self.path == '/fault/off': backlog = 0
            elif self.path == '/events': events.append(json.loads(body))
            else: self.send_response(404); self.end_headers(); return
        self.send_response(200); self.end_headers()
ThreadingHTTPServer(('0.0.0.0', 8080), Handler).serve_forever()
