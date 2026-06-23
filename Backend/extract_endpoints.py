import os
import re
import json

base_dir = "src/main/java/com/lms/controller"
endpoints = {}

for filename in os.listdir(base_dir):
    if filename.endswith("Controller.java"):
        filepath = os.path.join(base_dir, filename)
        with open(filepath, "r") as f:
            content = f.read()
            
        base_mapping = re.search(r'@RequestMapping\("([^"]+)"\)', content)
        base_path = base_mapping.group(1) if base_mapping else ""
        
        methods = re.findall(r'@(Post|Get|Put|Delete)Mapping\((?:value\s*=\s*)?((?:\{)?"[^"]+"(?:,\s*"[^"]+")*(?:\})?)?\)', content)
        
        if filename not in endpoints:
            endpoints[filename] = []
            
        for method, path in methods:
            if not path:
                path = '""'
            clean_path = path.replace('"', '').strip()
            endpoints[filename].append(f"{method.upper()} {base_path}{clean_path}")

print(json.dumps(endpoints, indent=2))
