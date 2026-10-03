"""Recovery cannot finish while pages answer but the backend still reports startup warm-up as unavailable."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import threading
import unittest


class ServingTests(unittest.TestCase):
    def run_probe(self, healthy_after, timeout):
        state = {'health': 0, 'search': 0, 'search_before_ready': False}

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                status = 200
                body = b'ok'
                if self.path == '/backend-health':
                    state['health'] += 1
                    status = 200 if state['health'] > healthy_after else 503
                elif self.path == '/api/v2/listings/search':
                    state['search'] += 1
                    state['search_before_ready'] |= state['health'] <= healthy_after
                    body = b'{"degraded":false,"items":[{"id":"synthetic"}]}'
                self.send_response(status)
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_args):
                pass

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        source = Path(__file__).with_name('ci-ops-drill.sh').read_text()
        start = source.index('wait_serving() {')
        function = source[start:source.index('\n}', start) + 2]
        # Load only this read-only probe, not the runner's Docker setup/cleanup. Faster polling keeps the regression small.
        script = f'''BASE=http://127.0.0.1:{server.server_port}
ms() {{ python3 -c 'import time; print(int(time.time()*1000))'; }}
sleep() {{ command sleep 0.01; }}
{function}
wait_serving {timeout} /listings/synthetic
'''
        try:
            result = subprocess.run(['bash', '-c', script], capture_output=True, text=True, timeout=6)
            return result, state
        finally:
            server.shutdown()
            server.server_close()
            worker.join()

    def test_pages_and_search_cannot_finish_before_backend_health_is_ready(self):
        result, state = self.run_probe(healthy_after=3, timeout=4)
        self.assertEqual(result.returncode, 0, result.stderr)
        first, ready = map(int, result.stdout.split())
        self.assertLess(first, ready)
        self.assertGreater(state['health'], 3)
        self.assertEqual(state['search'], 1)
        self.assertFalse(state['search_before_ready'])

    def test_backend_that_never_becomes_healthy_times_out_despite_successful_pages(self):
        result, state = self.run_probe(healthy_after=10000, timeout=1)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertGreater(state['health'], 0)
        self.assertEqual(state['search'], 0)
        self.assertEqual(result.stdout, '')


if __name__ == '__main__':
    unittest.main()
