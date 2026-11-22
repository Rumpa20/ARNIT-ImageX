# APK Signing and Update Compatibility

## Rule for distributed APKs

ARNIT ImageX APKs must be signed with one persistent signing key for every version intended to update an existing installation. Do not use a fresh or ephemeral CI key for distributable APK updates.

If Android reports:

> App not installed as package conflicts with an existing package.

the most likely cause is that the APK was signed with a different signing certificate. The package name alone does not make an APK update-compatible.

## Signing key handling

- Create or choose one long-lived Android keystore for ARNIT ImageX.
- Back up the keystore securely in at least two private locations.
- Never commit the keystore, key passwords, or aliases/passwords to Git.
- Store signing materials in GitHub Actions secrets.
- Restrict access to the repository and signing secrets.
- Do not rotate the signing key casually. A different key can prevent ordinary in-place updates.

Suggested GitHub Actions secret names (not yet configured):

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: signing key alias.
- `ANDROID_KEY_PASSWORD`: signing key password.

## Before the stable key is configured

CI debug APKs are test builds only. They may not install as updates over earlier debug APKs if the runner generated a different debug key.

For the current test APK, the user may need to uninstall the previously installed app before installing the new APK. **Uninstalling may erase local app data.** Avoid doing this unless the user is comfortable losing app data.

## Release checklist

1. Confirm the persistent signing key is backed up.
2. Configure the GitHub Actions secrets listed above.
3. Sign all update APKs with that same key.
4. Increment `versionCode` on every release.
5. Build the APK and verify its signature.
6. Compare the signing certificate against the previous release before sharing.
7. Test installing the APK over the previous signed version without uninstalling.

Until steps 1-3 are completed, do not claim update compatibility for APKs.
