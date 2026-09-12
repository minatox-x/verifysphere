# VerifySphere

> An Android app that handles `verifysphere://` deep-links, runs a Cloudflare Turnstile verification challenge, and delivers the encrypted token back to a callback URL in the system browser.

---

## How it works

```
Your app / web page
    │
    ▼  opens
verifysphere://verify?data=<AES-256 encrypted payload>
    │
    ▼  VerifySphere decrypts → extracts url, sitekey, callbackUrl
    │
    ▼  shows "Please wait…" screen + Turnstile dialog
    │
    ▼  on success → encrypts token → appends to callbackUrl
    │
    ▼  opens callbackUrl?token=<encrypted_base64> in system browser chooser
```

Everything sensitive (the site key, the callback URL, the raw token) is **never shown on screen**. The UI only shows "Please wait", ✓ success, or retry/error states.

---

## Payload format

The `data=` parameter is **Base64( AES-256-CBC( JSON ) )** where the wire format is `IV[16 bytes] || CipherText`.

The JSON inside must be:

```json
{
  "url":         "https://my-app.com",
  "sitekey":     "0x4AAAAAAA...",
  "callbackUrl": "https://my-app.com/callback?session=abc123"
}
```

| Field | Description |
|---|---|
| `url` | The domain your Cloudflare sitekey is registered for. Must start with `https://`. |
| `sitekey` | Your Cloudflare Turnstile public sitekey. |
| `callbackUrl` | Where to send the token. Existing query params are preserved; `&token=` is appended. |

---

## Token delivery

After the challenge passes, the app:

1. Encrypts the raw Turnstile token with the same AES-256 key (new random IV each time).
2. URL-encodes the result.
3. Opens `<callbackUrl>?token=<encrypted_base64>` (or `&token=` if the URL already has params) via a browser chooser intent.
4. Shows a fallback dialog with a **Copy URL** button in case no browser opens.

On your server, decrypt the `token` parameter with the same AES-256-CBC key to get the raw Turnstile token, then verify it against `https://challenges.cloudflare.com/turnstile/v0/siteverify`.

---

## Encryption key

The default key in `CryptoHelper.kt` is:

```
VerifySpherKey1AES256BitSecret!
```

(32 bytes → AES-256)

**You should replace this with your own random 32-byte key** before building. Edit the four `p1()–p4()` byte arrays in `CryptoHelper.kt` and update `KEY_BYTES` in `scripts/encrypt_payload.py` to match.

To generate a random key:
```bash
python3 -c "import os; k=os.urandom(32); print(list(k))"
```

---

## Repository setup

### 1. Generate a signing keystore

Run this **once** on your local machine (requires Java):

```bash
chmod +x scripts/generate_keystore.sh
./scripts/generate_keystore.sh
```

Follow the prompts. You'll get:
- `verifysphere-release.jks` — keep this safe forever
- `verifysphere-release.jks.b64` — base64 version for GitHub

### 2. Add GitHub Secrets

Go to **GitHub repo → Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | Contents of `verifysphere-release.jks.b64` |
| `KEYSTORE_PASSWORD` | Password you chose for the keystore |
| `KEY_ALIAS` | `verifysphere` |
| `KEY_PASSWORD` | Password you chose for the key |

### 3. Push a version tag to trigger the build

```bash
git add .
git commit -m "Initial release"
git tag v1.0.0
git push origin main --tags
```

GitHub Actions will:
1. Build the signed release APK with ProGuard/R8 obfuscation.
2. Upload it as a workflow artifact (always available, 30-day retention).
3. Create a GitHub Release with the APK attached (on version tags).

You can also trigger manually: **Actions → Build & Release APK → Run workflow**.

---

## Generating a deep-link (server side)

```bash
pip install pycryptodome
python3 scripts/encrypt_payload.py \
    --url      "https://my-app.com" \
    --sitekey  "0x4AAAAAAA..." \
    --callback "https://my-app.com/verify/callback?session=abc123&user=42"
```

Output:
```
verifysphere://verify?data=<base64_encrypted>
```

Use this as a clickable link or QR code in your existing app / web page.

### Test with the always-pass sitekey

```bash
python3 scripts/encrypt_payload.py \
    --url      "https://www.cloudflare.com" \
    --sitekey  "1x00000000000000000000AA" \
    --callback "https://httpbin.org/get"
```

Then on a connected device:
```bash
adb shell am start -a android.intent.action.VIEW \
    -d 'verifysphere://verify?data=<output from above>'
```

---

## Test sitekeys (Cloudflare)

| Sitekey | Behaviour |
|---|---|
| `1x00000000000000000000AA` | Always passes — no interaction needed |
| `2x00000000000000000000AB` | Always fails |
| `3x00000000000000000000FF` | Shows an interactive checkbox challenge |

Use `https://www.cloudflare.com` as the `url` for all test keys.

---

## Project structure

```
verifysphere/
├── .github/workflows/
│   └── release.yml              ← GitHub Actions CI/CD
├── app/
│   ├── libs/
│   │   └── turnstile-sdk-release.aar
│   ├── src/main/
│   │   ├── java/com/verifysphere/
│   │   │   ├── MainActivity.kt      ← UI + flow orchestration
│   │   │   ├── CryptoHelper.kt      ← AES-256-CBC encrypt/decrypt
│   │   │   ├── IntentPayload.kt     ← decrypted payload model
│   │   │   └── PayloadParser.kt     ← JSON → IntentPayload
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   └── values/{colors,strings,themes}.xml
│   │   └── AndroidManifest.xml      ← verifysphere:// intent filter
│   ├── build.gradle.kts
│   └── proguard-rules.pro           ← aggressive obfuscation
├── scripts/
│   ├── generate_keystore.sh         ← one-time keystore setup
│   └── encrypt_payload.py           ← generate deep-links server-side
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Security notes

- **Key obfuscation**: The AES key is split across four private methods in `CryptoHelper.kt`. With ProGuard's `-overloadaggressively` and `-repackageclasses ''`, these become single-character symbols in the release APK, making the key non-trivially hard to find via grep or simple string search. No security is absolute — a determined attacker with an Android reverse-engineering toolkit could still recover it — but for typical bot-protection use cases this is sufficient.
- **No disk writes**: Tokens are held in memory only and wiped in `onDestroy()`.
- **HTTPS only**: The network security config blocks all cleartext traffic.
- **No logging**: ProGuard strips all `Log.*` calls from the release build.
- **No UI leakage**: The decrypted URL, sitekey, callback URL, and raw token are never displayed on screen.

---

## License

MIT
