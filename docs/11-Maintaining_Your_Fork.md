# 11 ⏤ Maintaining & Rebranding Your Fork

> Guidelines for customizing, rebranding, and keeping your Lab-RATS fork updated with upstream changes.

---

## 🎨 Touch Points for a Full Identity Rebrand

To completely rebrand and change the identity of your Lab-RATS fork:

| Component / File | Purpose & Action |
| :--- | :--- |
| **`app/build.gradle`** | Change `applicationId` (package ID), `versionName`, `versionCode`, and `minSdk`. |
| **`app/src/main/res/values/strings.xml`** | Change `app_name` string resource. |
| **`apk-builder/build.sh` & `build.bat`** | Update default `APP_NAME` and `PKG_NAME` prompt values. |
| **Decoy Icons (`app/src/main/res/mipmap-*`)** | Replace or reskin decoy launcher icons with custom artwork. |
| **Signing Keystore Subject (`build.sh`)** | Update default keystore distinguished name (`CN=Lab-RATS Developer, O=Lab-RATS.LABS`). |
| **C2 Port Constant (`FirebaseConfig.java`)** | Change default port `9191` to a custom non-standard port. |
| **Session Cookie Token (`server.js` & `FirebaseConfig.java`)** | Customize `c2_auth_token` value for non-portable session tokens. |

---

## 🔄 Syncing Upstream Changes

To pull the latest updates from the main Lab-RATS repository into your fork:

```bash
git remote add upstream https://github.com/K4N3CO/Lab-RATS.git
git fetch upstream
git merge upstream/main
```
