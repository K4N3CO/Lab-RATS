# Stealth and persistence

## Identity layers

The package ID, app name and version are compile-time. Only the *icon and label* swap at runtime, a
few seconds after first launch. The package stays whatever you built.

Decoy screens (Play Protect, Calculator, Weather, System Update) are real activities, not images. The
calculator computes; the weather screen pulls Open-Meteo. Tapping around behaves normally until you
hit the unlock gesture.

## Recovery hooks

| Hook | Action |
| --- | --- |
| Dial `*#1337#` | Unmasks the dashboard |
| 10 rapid taps, screen center | Unmasks the dashboard (re-arms on close) |
| SMS `!RESTART_C2` | Restarts the server even if killed |
| Dashboard `RESTART_SERVER` | Restarts the server |
| Ghost → REPAIR PERMISSIONS | Re-raises revoked permission prompts |

`!RESTART_C2` needs the SMS receiver alive, so it survives a process kill but not a force-stop of
the package. It also matches on substring — any text containing it triggers.

## Persistence layers

1. **Accessibility watchdog** — an enabled Accessibility service is high-survival; it reanimates the
   core when the OS kills it.
2. **WakeLock** — holds the CPU during telemetry so doze doesn't truncate a report.
3. **Static wakers** — boot, power change, user-present broadcasts.
4. **Exponential respawn** — backoff restart to stay under the OS's "frequent restart" threshold.

These compose. Disabling Accessibility breaks the chain at step 1 and the rest degrade to
opportunistic restarts.

## Anti-removal

Blocks Settings → Uninstall and Force Stop by intercepting the uninstall intent and re-launching.
It is not bulletproof: `adb uninstall` and Safe Mode both bypass it, as does a Play Store update.

## Recent-apps ghosting

Hard-coded — the activity never appears in Recents. You can't turn it off, and it's the fastest way
to confirm the app is running: it isn't in the switcher but it is in Settings → Apps.

## Detection surface

Things a determined analyst finds:

- Package name, if you left the default.
- The Accessibility service entry in Settings.
- Persistent notification (can be minimized but not fully removed while the service runs).
- `adb shell dumpsys activity services` showing the bound socket.
- The keystore subject `CN=Lab-RATS Developer, O=Lab-RATS.LABS, C=US` — re-sign with your own cert to
  drop that tell.

Play Protect, Knox and McAfee have all shipped signature updates that catch earlier builds. A fresh
build with a fresh keystore is the only reliable answer; a build that passed last month proves
nothing.