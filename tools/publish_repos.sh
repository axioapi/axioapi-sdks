#!/usr/bin/env bash
# Mirrors sdk/ to GitHub: one repo per language plus the combined axioapi-sdks repo.
# Usage: bash sdk/tools/publish_repos.sh [--create] [--public]
#   --create  create missing repos first (needs `gh auth login`); private unless --public
set -euo pipefail

ORG="${AXIOAPI_GH_ORG:-axioapi}"
SDK_DIR="$(cd "$(dirname "$0")/.." && pwd)"   # sdk/ is its own git repo (origin = axioapi-sdks)
GH="${GH_BIN:-gh}"
CREATE=false
VISIBILITY="--private"
for arg in "$@"; do
    case "$arg" in
        --create) CREATE=true ;;
        --public) VISIBILITY="--public" ;;
    esac
done

# folder:repo:Language:topic
LANGS=(
    "python:axioapi-python:Python:python"
    "node:axioapi-node:Node.js:nodejs"
    "php:axioapi-php:PHP:php"
    "go:axioapi-go:Go:golang"
    "ruby:axioapi-ruby:Ruby:ruby"
    "java:axioapi-java:Java:java"
    "csharp:axioapi-dotnet:C# and .NET:dotnet"
)
TOPICS="api,api-client,sdk,seo-api,backlink-api,keyword-api,temp-mail-api,sms-verification-api,otp-api,email-validation-api,proxy-api,scraper-api"
DESCRIPTION_TAIL="SDK for the AxioAPI REST API: temp mail API, receive SMS and OTP API, email validation API, proxy API, SEO API (keyword data and backlink API) and social scraper APIs. One API key."

create_repo() {
    local repo="$1" description="$2" extra_topic="$3"
    if "$GH" repo view "$ORG/$repo" >/dev/null 2>&1; then
        echo "exists  $ORG/$repo"
        return
    fi
    "$GH" repo create "$ORG/$repo" "$VISIBILITY" --description "$description" --homepage "https://axioapi.com" >/dev/null
    "$GH" repo edit "$ORG/$repo" --add-topic "${TOPICS},${extra_topic}" >/dev/null
    echo "created $ORG/$repo"
}

push_prefix() {
    local prefix="$1" repo="$2" branch="split-$2"
    git -C "$SDK_DIR" branch -D "$branch" >/dev/null 2>&1 || true
    git -C "$SDK_DIR" subtree split --prefix="$prefix" -b "$branch" >/dev/null
    git -C "$SDK_DIR" push --force "https://github.com/$ORG/$repo.git" "$branch:main"
    git -C "$SDK_DIR" branch -D "$branch" >/dev/null
    echo "pushed  $prefix -> $ORG/$repo"
}

if [ -n "$(git -C "$SDK_DIR" status --porcelain)" ]; then
    echo "Commit your changes inside sdk/ first: only committed history is published." >&2
    exit 1
fi

for entry in "${LANGS[@]}"; do
    IFS=: read -r folder repo language topic <<<"$entry"
    "$CREATE" && create_repo "$repo" "Official $language $DESCRIPTION_TAIL" "$topic"
    push_prefix "$folder" "$repo"
done

"$CREATE" && create_repo "axioapi-sdks" "Official SDKs for the AxioAPI REST API in Python, Node.js, PHP, Go, Ruby, Java and C#: temp mail API, SMS OTP API, email validation API, proxy API, SEO API (keyword data and backlink API). One API key." "sdks"
git -C "$SDK_DIR" push --force "https://github.com/$ORG/axioapi-sdks.git" HEAD:main
echo "pushed  sdk -> $ORG/axioapi-sdks"
