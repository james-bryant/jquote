#!/usr/bin/env bash
set -euo pipefail

KEYSTORE_PATH="${JQUOTE_KEYSTORE_PATH:-$HOME/.jquote/jquote-keystore.p12}"
PASSWORD="${JQUOTE_KEYSTORE_PASSWORD:-changeit}"
FORCE="${FORCE:-false}"

if [[ -f "$KEYSTORE_PATH" && "$FORCE" != "true" ]]; then
  echo "Keystore already exists: $KEYSTORE_PATH. Set FORCE=true to overwrite." >&2
  exit 1
fi

mkdir -p "$(dirname "$KEYSTORE_PATH")"
if [[ -f "$KEYSTORE_PATH" ]]; then
  rm -f "$KEYSTORE_PATH"
fi

if ! command -v keytool >/dev/null 2>&1; then
  echo "keytool not found. Install a JDK and ensure keytool is on PATH." >&2
  exit 1
fi

keytool -genkeypair -alias jquote -keyalg RSA -keysize 2048 -validity 3650 -storetype PKCS12 \
  -keystore "$KEYSTORE_PATH" -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -dname "CN=127.0.0.1, OU=JQuote, O=JQuote, L=Local, S=Local, C=US" \
  -ext "SAN=DNS:localhost,IP:127.0.0.1"

echo "Created keystore at $KEYSTORE_PATH"
echo "Set JQUOTE_SSL_KEYSTORE and JQUOTE_SSL_KEYSTORE_PASSWORD if you changed the defaults."
