#!/usr/bin/env bash
# ============================================================
# generate_keystore.sh
# Run this ONCE locally to create your signing keystore,
# then add the base64 output to your GitHub secrets.
# ============================================================
set -e

KEYSTORE_FILE="verifysphere-release.jks"
KEY_ALIAS="verifysphere"
VALIDITY_DAYS=10000   # ~27 years

echo ""
echo "=== VerifySphere Keystore Generator ==="
echo ""
echo "You will be prompted for:"
echo "  • Keystore password (remember this!)"
echo "  • Key password (can be same as keystore)"
echo "  • Your name / organisation details"
echo ""

# Generate the keystore
keytool -genkey \
  -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity "$VALIDITY_DAYS"

echo ""
echo "✓ Keystore created: $KEYSTORE_FILE"
echo ""
echo "=== Base64 encode for GitHub Secrets ==="
echo ""
BASE64=$(base64 -w 0 "$KEYSTORE_FILE" 2>/dev/null || base64 "$KEYSTORE_FILE")
echo "$BASE64" > "${KEYSTORE_FILE}.b64"
echo "✓ Base64 saved to: ${KEYSTORE_FILE}.b64"
echo ""
echo "=== GitHub Secrets to set ==="
echo ""
echo "Go to: GitHub repo → Settings → Secrets and variables → Actions → New repository secret"
echo ""
echo "  Secret name       Value"
echo "  ──────────────    ─────────────────────────────────────"
echo "  KEYSTORE_BASE64   (contents of ${KEYSTORE_FILE}.b64)"
echo "  KEYSTORE_PASSWORD (the keystore password you just entered)"
echo "  KEY_ALIAS         $KEY_ALIAS"
echo "  KEY_PASSWORD      (the key password you just entered)"
echo ""
echo "IMPORTANT: Keep $KEYSTORE_FILE safe — you need it to update the app!"
echo "           If you lose it, you cannot publish updates to the same app."
echo ""
