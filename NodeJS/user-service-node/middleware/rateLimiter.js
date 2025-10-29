const rateLimit = require('express-rate-limit');

// General API rate limiter
const limiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    const retryAfter = Math.max(
      1,
      Math.ceil((req.rateLimit.resetTime - Date.now()) / 1000)
    );
    res.status(429).json({
      success: false,
      message: 'Too many requests, please try again later',
      detail: { retry_after: retryAfter },
      retry_after: retryAfter
    });
  }
});

// Auth-specific rate limiter (stricter)
const authLimiter = rateLimit({
  windowMs: 1 * 60 * 1000, // 15 minutes
  max: 50,
  standardHeaders: true,
  legacyHeaders: false,
  handler: (req, res) => {
    res.status(429).json({
      success: false,
      message: 'Too many authentication attempts. Please try again after 15 minutes.',
      detail: 'Rate limit exceeded'
    });
  }
});

console.log('✅ Using in-memory rate limiting');

module.exports = { limiter, authLimiter };
