# Cafe Bazaar release checklist

- [x] Production application ID: `ir.imohsen.callblocker`
- [x] Release build type with `debuggable=false` (Android release default)
- [x] Permanent signing supported through GitHub Actions secrets
- [x] Installable preview release available when store secrets are absent
- [x] APK signature verification in CI
- [x] Adaptive launcher icon
- [x] Privacy policy describing local call/contact processing
- [x] Android backup disabled for local rules/logs
- [x] No `INTERNET` permission, advertising SDK, or analytics SDK
- [x] `targetSdk 35`
- [ ] Generate and securely back up the permanent release keystore
- [ ] Add the four release-signing GitHub Secrets
- [ ] Run `Build Release APK` and confirm artifact name is `CallBlocker-store-release`
- [ ] Install and test the signed store APK on at least one physical device
- [ ] Upload the signed APK and listing assets to Cafe Bazaar
- [ ] Use the public privacy-policy URL in the store listing if requested

Never publish a `preview` artifact. Preview signing keys are intentionally temporary.
