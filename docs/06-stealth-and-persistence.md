# 06 — Stealth and persistence

Two different problems: looking like something else (stealth) and coming back after
being killed (persistence). They share a dependency — the Accessibility service — and
that dependency is the weakest point in both.

## Identity layers

Split by when they are decided:

| Layer | When | Changeable after build |
|---|---|---|
| Package ID | Compile | No |
| App label | Compile (pre-swap) | No |
| Version string | Compile | No |
| Icon + label | Runtime, ~5s after first launch | Yes, any decoy |
| Launcher entry | Runtime | Yes |
| Version *display* in decoy UI | Runtime, randomized | Yes |

Only icon and label swap at runtime. The package stays whatever you built — that is
what Settings → Apps shows, and it is the first thing an analyst reads.

## Decoy activities

Real activities, not screenshots. They respond to input:

| Decoy | Behavior | Weak tell |
|---|---|---|
| Play Protect | Fake scan progress, auto-grants permissions | Never finds a real threat |
| Calculator | Working expression parser | Nothing meaningful |
| Weather | Live Open-Meteo for a city | Network calls from a weather app |
| System Update | Multi-step update wizard | Never finishes |
| Lab-RATS | No disguise, opens dashboard directly | Obvious |

The Play Protect decoy is the install-friction reducer: the fake scan screen makes the
permission avalanche read as legitimate system behavior. It is also the most scrutinized
decoy, so it ages fastest.

**Unmasking:** dial `*#1337#`, or tap the screen centre 10× rapidly. Both replace the
decoy with the dashboard. Closing the app returns to the decoy.

## Persistence chain

Four layers, ordered by survival value. They compose — each depends on the one above.

| # | Layer | Component | Survives process kill | Survives force-stop | Survives reboot |
|---|---|---|---|---|---|
| 1 | Accessibility watchdog | enabled service | yes | yes | yes |
| 2 | WakeLock hold | `StatusNotification`, FGS | partial | no | no |
| 3 | Static wakers | `SystemBoot`, power/interaction broadcasts | n/a | no | yes |
| 4 | Exponential respawn | `StabilityWorker`, `StabilityPolicy` | yes | no | yes |

`OemStabilityHelper` adds OEM-specific keep-alive paths for Samsung, Xiaomi, Huawei and
OnePlus background managers — this is what stops the "app keeps stopping" notification
from a battery optimizer.

`WorkManager_Sync` and `IO_Persistence_Manager` re-arm state after a restart so a
respawned process picks up its config rather than starting cold.

**Kill the chain at layer 1 and the rest degrade** to opportunistic restarts triggered by
boot, power events and user presence. Nothing below layer 1 can revive a disabled
Accessibility service — only the user can, or `REPAIR PERMISSIONS` can re-raise the
prompt.

## Anti-removal

Intercepts the uninstall intent and force-stop path, then relaunches. Enabled by default
in the Ghost tab.

Bypasses, in the order an analyst tries them:

1. `adb uninstall` — no intent interception path
2. Safe Mode — Accessibility is off, chain is broken
3. Play Store update — installer path bypasses the hook
4. Settings → Clear data — resets config; the app survives but loses state
5. Revoke Accessibility, then uninstall

There is no root requirement and no system partition write, so nothing here is durable
across a factory reset.

## Recent-apps ghosting

Hard-coded via `excludeFromRecents` and detached FGS notifications. Not configurable.
Side effect: the app is invisible in the switcher but present in Settings → Apps — the
fastest single check for "is it running".

Foreground service notifications are detached rather than removed. Android still needs
the FGS to exist; it just doesn't show a task entry for it.

## Recovery hooks

| Hook | Trigger | Precondition | Side effect |
|---|---|---|---|
| Dial `*#1337#` | Dialer | Dialer reachable | Unmasks dashboard |
| 10 taps, centre | Decoy activity | Decoy in foreground | Unmasks, re-arms on close |
| SMS `!RESTART_C2` | Inbound SMS | SMS receiver alive | Restarts service; substring match, any text containing it works |
| `RESTART_SERVER` | Dashboard | Session valid | Restarts service |
| `REPAIR PERMISSIONS` | Ghost tab | Session valid | Re-raises revoked prompts |

`!RESTART_C2` survives a process kill but not a package force-stop — force-stop
unregisters receivers until the next manual launch.

## Stealth controls that mask the device, not the app

- **Blackout mode** — physically blanks the target display while the remote feed stays
  live. Pair with Ghost Control; the user sees a dead screen, you see everything.
- **Ghost toast overlays** — persistent pop-ups with controlled text, colour, size,
  position and animation. `Burnt_Toast` floods the screen to overwhelm input.
- **Remote system denial lock** — full-screen overlay that blocks physical interaction
  until unlocked from the dashboard or hard-restarted.

None of these are stealth *of* the app; they're stealth *of* the operator's activity.

## Debugging

```bash
adb logcat | grep -Ei 'DecoyActivity|ClonedIcon|Stability|watchdog|9191|Accessibility'
