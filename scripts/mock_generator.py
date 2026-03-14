#!/usr/bin/env python3
"""
Simple mock generator that listens on localhost:11434 and responds to POST /api/generate
with a JSON body {"text": "<json array>"} where <json array> is the contents of
src/main/resources/static/data/sample_questions.json. This mimics the expected model server
so you can test the Spring Boot app locally without the real generator.

Run: python scripts\mock_generator.py
"""
import http.server
import socketserver
import json
import pathlib

PORT = 11435
SAMPLE_PATH = pathlib.Path(__file__).parent.parent / 'src' / 'main' / 'resources' / 'static' / 'data' / 'sample_questions.json'

class Handler(http.server.BaseHTTPRequestHandler):
    def _set_headers(self, code=200):
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

    def do_POST(self):
        if self.path != '/api/generate':
            self._set_headers(404)
            self.wfile.write(json.dumps({'error':'not found'}).encode())
            return
        length = int(self.headers.get('content-length', 0))
        _ = self.rfile.read(length)
        try:
            with open(SAMPLE_PATH, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except Exception as e:
            self._set_headers(500)
            self.wfile.write(json.dumps({'error': str(e)}).encode())
            return
        # Return as {"text": "<json array as string>"}
        payload = {'text': json.dumps(data)}
        self._set_headers(200)
        self.wfile.write(json.dumps(payload).encode())

    def log_message(self, format, *args):
        # quiet
        return

if __name__ == '__main__':
    print(f"Mock generator running on http://localhost:{PORT}/api/generate")
    with socketserver.TCPServer(('127.0.0.1', PORT), Handler) as httpd:
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print('Shutting down')
            httpd.server_close()

