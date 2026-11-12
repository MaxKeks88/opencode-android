# Contributing

Thanks for your interest! This project is a community build of OpenCode for
Android. Small, focused changes are easiest to review.

## Before you start

- Read [`AGENTS.md`](AGENTS.md) (architecture, build, debugging) and
  [`README.md`](README.md).
- Open an issue for anything larger than a bug fix so we can agree on the
  approach.
- Do not commit `runtime/`, `OpenCodeAndroid*.apk`, `keystore.jks`,
  `keystore.properties` or build artifacts.

## Development

```bash
python tools/fetch_runtime.py     # first time only (~300 MB)
python build_apk.py               # dev build
python tools/smoke_test.py        # phone connected via ADB
```

Java sources live in `app/src/ru/opencode/android`. The chat UI is
`assets/app/page.html` + `assets/app/bridge.js`; it is copied to the app's
private storage on every start, so UI changes only need a rebuild.

## Pull requests

- Keep the existing style (Russian comments are fine).
- Describe what you tested: build, device, Android version, steps.
- Update `AGENTS.md` and the relevant `docs/` page when behaviour changes.
- Development journals are kept locally and are not part of the repository.
- Never include tokens, device serials or personal paths in screenshots/logs.

By contributing you agree that your changes are licensed under the MIT
license of this repository.
