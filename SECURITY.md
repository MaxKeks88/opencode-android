# Security policy

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Use GitHub's
private vulnerability reporting: **Security → Report a vulnerability** in this
repository, or contact the maintainer listed in the repository profile.

Include the app version (⚙ → About / `versionName`), Android version, and
reproduction steps. We aim to answer within a week.

## Scope

The app is designed as a local, single-user tool:

- `opencode serve` and the local HTTP server bind to `127.0.0.1` only.
- Every sensitive local route requires the per-install `X-Bridge-Token`.
- Cleartext HTTP is restricted to `127.0.0.1`/`localhost`.
- Public builds are not `debuggable` and do not enable WebView debugging.
- Provider credentials are stored by OpenCode on the device, not in the app.

## Out of scope

- Attacks that require an already-rooted device or a malicious ADB host.
- Play Protect / “unknown sources” warnings for sideloaded APKs.
- Issues in third-party models or providers.
- The app intentionally grants shell access (uid 2000) when the user enables
  Shizuku and approves the permission.

## Verifying releases

Every release publishes the APK and its SHA-256. You can verify a download:

```bash
sha256sum OpenCodeAndroid-<version>.apk
```

The APK is signed with the project key. Future updates must use the same
signing certificate; the app checks this before installing an update.
