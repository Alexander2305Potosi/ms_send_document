import os
import re
from collections import defaultdict

SRC_DIR = '/Users/alexander2305/Downloads/file-processor-service/src'

def get_java_files(dir_path):
    java_files = []
    for root, _, files in os.walk(dir_path):
        for file in files:
            if file.endswith('.java'):
                java_files.append(os.path.join(root, file))
    return java_files

def extract_declarations(content):
    declarations = {
        'classes': [],
        'methods': [],
        'fields': []
    }
    
    # Very basic regexes to capture declarations
    # Classes/Interfaces/Records
    class_matches = re.finditer(r'\b(?:class|interface|record|enum)\s+([A-Z]\w*)', content)
    for m in class_matches:
        declarations['classes'].append(m.group(1))
        
    # Methods (basic heuristic: return_type method_name(args) { )
    # This is simplified and might catch false positives/negatives, but useful as a starting point
    method_matches = re.finditer(r'\b(?:public|private|protected|static|final|abstract)?\s+[\w<>,\[\]\s]+\s+([a-z]\w*)\s*\(', content)
    for m in method_matches:
        name = m.group(1)
        if name not in ['if', 'for', 'while', 'switch', 'catch', 'return']:
            declarations['methods'].append(name)
            
    return declarations

def check_usages(java_files):
    all_declarations = defaultdict(list)
    file_contents = {}
    
    for f in java_files:
        with open(f, 'r') as file:
            content = file.read()
            file_contents[f] = content
            decls = extract_declarations(content)
            for k, v in decls.items():
                for item in v:
                    all_declarations[item].append(f)
                    
    results = []
    
    # Cross reference
    for decl_name, decl_files in all_declarations.items():
        used = False
        for f, content in file_contents.items():
            # Find occurrences
            occurrences = len(re.findall(r'\b' + re.escape(decl_name) + r'\b', content))
            if f in decl_files:
                # Deduct the declaration itself
                occurrences -= 1
            if occurrences > 0:
                used = True
                break
        if not used:
            for df in decl_files:
                results.append(f"Unused {decl_name} found in {df}")
                
    return results

files = get_java_files(SRC_DIR)
unused = check_usages(files)
for u in unused:
    print(u)
