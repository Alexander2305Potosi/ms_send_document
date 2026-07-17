import json
import sys

def get_ordered_keys(data, table_name):
    if not data: return []
    all_keys = set().union(*(d.keys() for d in data))
    
    preferred_orders = {
        "DOCUMENTOS": [
            "ID", "ID_DOCUMENTO", "ID_PRODUCTO", "NOMBRE_DOCUMENTO", "ESTADO", 
            "ES_ZIP", "CASO_USO", "REINTENTOS", "MENSAJE_SINCRONIZACION", "FECHA_CARGA", "FECHA_CARGA_ACTUALIZACION"
        ],
        "HISTORICO_DOCUMENTOS": [
            "ID", "ID_DOCUMENTOS", "NOMBRE_DOCUMENTO", "CASO_USO", "RESULTADO", 
            "CODIGO_ERROR", "REINTENTOS", "MENSAJE_SINCRONIZACION", "FECHA_INICIO_PROCESAMIENTO", "FECHA_FIN_PROCESAMIENTO"
        ],
        "PRODUCTOS_MAESTROS": [
            "ID", "ID_PRODUCTO", "NOMBRE", "ESTADO", "FECHA_CARGUE"
        ],
        "ANIMAL_DOCUMENTOS": [
            "ID", "ID_DOCUMENTO", "ID_ANIMAL", "NOMBRE_DOCUMENTO", "ESTADO_SINCRONIZACION",
            "ES_ZIP", "CASO_USO", "REINTENTOS", "MENSAJE_SINCRONIZACION", "FECHA_CARGA"
        ],
        "ANIMAL_HISTORICO": [
            "ID", "ID_DOCUMENTOS", "NOMBRE_DOCUMENTO", "CASO_USO", "RESULTADO",
            "ESTADO_SINCRONIZACION", "REINTENTOS", "MENSAJE_SINCRONIZACION", "FECHA_INICIO_PROCESAMIENTO", "FECHA_FIN_PROCESAMIENTO"
        ],
        "ANIMAL_MAESTROS": [
            "ID", "NAME", "CATEGORY"
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
            raw_val = row.get(k)
            val = str(raw_val if raw_val is not None else "")
            # if len(val) > 25: val = val[:22] + "..."
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
            raw_val = row.get(k)
            val = str(raw_val if raw_val is not None else "").replace("\n", " ")
            # if len(val) > 25: val = val[:22] + "..."
            parts.append(val.ljust(widths[k]))
        print(" | ".join(parts))

if __name__ == "__main__":
    try:
        raw_data = sys.stdin.read()
        if not raw_data:
            print("No data received for formatting.")
            sys.exit(0)
            
        full_dump = json.loads(raw_data)
        
        # 1. Documentos (Productos)
        print_table("TABLE: PRODUCTO_DOCUMENTOS", full_dump.get("documentos", []), "DOCUMENTOS")
        
        # 2. Historico Documentos (Productos)
        print_table("TABLE: PRODUCTO_HISTORICO_DOCUMENTOS", full_dump.get("historico_documentos", []), "HISTORICO_DOCUMENTOS")
        
        # 3. Master Products (Productos)
        print_table("TABLE: PRODUCTOS_MAESTROS", full_dump.get("productos_maestros", []), "PRODUCTOS_MAESTROS")

        # 4. Documentos (Animales)
        print_table("TABLE: ANIMAL_DOCUMENTOS (esquema_animales.documentos)", full_dump.get("animal_documentos", []), "ANIMAL_DOCUMENTOS")

        # 5. Historico Documentos (Animales)
        print_table("TABLE: ANIMAL_HISTORICO_DOCUMENTOS (esquema_animales.historico_documentos)", full_dump.get("animal_historico", []), "ANIMAL_HISTORICO")

        # 6. Master Animals (Animales)
        print_table("TABLE: ANIMALES_MAESTROS (schemAnimals.animals_maestro)", full_dump.get("animal_maestro", []), "ANIMAL_MAESTROS")
        
    except Exception as e:
        print(f"Error formatting table: {e}")
