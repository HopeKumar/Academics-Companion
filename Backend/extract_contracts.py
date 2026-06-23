import os
import re
import json

def extract_fields_from_java(content):
    fields = []
    # Match: private Type name; or private List<Type> name;
    pattern = re.compile(r'private\s+([\w<>,\s]+)\s+(\w+)(?:\s*=\s*[^;]+)?\s*;')
    for match in pattern.finditer(content):
        field_type = match.group(1).strip()
        field_name = match.group(2).strip()
        fields.append({'name': field_name, 'type': field_type})
    return fields

def process_directory(base_path):
    schemas = {}
    for root, dirs, files in os.walk(base_path):
        for file in files:
            if file.endswith('.java'):
                filepath = os.path.join(root, file)
                with open(filepath, 'r') as f:
                    content = f.read()
                
                class_pattern = re.search(r'(?:public|protected|private)?\s*(?:static\s+)?class\s+(\w+)', content)
                if class_pattern:
                    class_name = class_pattern.group(1)
                    fields = extract_fields_from_java(content)
                    
                    if fields:
                        properties = {}
                        for field in fields:
                            properties[field['name']] = {'type': field['type']}
                        
                        schemas[class_name] = {
                            'type': 'object',
                            'properties': properties
                        }
    return schemas

def to_yaml(d, indent=0):
    yaml_str = ""
    for k, v in d.items():
        if isinstance(v, dict):
            yaml_str += "  " * indent + f"{k}:\n" + to_yaml(v, indent + 1)
        elif isinstance(v, str):
            yaml_str += "  " * indent + f"{k}: {v}\n"
        else:
            yaml_str += "  " * indent + f"{k}: {v}\n"
    return yaml_str

def main():
    base_dir = "src/main/java/com/lms"
    target_dirs = ["dto", "model", "controller", "request", "response"]
    
    all_schemas = {}
    for d in target_dirs:
        path = os.path.join(base_dir, d)
        if os.path.exists(path):
            all_schemas.update(process_directory(path))
            
    output = {
        'openapi': '3.0.1',
        'info': {
            'title': 'CampusLM API Contracts',
            'version': '1.0'
        },
        'components': {
            'schemas': all_schemas
        }
    }
    
    os.makedirs("../docs", exist_ok=True)
    with open("../docs/api-dto-contracts.yaml", "w") as f:
        f.write(to_yaml(output))
    
    print("Extracted contracts successfully to docs/api-dto-contracts.yaml")

if __name__ == "__main__":
    main()
