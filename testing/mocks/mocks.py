import http.server
import socketserver
import threading
import json
import time
import re
import base64
import zipfile
import io

# State management
retry_lock = threading.Lock()
retry_counts = {}

def create_mock_zip(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as zf:
        for name, content in files.items():
            zf.writestr(name, content)
    return base64.b64encode(buf.getvalue()).decode('utf-8')

class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

# --- PRODUCT REST API MOCK (Port 3001) ---
class ProductRestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path_parts = self.path.split('/')
        if "/api/products/" in self.path and path_parts[-1] == "documents":
            product_id = path_parts[path_parts.index("products") + 1]
            
            scenarios = {
                "SC-SUCCESS-01": [{"documentId": "DOC-OK-STD", "filename": "manual.pdf"}],
                "SC-SUCCESS-02": [{"documentId": "DOC-OK-LARGE", "filename": "large_valid.pdf"}],
                "SC-SUCCESS-03": [{"documentId": "DOC-OK-DOCX", "filename": "guide.docx"}],
                "SC-SUCCESS-ZIP": [{"documentId": "DOC-OK-ZIP", "filename": "package.zip"}],
                "SC-RETRY-TIMEOUT": [{"documentId": "DOC-RETRY-TIMEOUT", "filename": "retry_timeout.pdf"}],
                "SC-RETRY-500": [{"documentId": "DOC-RETRY-500", "filename": "retry_500.pdf"}],
                "SC-RETRY-429": [{"documentId": "DOC-RETRY-429", "filename": "retry_429.pdf"}],
                "SC-FAIL-SIZE": [{"documentId": "DOC-FAIL-SIZE", "filename": "too_big.pdf"}],
                "SC-FAIL-EXT": [{"documentId": "DOC-FAIL-EXT", "filename": "virus.exe"}],
                "SC-FAIL-ZIP-EMPTY": [{"documentId": "DOC-FAIL-ZIP-EMPTY", "filename": "empty.zip"}],
                "SC-FAIL-SOAP-FAULT": [{"documentId": "DOC-FAIL-SOAP-FAULT", "filename": "soap_fault.pdf"}],
                "SC-FAIL-MALFORMED": [{"documentId": "DOC-FAIL-MALFORMED", "filename": "malformed.pdf"}],
                "SC-FAIL-EMPTY": [{"documentId": "DOC-FAIL-EMPTY", "filename": "empty_resp.pdf"}]
            }
            response = scenarios.get(product_id, [])
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps(response).encode())
            
        elif "/api/products/" in self.path and "documents" in path_parts:
            doc_id = path_parts[-1]
            # Custom content based on doc_id
            content = "U09BUCB0ZXN0IGNvbnRlbnQ=" # Default "SOAP test content"
            size = 100
            filename = f"{doc_id}.pdf"

            if "DOC-OK-ZIP" in doc_id:
                content = create_mock_zip({"file1.pdf": b"content1", "file2.pdf": b"content2"})
                filename = "package.zip"
            elif "DOC-FAIL-ZIP-EMPTY" in doc_id:
                content = create_mock_zip({})
                filename = "empty.zip"
            elif "DOC-OK-LARGE" in doc_id:
                size = 9 * 1024 * 1024 # 9MB
            elif "DOC-FAIL-SIZE" in doc_id:
                size = 20 * 1024 * 1024 # 20MB
            elif "DOC-FAIL-EXT" in doc_id:
                filename = "virus.exe"

            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({
                "documentId": doc_id, "filename": filename,
                "contentType": "application/pdf" if ".pdf" in filename else "application/octet-stream",
                "content": content, "size": size, "origin": "PORTAL", "pais": "CO"
            }).encode())

# --- SOAP API MOCK (Port 9000) ---
class SoapHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length).decode('utf-8', errors='ignore')
        
        match = re.search(r'<nombreArchivo>(.*?)</nombreArchivo>', post_data)
        doc_key = match.group(1) if match else "unknown"
        
        with retry_lock:
            retry_counts[doc_key] = retry_counts.get(doc_key, 0) + 1
            attempt = retry_counts[doc_key]
        
        print(f"[SOAP] Request for {doc_key} - Attempt {attempt}")

        # Transient Failure Scenarios (Retryable)
        if attempt <= 2:
            if "retry_timeout" in doc_key:
                time.sleep(8); return
            if "retry_500" in doc_key:
                self.send_response(500); self.end_headers(); self.wfile.write(b"Internal Error"); return
            if "retry_429" in doc_key:
                self.send_response(429); self.end_headers(); self.wfile.write(b"Too Many Requests"); return

        # Technical Failure Scenarios (Non-retryable or specific)
        if "soap_fault" in doc_key:
            fault_xml = """<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
   <S:Body><S:Fault><faultcode>S:Server</faultcode><faultstring>Critical Business Fault</faultstring></S:Fault></S:Body>
</S:Envelope>"""
            self.send_response(500); self.send_header('Content-Type', 'text/xml'); self.end_headers()
            self.wfile.write(fault_xml.encode()); return

        if "malformed" in doc_key:
            self.send_response(200); self.end_headers()
            self.wfile.write(b"<invalid><xml>Malforming here..."); return

        if "empty_resp" in doc_key:
            self.send_response(200); self.end_headers(); return

        # Success Logic
        status = "OK"
        message = "Procesado exitosamente"
        if attempt > 1:
            message = f"Exito tras {attempt-1} reintentos"

        response_xml = f"""<?xml version="1.0" encoding="UTF-8"?>
<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
   <S:Body>
      <transmitirDocumentoResponse xmlns="http://prueba.com/intf/factory/adminDocs/V1.0">
         <status>{status}</status><message>{message}</message>
         <correlationId>MOCK-{int(time.time())}</correlationId>
      </transmitirDocumentoResponse>
   </S:Body>
</S:Envelope>"""
        self.send_response(200); self.send_header('Content-Type', 'text/xml'); self.end_headers()
        self.wfile.write(response_xml.encode())

def run_server(handler_class, port):
    httpd = ThreadedTCPServer(("", port), handler_class)
    httpd.serve_forever()

if __name__ == "__main__":
    threading.Thread(target=run_server, args=(ProductRestHandler, 3001), daemon=True).start()
    threading.Thread(target=run_server, args=(SoapHandler, 9000), daemon=True).start()
    print("Mocks started. Full suite of scenarios enabled (Success, Retry, Business Fail, Tech Fail, ZIP).")
    try:
        while True: time.sleep(1)
    except KeyboardInterrupt: pass
