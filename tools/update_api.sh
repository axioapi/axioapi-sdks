#!/usr/bin/env bash
# One step after any API change: rebuild the operation registry from the app and copy it into every SDK.
# No SDK code changes when endpoints are added, removed or renamed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

php "$ROOT/sdk/tools/generate_registry.php"
python "$ROOT/sdk/tools/sync_spec.py"

echo "Registry updated. Run: bash sdk/tools/test_all.sh"
