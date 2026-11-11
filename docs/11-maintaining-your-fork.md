# Maintaining your fork

## Rebrand

Touch points for a full identity change:

1. `app/build.gradle` — `applicationId`, `versionName`, `versionCode`, `minSdk`.
2. `app/src/main/res/values/strings.xml` — `app_name`.
3. `apk-builder/build.sh` — the default `APP_NAME` / `PKG_NAME` prompts.
4. Decoy PNGs in `app/src/main/res/mipmap-*` — reskin or leave.
5. Keystore `dname` in `build.sh` — replace `CN=Lab-RATS Developer, O=Lab-RATS.LABS`.
6. `PORT` constant if 9191 is a tell.
7. `router/` auth cookie value if you want a non-portable session.

Do 1–2 and 5 as a set; a partial rebrand with the default package still leaks the origin.

## Upgrading from upstream

```bash
git remote add upstream https://github.com/K4N3CO/Lab-RATS-v1.6.git
git fetch upstream
git diff upstream/main --stat