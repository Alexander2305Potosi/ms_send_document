import http.server
import socketserver
import threading
import json
import time
import re
import base64
import zipfile
import io

retry_lock = threading.Lock()
retry_counts = {}

FILENAME_MAP = {
    "DOC-RT-01": "retry_timeout.pdf", "DOC-RT-02": "retry_500.pdf",
    "DOC-RT-03": "retry_503.pdf", "DOC-RT-04": "retry_429.pdf", "DOC-RT-05": "retry_504.pdf",
    "DOC-RT-06": "retry_503_success.pdf",
    "DOC-TE-01": "soap_fault.pdf", "DOC-TE-02": "malformed.pdf", "DOC-TE-03": "empty_resp.pdf",
    "DOC-TE-04": "unauthorized.pdf", "DOC-TE-05": "forbidden.pdf", "DOC-TE-06": "bad_request.pdf",
    "DOC-TE-07": "corrupted.pdf", "DOC-BR-02": "virus.exe", "DOC-BR-03": "script.sh",
    "DOC-BR-04": "WRONG_NAME.pdf", "DOC-OK-01": "manual.pdf", "DOC-OK-02": "large_valid.pdf",
    "DOC-OK-03": "guide.docx", "DOC-OK-04": "notes.txt", "DOC-OK-05": "bundle.zip",
    "DOC-OK-06": "user_manual.pdf", "DOC-OK-07": "brazil_tech.docx", "DOC-OK-08": "arg_setup.txt",
    "DOC-OK-09": "chile_safe.pdf", "DOC-OK-10": "peru_qa.pdf", "DOC-OK-11": "multi_type.zip",
    "DOC-OK-12": "nested.zip", "DOC-OK-13": "mexico_user.docx", "DOC-OK-14": "col_compressed.zip",
    "DOC-OK-15": "partial.zip", "DOC-OK-16": "fail_all.zip",
    "DOC-BR-07": "duplicate.pdf", "DOC-BR-08": "oversized_inner.zip", "DOC-BR-09": "unsupported_inner.zip",
    "DOC-BR-10": "empty_inner.zip", "DOC-BR-11": "corrupted.zip", "DOC-BR-12": "no_sucursal.pdf",
    "DOC-TE-08": "persistent_500.pdf", "DOC-TE-09": "persistent_503.pdf", "DOC-TE-10": "persistent_504.pdf",
    "DOC-TE-13": "download_500.pdf", "DOC-TE-14": "download_404.pdf",
    "DOC-TE-15": "malformed_xml.pdf", "DOC-TE-16": "soap_timeout_persistent.pdf",
    "DOC-ANIMAL-100-01": "animal_100_health.pdf", "DOC-ANIMAL-200-01": "animal_200_health.pdf",
    "DOC-ANIMAL-300-02": "bird_unsupported.exe", "DOC-ANIMAL-300-03": "bird_oversized.pdf",
    "DOC-ANIMAL-300-04": "animal_retry_timeout.pdf"
}

FILENAME_MAP_CLEAN = {k.replace("-", "").upper(): v for k, v in FILENAME_MAP.items()}

def get_filename_for_doc(doc_id):
    doc_id_upper = doc_id.upper()
    if "ANIMAL" in doc_id_upper:
        clean = doc_id_upper.replace("-", "")
        product_key = clean.replace("ANIMAL", "")
        filename = FILENAME_MAP_CLEAN.get(product_key, None)
        if filename:
            return filename
    return FILENAME_MAP_CLEAN.get(doc_id_upper.replace("-", ""), f"{doc_id}.pdf")

def create_mock_zip(files):
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, 'w') as zf:
        for name, content in files.items():
            zf.writestr(name, content)
    return base64.b64encode(buf.getvalue()).decode('utf-8')

class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True
    allow_reuse_address = True

# --- PRODUCT REST API MOCK (Port 3003) ---
class ProductRestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path_parts = self.path.split('/')
        if "/api/animals/" in self.path and "directory" in path_parts:
            animal_id = path_parts[path_parts.index("animals") + 1]
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({"directoryId": f"DIR-ANIMAL-{animal_id}"}).encode())
            return
            
        elif "/api/directories/" in self.path and "tree" in path_parts:
            dir_id = path_parts[path_parts.index("directories") + 1]
            animal_id = dir_id.split("-")[-1]
            
            try:
                animal_id_int = int(animal_id)
            except ValueError:
                animal_id_int = 0
                
            # Dynamic document tree generation for animal scenarios
            if 101 <= animal_id_int <= 116:
                idx = animal_id_int - 100
                doc_id = f"DOC-ANIMAL-OK-{idx:02d}"
            elif 201 <= animal_id_int <= 206:
                idx = animal_id_int - 200
                doc_id = f"DOC-ANIMAL-RT-{idx:02d}"
            elif 301 <= animal_id_int <= 312:
                idx = animal_id_int - 300
                doc_id = f"DOC-ANIMAL-BR-{idx:02d}"
            elif 401 <= animal_id_int <= 416:
                idx = animal_id_int - 400
                doc_id = f"DOC-ANIMAL-TE-{idx:02d}"
            elif animal_id == "300":
                # Fallback / keeping original bird scenarios
                children = [
                    {
                        "id": "doc-animal-300-ok-retry",
                        "name": "animal_retry_timeout.pdf",
                        "source": 1,
                        "businessDocumentId": "DOC-ANIMAL-300-04",
                        "productId": "300",
                        "children": []
                    },
                    {
                        "id": "doc-animal-300-unsupported",
                        "name": "bird_unsupported.exe",
                        "source": 1,
                        "businessDocumentId": "DOC-ANIMAL-300-02",
                        "productId": "300",
                        "children": []
                    },
                    {
                        "id": "doc-animal-300-oversized",
                        "name": "bird_oversized.pdf",
                        "source": 1,
                        "businessDocumentId": "DOC-ANIMAL-300-03",
                        "productId": "300",
                        "children": []
                    }
                ]
                tree = {
                    "id": f"node-{dir_id}",
                    "name": f"directory-{dir_id}",
                    "source": None,
                    "businessDocumentId": None,
                    "productId": None,
                    "children": children
                }
                self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
                self.wfile.write(json.dumps(tree).encode())
                return
            else:
                doc_id = f"DOC-ANIMAL-{animal_id}-01"
                
            filename = get_filename_for_doc(doc_id)
            
            # Special case: BR-07 is a duplicate document (2 duplicates)
            if animal_id_int == 307:
                children = [
                    {
                        "id": "doc-animal-307-1",
                        "name": filename,
                        "source": 1,
                        "businessDocumentId": doc_id,
                        "productId": str(animal_id),
                        "children": []
                    },
                    {
                        "id": "doc-animal-307-2",
                        "name": filename,
                        "source": 1,
                        "businessDocumentId": doc_id,
                        "productId": str(animal_id),
                        "children": []
                    }
                ]
            else:
                children = [
                    {
                        "id": f"doc-animal-{animal_id}",
                        "name": filename,
                        "source": 1,
                        "businessDocumentId": doc_id,
                        "productId": str(animal_id),
                        "children": []
                    }
                ]
                
            tree = {
                "id": f"node-{dir_id}",
                "name": f"directory-{dir_id}",
                "source": None,
                "businessDocumentId": None,
                "productId": None,
                "children": children
            }
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps(tree).encode())
            return

        elif "/api/products/" in self.path and path_parts[-1] == "documents":
            product_id = path_parts[path_parts.index("products") + 1]
            
            # Simulated HTTP errors on Sync
            if product_id == "SC-TE-11":
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b"Product sync internal server error")
                return
            if product_id == "SC-TE-12":
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Product sync not found")
                return
            
            scenarios = {
                "SC-OK-01": [{"documentId": "DOC-OK-01", "filename": "manual.pdf"}],
                "SC-OK-02": [{"documentId": "DOC-OK-02", "filename": "large_valid.pdf"}],
                "SC-OK-03": [{"documentId": "DOC-OK-03", "filename": "guide.docx"}],
                "SC-OK-04": [{"documentId": "DOC-OK-04", "filename": "notes.txt"}],
                "SC-OK-05": [{"documentId": "DOC-OK-05", "filename": "bundle.zip"}],
                "SC-OK-06": [{"documentId": "DOC-OK-06", "filename": "user_manual.pdf"}],
                "SC-OK-07": [{"documentId": "DOC-OK-07", "filename": "brazil_tech.docx"}],
                "SC-OK-08": [{"documentId": "DOC-OK-08", "filename": "arg_setup.txt"}],
                "SC-OK-09": [{"documentId": "DOC-OK-09", "filename": "chile_safe.pdf"}],
                "SC-OK-10": [{"documentId": "DOC-OK-10", "filename": "peru_qa.pdf"}],
                "SC-OK-11": [{"documentId": "DOC-OK-11", "filename": "multi_type.zip"}],
                "SC-OK-12": [{"documentId": "DOC-OK-12", "filename": "nested.zip"}],
                "SC-OK-13": [{"documentId": "DOC-OK-13", "filename": "mexico_user.docx"}],
                "SC-OK-14": [{"documentId": "DOC-OK-14", "filename": "col_compressed.zip"}],
                "SC-OK-15": [{"documentId": "DOC-OK-15", "filename": "partial.zip"}],
                "SC-OK-16": [{"documentId": "DOC-OK-16", "filename": "fail_all.zip"}],
                "SC-RT-01": [{"documentId": "DOC-RT-01", "filename": "retry_timeout.pdf"}],
                "SC-RT-02": [{"documentId": "DOC-RT-02", "filename": "retry_500.pdf"}],
                "SC-RT-03": [{"documentId": "DOC-RT-03", "filename": "retry_503.pdf"}],
                "SC-RT-04": [{"documentId": "DOC-RT-04", "filename": "retry_429.pdf"}],
                "SC-RT-05": [{"documentId": "DOC-RT-05", "filename": "retry_504.pdf"}],
                "SC-RT-06": [{"documentId": "DOC-RT-06", "filename": "retry_503_success.pdf"}],
                "SC-BR-01": [{"documentId": "DOC-BR-01", "filename": "too_large.pdf"}],
                "SC-BR-02": [{"documentId": "DOC-BR-02", "filename": "virus.exe"}],
                "SC-BR-03": [{"documentId": "DOC-BR-03", "filename": "script.sh"}],
                "SC-BR-04": [{"documentId": "DOC-BR-04", "filename": "WRONG_NAME.pdf"}],
                "SC-BR-05": [{"documentId": "DOC-BR-05", "filename": "empty.zip"}],
                "SC-BR-06": [{"documentId": "DOC-BR-06", "filename": "mixed_content.zip"}],
                "SC-BR-07": [
                    {"documentId": "DOC-BR-07", "filename": "duplicate.pdf"},
                    {"documentId": "DOC-BR-07", "filename": "duplicate.pdf"}
                ],
                "SC-BR-08": [{"documentId": "DOC-BR-08", "filename": "oversized_inner.zip"}],
                "SC-BR-09": [{"documentId": "DOC-BR-09", "filename": "unsupported_inner.zip"}],
                "SC-BR-10": [{"documentId": "DOC-BR-10", "filename": "empty_inner.zip"}],
                "SC-BR-11": [{"documentId": "DOC-BR-11", "filename": "corrupted.zip"}],
                "SC-BR-12": [{"documentId": "DOC-BR-12", "filename": "no_sucursal.pdf"}],
                "SC-TE-01": [{"documentId": "DOC-TE-01", "filename": "soap_fault.pdf"}],
                "SC-TE-02": [{"documentId": "DOC-TE-02", "filename": "malformed.pdf"}],
                "SC-TE-03": [{"documentId": "DOC-TE-03", "filename": "empty_resp.pdf"}],
                "SC-TE-04": [{"documentId": "DOC-TE-04", "filename": "unauthorized.pdf"}],
                "SC-TE-05": [{"documentId": "DOC-TE-05", "filename": "forbidden.pdf"}],
                "SC-TE-06": [{"documentId": "DOC-TE-06", "filename": "bad_request.pdf"}],
                "SC-TE-07": [{"documentId": "DOC-TE-07", "filename": "corrupted.pdf"}],
                "SC-TE-08": [{"documentId": "DOC-TE-08", "filename": "persistent_500.pdf"}],
                "SC-TE-09": [{"documentId": "DOC-TE-09", "filename": "persistent_503.pdf"}],
                "SC-TE-10": [{"documentId": "DOC-TE-10", "filename": "persistent_504.pdf"}],
                "SC-TE-13": [{"documentId": "DOC-TE-13", "filename": "download_500.pdf"}],
                "SC-TE-14": [{"documentId": "DOC-TE-14", "filename": "download_404.pdf"}],
                "SC-TE-15": [{"documentId": "DOC-TE-15", "filename": "malformed_xml.pdf"}],
                "SC-TE-16": [{"documentId": "DOC-TE-16", "filename": "soap_timeout_persistent.pdf"}]
            }
            response = scenarios.get(product_id, [])
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps(response).encode())
            
        elif "/api/products/" in self.path and "documents" in path_parts:
            doc_id = path_parts[-1]
            doc_id_clean = doc_id.replace("-", "").upper()
            
            # Simulated HTTP errors on Download
            if doc_id_clean == "DOCTE13":
                self.send_response(500)
                self.end_headers()
                self.wfile.write(b"Download internal server error")
                return
            if doc_id_clean == "DOCTE14":
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"Download not found")
                return
            
            content = "U09BUCB0ZXN0IGNvbnRlbnQ=" # "SOAP test content" in base64
            size = 100
            # Use the filenames defined in scenarios to trigger SOAP mock failures
            filename_map = {
                "DOC-RT-01": "retry_timeout.pdf", "DOC-RT-02": "retry_500.pdf",
                "DOC-RT-03": "retry_503.pdf", "DOC-RT-04": "retry_429.pdf", "DOC-RT-05": "retry_504.pdf",
                "DOC-RT-06": "retry_503_success.pdf",
                "DOC-TE-01": "soap_fault.pdf", "DOC-TE-02": "malformed.pdf", "DOC-TE-03": "empty_resp.pdf",
                "DOC-TE-04": "unauthorized.pdf", "DOC-TE-05": "forbidden.pdf", "DOC-TE-06": "bad_request.pdf",
                "DOC-TE-07": "corrupted.pdf", "DOC-BR-02": "virus.exe", "DOC-BR-03": "script.sh",
                "DOC-BR-04": "WRONG_NAME.pdf", "DOC-OK-01": "manual.pdf", "DOC-OK-02": "large_valid.pdf",
                "DOC-OK-03": "guide.docx", "DOC-OK-04": "notes.txt", "DOC-OK-05": "bundle.zip",
                "DOC-OK-06": "user_manual.pdf", "DOC-OK-07": "brazil_tech.docx", "DOC-OK-08": "arg_setup.txt",
                "DOC-OK-09": "chile_safe.pdf", "DOC-OK-10": "peru_qa.pdf", "DOC-OK-11": "multi_type.zip",
                "DOC-OK-12": "nested.zip", "DOC-OK-13": "mexico_user.docx", "DOC-OK-14": "col_compressed.zip",
                "DOC-OK-15": "partial.zip", "DOC-OK-16": "fail_all.zip",
                "DOC-BR-07": "duplicate.pdf", "DOC-BR-08": "oversized_inner.zip", "DOC-BR-09": "unsupported_inner.zip",
                "DOC-BR-10": "empty_inner.zip", "DOC-BR-11": "corrupted.zip", "DOC-BR-12": "no_sucursal.pdf",
                "DOC-TE-08": "persistent_500.pdf", "DOC-TE-09": "persistent_503.pdf", "DOC-TE-10": "persistent_504.pdf",
                "DOC-TE-13": "download_500.pdf", "DOC-TE-14": "download_404.pdf",
                "DOC-TE-15": "malformed_xml.pdf", "DOC-TE-16": "soap_timeout_persistent.pdf",
                "DOC-ANIMAL-100-01": "animal_100_health.pdf", "DOC-ANIMAL-200-01": "animal_200_health.pdf",
                "DOC-ANIMAL-300-02": "bird_unsupported.exe", "DOC-ANIMAL-300-03": "bird_oversized.pdf",
                "DOC-ANIMAL-300-04": "animal_retry_timeout.pdf"
            }
            
            filename = get_filename_for_doc(doc_id)
            
            if "DOCOK05" in doc_id_clean:
                content = create_mock_zip({"f1.pdf": b"c1", "f2.pdf": b"c2", "f3.pdf": b"c3"})
                filename = "bundle.zip"
            elif "DOCOK11" in doc_id_clean:
                content = create_mock_zip({"f1.pdf": b"c1", "f2.docx": b"c2", "f3.txt": b"c3"})
                filename = "multi_type.zip"
            elif "DOCOK12" in doc_id_clean:
                content = create_mock_zip({"docs/subfolder/file.pdf": b"c1"})
                filename = "nested.zip"
            elif "DOCOK14" in doc_id_clean:
                content = create_mock_zip({"inner1.pdf": b"inner_content_1", "inner2.pdf": b"inner_content_2"})
                filename = "col_compressed.zip"
            elif "DOCOK15" in doc_id_clean:
                content = create_mock_zip({"inner_ok.pdf": b"c1", "inner_fail_soap.pdf": b"c2"})
                filename = "partial.zip"
            elif "DOCOK16" in doc_id_clean:
                content = create_mock_zip({"inner_fail_soap1.pdf": b"c1", "inner_fail_soap2.pdf": b"c2"})
                filename = "fail_all.zip"
            elif "DOCBR06" in doc_id_clean:
                content = create_mock_zip({"f1.exe": b"virus", "f2.sh": b"script"})
                filename = "mixed_content.zip"
            elif "DOCBR05" in doc_id_clean:
                content = create_mock_zip({})
                filename = "empty.zip"
            elif "DOCBR08" in doc_id_clean:
                content = create_mock_zip({"large_inner.pdf": b"a" * 11 * 1024 * 1024}) # 11MB file (exceeds 10MB limit)
                filename = "oversized_inner.zip"
            elif "DOCBR09" in doc_id_clean:
                content = create_mock_zip({"malicious.exe": b"virus"})
                filename = "unsupported_inner.zip"
            elif "DOCBR10" in doc_id_clean:
                content = create_mock_zip({"empty.pdf": b""})
                filename = "empty_inner.zip"
            elif "DOCBR11" in doc_id_clean:
                content = base64.b64encode(b"invalid_zip_container_data").decode('utf-8')
                filename = "corrupted.zip"
            elif "DOCOK02" in doc_id_clean:
                size = 9 * 1024 * 1024 
            elif "DOCBR01" in doc_id_clean or "DOCANIMAL30003" in doc_id_clean:
                size = 20 * 1024 * 1024 
            elif "DOCBR02" in doc_id_clean: filename = "virus.exe"
            elif "DOCBR03" in doc_id_clean: filename = "script.sh"
            elif "DOCANIMAL30002" in doc_id_clean: filename = "bird_unsupported.exe"
            elif "DOCTE07" in doc_id_clean: content = "NOT_BASE64_AT_ALL_!!!!"

            # Determine dynamic origin and pais for homologation
            origin = "PORTAL"
            pais = "CO"
            product_id = "unknown"
            if "products" in path_parts:
                product_id = path_parts[path_parts.index("products") + 1]
            product_id_clean = product_id.replace("-", "").upper()

            if "SCOK06" in product_id_clean or "DOCOK06" in doc_id_clean or "DOCOK13" in doc_id_clean:
                origin = "usuario"
                pais = "MX"
            elif "SCOK07" in product_id_clean or "DOCOK07" in doc_id_clean:
                origin = "tecnico"
                pais = "BR"
            elif "SCOK08" in product_id_clean or "DOCOK08" in doc_id_clean:
                origin = "setup"
                pais = "AR"
            elif "SCOK09" in product_id_clean or "DOCOK09" in doc_id_clean:
                origin = "safe"
                pais = "CL"
            elif "SCOK10" in product_id_clean or "DOCOK10" in doc_id_clean:
                origin = "qa"
                pais = "PE"
            elif "DOCANIMAL10001" in doc_id_clean or "DOCANIMAL30003" in doc_id_clean or "DOCANIMAL30004" in doc_id_clean:
                origin = "garantia"
                pais = "CO"
            elif "DOCANIMAL20001" in doc_id_clean or "DOCANIMAL30002" in doc_id_clean:
                origin = "user"
                pais = "MX"

            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.end_headers()
            self.wfile.write(json.dumps({
                "documentId": doc_id, "filename": filename,
                "contentType": "application/pdf" if not filename.endswith(".zip") else "application/zip",
                "content": content, "size": size, 
                "origin": origin, "pais": pais
            }).encode())

# --- SOAP API MOCK (Port 9003) ---
class SoapHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length).decode('utf-8', errors='ignore')
        
        # Flexible regex to find nombreArchivo even with namespaces
        match = re.search(r'<(?:.*:)?nombreArchivo>(.*?)</(?:.*:)?nombreArchivo>', post_data)
        doc_key = match.group(1) if match else "unknown"
        
        with retry_lock:
            retry_counts[doc_key] = retry_counts.get(doc_key, 0) + 1
            attempt = retry_counts[doc_key]
        
        print(f"[SOAP] Request received for: {doc_key} (Attempt: {attempt})", flush=True)
        if doc_key == "unknown":
            print(f"   [DEBUG] Raw Body Snippet: {post_data[:200]}...", flush=True)

        # Transient Failure Scenarios (Fail 1st and 2nd attempt)
        if attempt <= 2:
            if "retry_timeout" in doc_key:
                print(f"   -> Simulating TIMEOUT (8s sleep) for {doc_key}", flush=True)
                time.sleep(8); return
            if "retry_500" in doc_key:
                print(f"   -> Simulating HTTP 500 for {doc_key}", flush=True)
                self.send_response(500); self.end_headers(); self.wfile.write(b"Server Error"); return
            if "retry_503" in doc_key or "retry_503_success" in doc_key:
                print(f"   -> Simulating HTTP 503 for {doc_key}", flush=True)
                self.send_response(503); self.end_headers(); self.wfile.write(b"Service Unavailable"); return
            if "retry_429" in doc_key:
                print(f"   -> Simulating HTTP 429 for {doc_key}", flush=True)
                self.send_response(429); self.end_headers(); self.wfile.write(b"Rate Limited"); return
            if "retry_504" in doc_key:
                print(f"   -> Simulating HTTP 504 for {doc_key}", flush=True)
                self.send_response(504); self.end_headers(); self.wfile.write(b"Gateway Timeout"); return

        # Persistent/Permanent HTTP failures
        if "inner_fail_soap" in doc_key:
            print(f"   -> Simulating SOAP failure for inner file {doc_key}", flush=True)
            self.send_response(500); self.end_headers(); self.wfile.write(b"SOAP failure for inner file"); return
        if "persistent_500" in doc_key:
            print(f"   -> Simulating PERSISTENT HTTP 500 for {doc_key}", flush=True)
            self.send_response(500); self.end_headers(); self.wfile.write(b"Persistent Server Error"); return
        if "persistent_503" in doc_key:
            print(f"   -> Simulating PERSISTENT HTTP 503 for {doc_key}", flush=True)
            self.send_response(503); self.end_headers(); self.wfile.write(b"Persistent Service Unavailable"); return
        if "persistent_504" in doc_key:
            print(f"   -> Simulating PERSISTENT HTTP 504 for {doc_key}", flush=True)
            self.send_response(504); self.end_headers(); self.wfile.write(b"Persistent Gateway Timeout"); return
        if "soap_timeout_persistent" in doc_key:
            print(f"   -> Simulating persistent TIMEOUT (8s sleep) for {doc_key}", flush=True)
            time.sleep(8); return

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
        if "malformed_xml" in doc_key:
            print(f"   -> Simulating persistent malformed XML for {doc_key}", flush=True)
            self.send_response(200); self.end_headers(); self.wfile.write(b"<xml>malformed_and_broken"); return
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
    threading.Thread(target=run_server, args=(ProductRestHandler, 3003), daemon=True).start()
    threading.Thread(target=run_server, args=(SoapHandler, 9003), daemon=True).start()
    print("Mocks started. 45 Scenarios active.")
    try:
        while True: time.sleep(1)
    except KeyboardInterrupt: pass
