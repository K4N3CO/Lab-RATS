const jwt = require('jsonwebtoken');
const crypto = require('crypto');

// Ensure these are set in your .env file
const ACCESS_SECRET = process.env.ACCESS_SECRET || 'lab_rats_c2_access_secret_key_2026_super_secure_987654321';
const REFRESH_SECRET = process.env.REFRESH_SECRET || 'lab_rats_c2_refresh_secret_key_2026_super_secure_123456789';
const IS_PROD = process.env.NODE_ENV === 'production';

// Helper to extract cookie from raw Cookie header if req.cookies is unparsed
function getCookieFromHeader(cookieHeader, name) {
  if (!cookieHeader) return null;
  const matches = cookieHeader.match(new RegExp('(?:^|; )' + name.replace(/([\.$?*|{}\(\)\[\]\\\/\+^])/g, '\\$1') + '=([^;]*)'));
  return matches ? decodeURIComponent(matches[1]) : null;
}

// Generates tokens and attaches them as strict HttpOnly cookies
function issueRotatedCookies(res, userId, tokenFamilyId) {
  const jti = crypto.randomUUID(); // Unique ID for this specific refresh token

  const accessToken = jwt.sign({ userId }, ACCESS_SECRET, { expiresIn: '5m' });
  const refreshToken = jwt.sign(
    { userId, jti, familyId: tokenFamilyId },
    REFRESH_SECRET,
    { expiresIn: '7d' }
  );

  const baseCookieOptions = {
    httpOnly: true,
    secure: IS_PROD, // Render provides free HTTPS automatically in production
    // Use 'None' if frontend & backend have different .onrender.com URLs.
    // Use 'Lax' or 'Strict' if served from the same domain/custom domain.
    sameSite: IS_PROD ? 'None' : 'Lax'
  };

  res.cookie('access_token', accessToken, {
    ...baseCookieOptions,
    maxAge: 5 * 60 * 1000 // 5 minutes
  });

  res.cookie('refresh_token', refreshToken, {
    ...baseCookieOptions,
    path: '/api/auth/rotate', // Restricts cookie so it's ONLY sent to the rotation endpoint
    maxAge: 7 * 24 * 60 * 60 * 1000 // 7 days
  });

  return { jti, expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) };
}

// Middleware to protect your private API routes
function requireAuth(req, res, next) {
  const token = req.cookies?.access_token || getCookieFromHeader(req.headers?.cookie, 'access_token');
  if (!token) return res.status(401).json({ error: 'Unauthorized: No access token' });

  jwt.verify(token, ACCESS_SECRET, (err, decoded) => {
    if (err) return res.status(401).json({ error: 'Unauthorized: Token expired or invalid' });
    req.user = decoded;
    next();
  });
}

module.exports = { issueRotatedCookies, requireAuth, ACCESS_SECRET, REFRESH_SECRET };
