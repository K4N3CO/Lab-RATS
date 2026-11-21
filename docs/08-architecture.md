# 08 — Architecture

## Shape

The payload is the server. There is no operator-side controller beyond a browser tab.
Everything else — builder, carriers, relay — exists to get the payload onto a device and
keep a path back to it.

Three trust domains, in order of how much of the system they can break:

| Domain | Compromise means |
|---|---|
| Build host | Malicious APK, poisoned keystore, exfiltrated webhook URL |
| Target device | Full dashboard control, all collected data |
| Relay / sheet | Fleet list, exfiltrated media, session proxying |

## Component map

```mermaid
flowchart LR
    subgraph BAND1["WORKSTATION / BUILD HOST"]
        direction TB
        B1["build.sh / build.ps1 / build.bat<br/>menu options 1-9"]
        B2["build_config.txt<br/>app name · package ID · minSdk · webhook · decoy"]
        B3["binder.py<br/>smali surgery (option 7)"]
        B4["Gradle assembleRelease<br/>throwaway keystore"]
        B5["output APK artifact"]
        B6["carrier packer<br/>PDF / MP4 / ICS / NFC / QR / VCF / GIF"]
        B1 --> B2 --> B3 --> B4 --> B5 --> B6
    end

    subgraph BAND2["DELIVERY"]
        direction TB
        D1["anonymous cloud hosts<br/>Catbox / Litterbox / Tmpfiles fallback"]
        D2["URL shortener<br/>is.gd"]
        D3["manual channels<br/>Email / Chat / Physical tag"]
        D1 --> D2 --> D3
    end

    subgraph BAND3["TARGET DEVICE (payload is the server)"]
        direction TB
        T1["installer"]
        T2["PermissionActivity<br/>fake update screen"]
        T3["DecoyActivity<br/>decoy UI"]
        T4["ClonedIconManager<br/>icon swap"]
        T5["LabRatsApp"]
        T6["MediaFrameworkService<br/>NanoHTTPD 0.0.0.0:9191"]
        T7["Router<br/>AuthController / RouteHandler"]
        T8["modules / exploits"]

        subgraph PERSIST["PERSISTENCE LAYER"]
            direction TB
            P1["SystemBoot"]
            P2["StabilityWorker"]
            P3["StabilityPolicy"]
            P4["OemStabilityHelper"]
            P5["Accessibility watchdog"]
            P6["StatusNotification"]
            P7["WorkManager_Sync"]
            P8["IO_Persistence_Manager"]
            P1 --> P2 --> P3
            P2 --> P4
            P2 --> P5
            P5 --> P6
            P6 --> P7 --> P8
        end
        T1 --> T2 --> T3 --> T4 --> T5 --> T6 --> T7 --> T8
    end

    subgraph BAND4["TRANSPORT + OPERATOR"]
        direction TB
        X1["direct IPv6 GUA<br/>plain HTTP :9191"]
        X2["C2_Tunnel"]
        X3["C2_Uploader"]
        X4["c2-server (Express)<br/>routes / middleware / c2"]
        X5["Google Apps Script webhook"]
        X2 --> X3 --> X4 --> X5
        O1["OPERATOR BROWSER<br/>dashboard, 13 tabs"]
        X1 --> O1
        X4 --> O1
        L1["dashed = direct IPv6, plain HTTP, no TLS<br/>solid = relayed over HTTPS"]
    end

    B6 -->|"upload"| D1
    D3 -->|"download + install"| T1
    T5 -->|"open once"| T2
    T6 -.->|"check-in"| X1
    T6 -->|"check-in"| X2
    T7 --> P1
    X1 -.->|"plain HTTP no TLS"| O1

    classDef accent fill:#1f6feb,stroke:#58a6ff,color:#fff
    class T1,T2,T3,T4,T5,T6,T7,T8 accent
    style O1 fill:#238636,stroke:#3fb950,color:#fff
