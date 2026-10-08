"""Copies sdk/spec/operations.json into every SDK package. Run after generate_registry.php."""

import shutil
from pathlib import Path

root = Path(__file__).resolve().parent.parent
source = root / "spec" / "operations.json"
targets = [
    "python/src/axioapi/operations.json",
    "node/src/operations.json",
    "php/src/operations.json",
    "go/operations.json",
    "ruby/lib/axioapi/operations.json",
    "java/src/main/resources/operations.json",
    "csharp/src/AxioAPI/operations.json",
]
for target in targets:
    path = root / target
    path.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, path)
    print("synced", target)
