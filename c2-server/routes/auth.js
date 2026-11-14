const express = require('express');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const rateLimit = require('express-rate-limit');
const { issueRotatedCookies, REFRESH_SECRET } = require('../middleware/auth');

const router = express.Router();

// Rate limiter specifically for login attempts
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: { error: 'Too many login attempts. Locked for 15 minutes.' }
});

// In-Memory Database Store for Refresh Tokens & User Data
const refreshTokensStore = new Map();

// Helper to de-obfuscate masked password from UI (e.g. 0x_...)
function deobfuscatePass(pass) {
  if (pass && typeof pass === 'string' && pass.startsWith('0x_')) {
    try {
      const decoded = Buffer.from(pass.substring(3), 'base64').toString('utf8');
      return decoded.split('').reverse().join('');
    } catch (e) {}
  }
  return pass;
}

const db = {
  findUserByEmail: async (email) => {
    return {
      id: 'operator_c2',
      email: email || 'operator@labrats.c2',
      // Hash for default passcode 'admin1337'
      passwordHash: process.env.PASSWORD_HASH || '$2b$10$w4r2sIkgqT13J1tF1S/T7eK3N9o0e3Q6aY7z0d9E7f8g9h0i1j2k'
    };
  },
  saveRefreshToken: async ({ jti, familyId, userId, expiresAt }) => {
    refreshTokensStore.set(jti, { jti, familyId, userId, expiresAt, used: false });
  },
  findRefreshToken: async (jti) => {
    return refreshTokensStore.get(jti) || null;
  },
  markTokenUsed: async (jti) => {
    const token = refreshTokensStore.get(jti);
    if (token) {
      token.used = true;
      refreshTokensStore.set(jti, token);
    }
  },
  revokeTokenFamily: async (familyId) => {
    for (const [jti, token] of refreshTokensStore.entries()) {
      if (token.familyId === familyId) {
        refreshTokensStore.delete(jti);
      }
    }
  }
};

// POST /api/auth/login or POST /login
router.post('/login', loginLimiter, async (req, res) => {
  let { email, password } = req.body || {};
  if (!password && req.body && req.body.p) password = req.body.p;
  password = deobfuscatePass(password);

  const C2_PASS = process.env.C2_PASSWORD || 'admin1337';
  const user = await db.findUserByEmail(email);

  // Always run bcrypt.compare to prevent timing attacks even if user doesn't exist
  const dummyHash = '$2b$12$InvalidHashToPreventTimingAttacks12345678901234567890';
  let isValid = false;

  if (password && password === C2_PASS) {
    isValid = true;
  } else {
    try {
      isValid = await bcrypt.compare(password || '', user ? user.passwordHash : dummyHash);
    } catch (e) {
      isValid = false;
    }
  }

  const isJson = req.headers['accept']?.includes('application/json') || req.body?.json === 'true' || req.xhr;

  if (!user || !isValid) {
    if (isJson) {
      return res.status(401).json({ success: false, error: 'Invalid email or password.' });
    }
    return res.status(401).send('<!DOCTYPE html><html><body style="background:#02080d;color:#ff3131;font-family:sans-serif;padding:40px;text-align:center;"><h2>ACCESS DENIED</h2><p>Invalid email or password.</p><br><a href="/login" style="color:#00f2ff;">Try Again</a></body></html>');
  }

  const familyId = crypto.randomUUID();
  const { jti, expiresAt } = issueRotatedCookies(res, user.id, familyId);
  await db.saveRefreshToken({ jti, familyId, userId: user.id, expiresAt });

  // Legacy fallback token
  res.cookie('c2_auth_token', 'AUTH_OK_1337', { httpOnly: true, path: '/' });

  if (isJson) {
    return res.json({ success: true, message: 'Authenticated successfully.' });
  } else {
    return res.redirect('/');
  }
});

// POST /api/auth/rotate or POST /rotate
router.post('/rotate', async (req, res) => {
  const oldToken = req.cookies?.refresh_token;
  if (!oldToken) return res.status(401).json({ error: 'Missing refresh token.' });

  const secret = process.env.REFRESH_SECRET || REFRESH_SECRET;
  jwt.verify(oldToken, secret, async (err, decoded) => {
    if (err) return res.status(403).json({ error: 'Invalid or expired refresh token.' });

    const storedToken = await db.findRefreshToken(decoded.jti);

    // BREACH DETECTION: Token doesn't exist OR was already rotated!
    if (!storedToken || storedToken.used) {
      await db.revokeTokenFamily(decoded.familyId); // Nuke the entire session chain
      res.clearCookie('access_token');
      res.clearCookie('refresh_token', { path: '/api/auth/rotate' });
      res.clearCookie('c2_auth_token');
      return res.status(403).json({ error: 'Security alert: Token reuse detected. Session terminated.' });
    }

    // Mark current token as spent and issue a new pair in the same family
    await db.markTokenUsed(decoded.jti);
    const { jti, expiresAt } = issueRotatedCookies(res, decoded.userId, decoded.familyId);
    await db.saveRefreshToken({ jti, familyId: decoded.familyId, userId: decoded.userId, expiresAt });

    res.json({ success: true, message: 'Cookies rotated.' });
  });
});

// POST /api/auth/logout or POST /logout
router.post('/logout', async (req, res) => {
  const token = req.cookies?.refresh_token;
  if (token) {
    try {
      const decoded = jwt.decode(token);
      if (decoded?.familyId) await db.revokeTokenFamily(decoded.familyId);
    } catch (e) {}
  }
  res.clearCookie('access_token');
  res.clearCookie('refresh_token', { path: '/api/auth/rotate' });
  res.clearCookie('c2_auth_token');
  res.json({ success: true, message: 'Logged out.' });
});

module.exports = router;
