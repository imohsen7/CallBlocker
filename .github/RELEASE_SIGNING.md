# Release signing

The public/store package is `ir.imohsen.callblocker`.

## 1. Generate the permanent keystore once

Run locally and keep the resulting file in a secure backup. Losing this key prevents publishing updates under the same app identity.

```bash
keytool -genkeypair \
  -v \
  -keystore callblocker-release.jks \
  -alias callblocker \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Do not commit `callblocker-release.jks` to Git.

## 2. Convert the keystore to base64

macOS:

```bash
base64 -i callblocker-release.jks | tr -d '\n' > callblocker-release.base64.txt
```

Linux:

```bash
base64 -w 0 callblocker-release.jks > callblocker-release.base64.txt
```

## 3. Add GitHub Actions secrets

Repository → Settings → Secrets and variables → Actions → New repository secret

Create these four secrets:

- `RELEASE_KEYSTORE_BASE64`: contents of `callblocker-release.base64.txt`
- `RELEASE_STORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: alias used when creating the key, e.g. `callblocker`
- `RELEASE_KEY_PASSWORD`: key password

## 4. Build

Run the `Build Release APK` workflow.

When all four secrets are present, the artifact is named `CallBlocker-store-release` and is signed with the permanent key.

If the secrets are missing, CI creates `CallBlocker-preview-release` with a temporary key. Preview builds are installable for testing but must never be uploaded to Cafe Bazaar or another store because the key changes between runs.

## Important

Always keep at least two secure backups of the permanent `.jks` file and its passwords. Every future update of the published application must be signed with the same key.
