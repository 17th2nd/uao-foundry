#!/usr/bin/env python3
"""Capture UI evidence from the running application using Chrome DevTools Protocol.

Standard library only, matching the repository's zero-dependency posture. Drives a real headless
Chrome against the real application, so the screenshots are of the shipped UI reading live data
from the real registry -- not mockups.
"""
import base64, json, os, socket, struct, subprocess, sys, time, urllib.request
from pathlib import Path


class CDP:
    """A minimal CDP client: enough websocket to send commands and read replies."""

    def __init__(self, ws_url):
        _, _, rest = ws_url.partition("://")
        hostport, _, path = rest.partition("/")
        host, _, port = hostport.partition(":")
        self.sock = socket.create_connection((host, int(port or 80)), timeout=30)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((
            f"GET /{path} HTTP/1.1\r\nHost: {hostport}\r\nUpgrade: websocket\r\n"
            f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n"
        ).encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.sock.recv(4096)
        self.buffer = buf.split(b"\r\n\r\n", 1)[1]
        self.next_id = 0

    def _send(self, payload: bytes):
        header = bytearray([0x81])
        length = len(payload)
        mask = os.urandom(4)
        if length < 126:
            header.append(0x80 | length)
        elif length < (1 << 16):
            header.append(0x80 | 126); header += struct.pack(">H", length)
        else:
            header.append(0x80 | 127); header += struct.pack(">Q", length)
        header += mask
        self.sock.sendall(bytes(header) + bytes(b ^ mask[i % 4] for i, b in enumerate(payload)))

    def _recv_frame(self):
        def need(n):
            while len(self.buffer) < n:
                chunk = self.sock.recv(65536)
                if not chunk:
                    raise ConnectionError("CDP socket closed")
                self.buffer += chunk
        need(2)
        length = self.buffer[1] & 0x7F
        offset = 2
        if length == 126:
            need(4); length = struct.unpack(">H", self.buffer[2:4])[0]; offset = 4
        elif length == 127:
            need(10); length = struct.unpack(">Q", self.buffer[2:10])[0]; offset = 10
        need(offset + length)
        payload = self.buffer[offset:offset + length]
        self.buffer = self.buffer[offset + length:]
        return payload

    def call(self, method, params=None, timeout=60):
        self.next_id += 1
        message_id = self.next_id
        self._send(json.dumps({"id": message_id, "method": method, "params": params or {}}).encode())
        deadline = time.time() + timeout
        while time.time() < deadline:
            message = json.loads(self._recv_frame())
            if message.get("id") == message_id:
                if "error" in message:
                    raise RuntimeError(f"{method}: {message['error']}")
                return message.get("result", {})
        raise TimeoutError(method)

    def eval(self, expression):
        result = self.call("Runtime.evaluate",
                           {"expression": expression, "awaitPromise": True, "returnByValue": True})
        return result.get("result", {}).get("value")

    def screenshot(self, path: Path):
        data = self.call("Page.captureScreenshot", {"format": "png", "captureBeyondViewport": True})
        path.write_bytes(base64.b64decode(data["data"]))
        return path.stat().st_size


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:7717/"
    out = Path(sys.argv[2] if len(sys.argv) > 2 else "temp/ui-evidence")
    out.mkdir(parents=True, exist_ok=True)

    port = 9222
    chrome = subprocess.Popen(
        ["google-chrome", "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
         f"--remote-debugging-port={port}", "--window-size=1400,1150",
         f"--user-data-dir=/tmp/usi-ui-evidence-{os.getpid()}", "about:blank"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        target = None
        for _ in range(60):
            try:
                with urllib.request.urlopen(f"http://127.0.0.1:{port}/json/list", timeout=2) as r:
                    tabs = json.load(r)
                pages = [t for t in tabs if t.get("type") == "page"]
                if pages:
                    target = pages[0]; break
            except Exception:
                time.sleep(0.5)
        if not target:
            print("could not attach to Chrome", file=sys.stderr); return 1

        cdp = CDP(target["webSocketDebuggerUrl"])
        cdp.call("Page.enable")
        cdp.call("Runtime.enable")

        def goto_view(view, settle=1.2):
            cdp.eval(f"document.querySelector('.tab[data-view=\"{view}\"]').click()")
            time.sleep(settle)

        cdp.call("Page.navigate", {"url": base})
        time.sleep(2.5)

        shots = []

        # 1. Manufacture, with a completed run rendered.
        cdp.eval("document.getElementById('f-identity').value='electric motor'")
        cdp.eval("document.getElementById('f-context').value='three phase induction motor industrial'")
        cdp.eval("document.getElementById('f-fixture').value="
                 f"'{os.path.abspath('examples/demonstration/electric-motor.json')}'")
        cdp.eval("document.getElementById('btn-manufacture').click()")
        for _ in range(60):
            time.sleep(0.5)
            if cdp.eval("document.getElementById('result-panel').hidden === false"):
                break
        time.sleep(1.0)
        shots.append(("01-manufacture-result.png", cdp.screenshot(out / "01-manufacture-result.png")))

        # 2. Registry search.
        goto_view("registry")
        cdp.eval("document.getElementById('s-query').value='electric motor'")
        cdp.eval("document.getElementById('btn-search').click()")
        time.sleep(1.5)
        shots.append(("02-registry-search.png", cdp.screenshot(out / "02-registry-search.png")))

        # 3. Identity inspector, addressed by durable external identifier.
        goto_view("identity")
        cdp.eval("document.getElementById('i-ref').value='wikidata:Q53068'")
        cdp.eval("document.getElementById('btn-identity').click()")
        time.sleep(1.8)
        shots.append(("03-identity-inspector.png", cdp.screenshot(out / "03-identity-inspector.png")))

        # 4. Package inspector.
        package_id = json.load(urllib.request.urlopen(base.rstrip('/') + "/api/runs"))["runs"][0]["packageId"]
        goto_view("package")
        cdp.eval(f"document.getElementById('p-id').value='{package_id}'")
        cdp.eval("document.getElementById('btn-package').click()")
        time.sleep(1.8)
        shots.append(("04-package-inspector.png", cdp.screenshot(out / "04-package-inspector.png")))

        # 5. Runs.
        goto_view("runs", 1.8)
        shots.append(("05-runs.png", cdp.screenshot(out / "05-runs.png")))

        # 6. Status.
        goto_view("status", 1.8)
        shots.append(("06-status.png", cdp.screenshot(out / "06-status.png")))

        errors = cdp.eval("window.__errors ? window.__errors.length : 0")
        for name, size in shots:
            print(f"  captured {name} ({size} bytes)")
        print(f"  console errors observed: {errors or 0}")
        return 0
    finally:
        chrome.terminate()
        try: chrome.wait(timeout=10)
        except Exception: chrome.kill()


if __name__ == "__main__":
    raise SystemExit(main())
