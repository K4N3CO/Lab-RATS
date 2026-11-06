# Building payloads

Everything runs from `apk-builder/`. Nothing here touches a network except the wizard's upload step.

## Menu reference

| # | Option | Behavior |
| --- | --- | --- |
| 1 | Start Build | Requirement check → keystore → config → `assembleRelease`. |
| 2 | Generate Keystore Only | New `lab-rats-keystore.jks` + `keystore.properties`. |
| 3 | Configure App Settings Only | Rewrites name/ID/version/webhook without building. |
| 4 | Check Requirements | Java version probe, `bc`/`awk` check, `gradlew` presence. |
| 5 | Weaponized Payload Lab | Standalone carriers (13 vectors, incl. priv-app ZIP). |
| 6 | Infection Wizard | Build → host → shorten → weaponize, one pass. |
| 7 | Smali Surgery & APK Binder | Injects the payload into a third-party APK. |
| 8 | Help | Prints the short command reference. |
| 9 | Exit | — |

## Config keys

Option 1 and 3 write these:

**`build_config.txt`** (in `apk-builder/`)