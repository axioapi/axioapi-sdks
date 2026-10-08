"""Mock AxioAPI server shared by every SDK test suite. Usage: python mock_server.py [port]

Mirrors the real envelope: {"status","data","request":{...}} on success and
{"status","data":null,"error":{code,message,request_id,fields?},"request":{...}} on errors.
"""

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

KEY = "test_key"
COUNTS = {}
PNG = b"\x89PNG\r\n\x1a\n\x00\x01\x02binary"


def envelope(status, data=None, error=None, req_id="req_test_1"):
    body = {"status": "success" if status < 400 else "error", "data": data}
    if error:
        body["error"] = {"request_id": req_id, **error}
    body["request"] = {"id": req_id, "method": "", "path": "", "input": {}}
    return body


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def send(self, status, body=None, headers=None, raw=None, ctype="application/json"):
        payload = raw if raw is not None else json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("X-Request-Id", "req_test_1")
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    def hit(self, name):
        COUNTS[name] = COUNTS.get(name, 0) + 1
        return COUNTS[name]

    def handle_any(self):
        url = urlparse(self.path)
        path = url.path
        query = parse_qs(url.query)
        length = int(self.headers.get("Content-Length") or 0)
        raw_body = self.rfile.read(length) if length else b""
        try:
            body = json.loads(raw_body) if raw_body else None
        except ValueError:
            body = None

        if path == "/__reset":
            COUNTS.clear()
            return self.send(200, {"ok": True})
        if path == "/__hits":
            return self.send(200, COUNTS)

        auth = self.headers.get("Authorization", "")
        if auth == "Bearer nocredit":
            return self.send(402, envelope(402, error={"code": "insufficient_credits", "message": "Not enough credits"}))
        if auth != f"Bearer {KEY}":
            return self.send(401, envelope(401, error={"code": "unauthenticated", "message": "Unauthenticated."}))

        if path == "/api/v1/flaky":
            if self.hit("flaky") <= 2:
                return self.send(503, envelope(503, error={"code": "service_unavailable", "message": "Try later"}))
            return self.send(200, envelope(200, {"attempts": COUNTS["flaky"]}))
        if path == "/api/v1/ratelimited":
            if self.hit("ratelimited") == 1:
                return self.send(429, envelope(429, error={"code": "rate_limited", "message": "Slow down"}), {"Retry-After": "0"})
            return self.send(200, envelope(200, {"attempts": COUNTS["ratelimited"]}))
        if path == "/api/v1/post503":
            self.hit("post503")
            return self.send(503, envelope(503, error={"code": "service_unavailable", "message": "Try later"}))
        if path == "/api/v1/always429":
            self.hit("always429")
            return self.send(429, envelope(429, error={"code": "rate_limited", "message": "Slow down"}), {"Retry-After": "7"})
        if path == "/api/v1/seo/on-page-audit" and self.command == "POST" and not (body or {}).get("url"):
            return self.send(422, envelope(422, error={"code": "validation_failed", "message": "Invalid request parameters", "fields": {"url": ["The url field is required."]}}))
        if path.startswith("/api/v1/temp-mail/inboxes/missing"):
            return self.send(404, envelope(404, error={"code": "not_found", "message": "Resource unavailable"}))
        if path.endswith("/artifact"):
            return self.send(200, raw=PNG, ctype="image/png")
        if path == "/api/v1/boom":
            return self.send(500, raw=b"<html>oops</html>", ctype="text/html")

        echo = {"method": self.command, "path": path, "query": query, "body": body,
                "user_agent": self.headers.get("User-Agent"), "content_type": self.headers.get("Content-Type")}
        return self.send(201 if self.command == "POST" and path.endswith("/inboxes") else 200, envelope(200, echo))

    do_GET = do_POST = do_PUT = do_PATCH = do_DELETE = do_HEAD = handle_any


class QuietServer(ThreadingHTTPServer):
    daemon_threads = True

    def handle_error(self, request, client_address):
        pass  # clients closing keep-alive connections is normal


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    QuietServer(("127.0.0.1", port), Handler).serve_forever()
