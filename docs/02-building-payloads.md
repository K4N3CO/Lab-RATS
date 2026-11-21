# 02 — Building payloads

All commands run from `apk-builder/`. Nothing touches the network except the wizard's
upload step.

## Menu

| # | Option | Behavior |
|---|---|---|
| 1 | Start Build | Requirement check → keystore → config → `assembleRelease` |
| 2 | Generate Keystore Only | New `lab-rats-keystore.jks` + `keystore.properties` |
| 3 | Configure App Settings Only | Rewrites name/ID/version/webhook without building |
| 4 | Check Requirements | Java probe, `bc`/`awk` check, `gradlew` presence |
| 5 | Weaponized Payload Lab | Standalone carriers, no APK build |
| 6 | Infection Wizard | Build → host → shorten → weaponize, one pass |
| 7 | Smali Surgery & APK Binder | Injects the payload into a third-party APK |
| 8 | Help | Short command reference |
| 9 | Exit | — |

## Config keys

Options 1 and 3 write `build_config.txt`:

| Key | Meaning |
|---|---|
| `APP_NAME` | Visible label at install |
| `PACKAGE_ID` | Application ID — must be unique per campaign |
| `MIN_SDK` | 21–34 |
| `C2_WEBHOOK` | Google Apps Script or relay URL |
| `DECOY` | 1 Play Protect · 2 Calculator · 3 Weather · 4 System Update · 5 Lab-RATS |

## Output

    output/{AppName}-v{Version}-signed.apk
    output/exploit.*        (wizard only)

## Carriers (12 named + priv-app ZIP = 13)

Zero-Click MP4, Stealth PDF, Meeting Invite (ICS), Dolby Audio, ADB Script,
Bluetooth Push (VCF), NFC NDEF, Stego Image, PWA Bundle, Office Word, Office Excel,
Ghost GIF, priv-app ZIP.

## Signing

Every build generates a fresh keystore. Two APKs with different keystores cannot
upgrade each other — uninstall first, or reuse the keystore from option 2 for a
stable campaign.
