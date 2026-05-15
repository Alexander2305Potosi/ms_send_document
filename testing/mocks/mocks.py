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
                "SC-OK-01": [{"documentId": "DOC-OK-01", "filename": "manual.pdf"}],
                "SC-OK-02": [{"documentId": "DOC-OK-02", "filename": "large_valid.pdf"}],
                "SC-OK-03": [{"documentId": "DOC-OK-03", "filename": "guide.docx"}],
                "SC-OK-04": [{"documentId": "DOC-OK-04", "filename": "notes.txt"}],
                "SC-OK-05": [{"documentId": "DOC-OK-05", "filename": "bundle.zip"}],
                "SC-RT-01": [{"documentId": "DOC-RT-01", "filename": "retry_timeout.pdf"}],
                "SC-RT-02": [{"documentId": "DOC-RT-02", "filename": "retry_500.pdf"}],
                "SC-RT-03": [{"documentId": "DOC-RT-03", "filename": "retry_503.pdf"}],
                "SC-RT-04": [{"documentId": "DOC-RT-04", "filename": "retry_429.pdf"}],
                "SC-RT-05": [{"documentId": "DOC-RT-05", "filename": "retry_504.pdf"}],
                "SC-BR-01": [{"documentId": "DOC-BR-01", "filename": "too_large.pdf"}],
                "SC-BR-02": [{"documentId": "DOC-BR-02", "filename": "virus.exe"}],
                "SC-BR-03": [{"documentId": "DOC-BR-03", "filename": "script.sh"}],
                "SC-BR-04": [{"documentId": "DOC-BR-04", "filename": "WRONG_NAME.pdf"}],
                "SC-BR-05": [{"documentId": "DOC-BR-05", "filename": "empty.zip"}],
                "SC-BR-06": [{"documentId": "DOC-BR-06", "filename": "mixed_content.zip"}],
                "SC-TE-01": [{"documentId": "DOC-TE-01", "filename": "soap_fault.pdf"}],
                "SC-TE-02": [{"documentId": "DOC-TE-02", "filename": "malformed.pdf"}],
                "SC-TE-03": [{"documentId": "DOC-TE-03", "filename": "empty_resp.pdf"}],
                "SC-TE-04": [{"documentId": "DOC-TE-04", "filename": "unauthorized.pdf"}],
                "SC-TE-05": [{"documentId": "DOC-TE-05", "filename": "forbidden.pdf"}],
                "SC-TE-06": [{"documentId": "DOC-TE-06", "filename": "bad_request.pdf"}],
                "SC-TE-07": [{"documentId": "DOC-TE-07", "filename": "corrupted.pdf"}]
            }
            response = scenarios.get(product_id, [])
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps(response).encode())
            
        elif "/api/products/" in self.path and "documents" in path_parts:
            doc_id = path_parts[-1]
            content = "U09BUCB0ZXN0IGNvbnRlbnQ=" 
            size = 100
            filename = f"{doc_id}.pdf"

            if "DOC-OK-05" in doc_id:
                content = create_mock_zip({"f1.pdf": b"c1", "f2.pdf": b"c2", "f3.pdf": b"c3"})
                filename = "bundle.zip"
            elif "DOC-BR-06" in doc_id:
                content = create_mock_zip({"f1.exe": b"virus", "f2.sh": b"script"})
                filename = "mixed_content.zip"
            elif "DOC-BR-05" in doc_id:
                content = create_mock_zip({})
                filename = "empty.zip"
            elif "DOC-OK-02" in doc_id:
                size = 9 * 1024 * 1024 
            elif "DOC-BR-01" in doc_id:
                size = 20 * 1024 * 1024 
            elif "DOC-BR-02" in doc_id: filename = "virus.exe"
            elif "DOC-BR-03" in doc_id: filename = "script.sh"
            elif "DOC-TE-07" in doc_id: content = "NOT_BASE64_AT_ALL_!!!!"

            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({
                "documentId": doc_id, "filename": filename,
                "contentType": "application/pdf", "content": content, "size": size, 
                "origin": "PORTAL", "pais": "CO"
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

        # Transient Failure Scenarios (Fail 1st and 2nd attempt)
        if attempt <= 2:
            if "retry_timeout" in doc_key:
                time.sleep(8); return
            if "retry_500" in doc_key:
                self.send_response(500); self.end_headers(); self.wfile.write(b"Server Error"); return
            if "retry_503" in doc_key:
                self.send_response(503); self.end_headers(); self.wfile.write(b"Service Unavailable"); return
            if "retry_429" in doc_key:
                self.send_response(429); self.end_headers(); self.wfile.write(b"Rate Limited"); return
            if "retry_504" in doc_key:
                self.send_response(504); self.end_headers(); self.wfile.write(b"Gateway Timeout"); return

        # Final Technical Failure Scenarios
        if "soap_fault" in doc_key:
            fault_xml = """<S:Envelope xmlns:S="http://schemas.xmlsoap.org/soap/envelope/">
   <S:Body><S:Fault><faultcode>S:Server</faultcode><faultstring>Business logic failure</faultstring></S:Fault></S:Body>
</S:Envelope>"""
            self.send_response(500); self.send_header('Content-Type', 'text/xml'); self.end_headers()
            self.wfile.write(fault_xml.encode()); return
        if "unauthorized" in doc_key: self.send_response(410); self.end_headers(); return
        if "forbidden" in doc_key: self.send_response(403); self.end_headers(); return
        if "bad_request" in doc_key: self.send_response(400); self.end_headers(); return
        if "malformed" in doc_key: self.send_response(200); self.end_headers(); self.wfile.write(b"<xml>malformed"); return
        if "empty_resp" in doc_key: self.send_response(200); self.end_headers(); return

        # Success Logic
        status = "OK"
        message = "Procesado exitosamente"
        if attempt > 1: message = f"Exito tras {attempt-1} reintentos"

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
    print("Mocks started. 23 Scenarios active.")
    try:
        while True: time.sleep(1)
    except KeyboardInterrupt: pass
