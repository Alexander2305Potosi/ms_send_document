import json
import sys

def get_ordered_keys(data, table_name):
    if not data: return []
    all_keys = set().union(*(d.keys() for d in data))
    
    preferred_orders = {
        "DOCUMENTOS": [
            "ID", "ID_DOCUMENTO", "ID_PRODUCTO", "NOMBRE", "ESTADO", 
            "ES_ZIP", "CASO_USO", "REINTENTOS", "MENSAJE_ERROR", "FECHA_CREACION", "FECHA_ACTUALIZACION"
        ],
        "HISTORICO_DOCUMENTOS": [
            "ID", "DOCUMENTO_ID", "NOMBRE_ARCHIVO", "OPERACION", "RESULTADO", 
            "CODIGO_ERROR", "REINTENTOS", "MENSAJE_ERROR", "FECHA_INICIO", "FECHA_FIN"
        ],
        "PRODUCTOS_MAESTROS": [
            "ID", "ID_PRODUCTO", "NOMBRE", "ESTADO", "FECHA_CARGUE"
        ]
    }
    
    order = preferred_orders.get(table_name, [])
    ordered_keys = [k for k in order if k in all_keys]
    remaining_keys = sorted([k for k in all_keys if k not in order])
    
    return ordered_keys + remaining_keys

def print_table(title, data, table_key):
    if not data:
        print(f"\n--- {title} (Empty) ---\n")
        return

    keys = get_ordered_keys(data, table_key)
    
    # Calculate column widths
    widths = {k: len(k) for k in keys}
    for row in data:
        for k in keys:
            val = str(row.get(k, "") or "")
            if len(val) > 25: val = val[:22] + "..."
            widths[k] = max(widths[k], len(val))

    # Print Header
    print(f"\n--- {title} ---")
    header = " | ".join(k.ljust(widths[k]) for k in keys)
    print(header)
    print("-" * len(header))

    # Print Rows
    for row in data:
        parts = []
        for k in keys:
            val = str(row.get(k, "") or "").replace("\n", " ")
            if len(val) > 25: val = val[:22] + "..."
            parts.append(val.ljust(widths[k]))
        print(" | ".join(parts))

if __name__ == "__main__":
    try:
        raw_data = sys.stdin.read()
        if not raw_data:
            print("No data received for formatting.")
            sys.exit(0)
            
        full_dump = json.loads(raw_data)
        
        # 1. Documentos
        print_table("TABLE: DOCUMENTOS", full_dump.get("documentos", []), "DOCUMENTOS")
        
        # 2. Historico Documentos
        print_table("TABLE: HISTORICO_DOCUMENTOS", full_dump.get("historico_documentos", []), "HISTORICO_DOCUMENTOS")
        
        # 3. Master Products
        print_table("TABLE: PRODUCTOS_MAESTROS", full_dump.get("productos_maestros", []), "PRODUCTOS_MAESTROS")
        
    except Exception as e:
        print(f"Error formatting table: {e}")
