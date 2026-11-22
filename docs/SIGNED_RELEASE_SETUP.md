# Signed APK release setup

This workflow builds and signs a release APK only when the repository Actions secrets are configured.

## Required GitHub Actions secrets

Settings → Secrets and variables → Actions → New repository secret:

- `IMAGEX_SIGNING_KEY_BASE64`: base64-encoded contents of the **existing app signing keystore**.
- `IMAGEX_SIGNING_KEY_ALIAS`: key alias in that keystore.
- `IMAGEX_SIGNING_KEY_PASSWORD`: key password.
- `IMAGEX_SIGNING_STORE_PASSWORD`: keystore password.

To base64-encode the keystore in Termux:
```sh
base64 -w 0 existing-signing-key.jks
```
Copy the output into the first secret. Do not commit or paste the keystore or passwords into the repository/chat.

**Important:** To update an already installed ARNIT ImageX app, the keystore must be the exact original key used to sign that installed app. A different key will prevent updates. This workflow does not generate a new signing key.

After secrets are added, open Actions → Signed Release APK → Run workflow. The signed APK is uploaded as an artifact. It can also run automatically when a version tag beginning with `v` is pushed.
