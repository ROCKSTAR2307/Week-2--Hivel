const jwt = require('jsonwebtoken');
const { PrismaClient } = require('@prisma/client');

const prisma = new PrismaClient();

const authMiddleware = async (req, res, next) => {
  try {
    const authHeader = req.headers['authorization'] || '';  
    const secret = process.env.JWT_SECRET;

    if (authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      try {
        const payload = jwt.verify(token, secret);
        const email = payload.sub || payload.email;

        if (!email) {
          throw new Error('Invalid token payload');
        }

        // Ensure the auth user still exists
        const authUser = await prisma.auth.findUnique({ where: { email } });
        if (!authUser) {
          return res.status(401).json({ detail: 'User no longer exists' });
        }

        req.user = { email };
        return next();
      } catch (err) {
        return res.status(401).json({ detail: 'Invalid or expired token' });
      }
    }

    if (apiKey) {
      const authUser = await prisma.auth.findUnique({ where: { apiKey } });
      if (!authUser) {
        return res.status(401).json({ detail: 'Invalid API key' });
      }
      req.user = { email: authUser.email };
      return next();
    }

    return res.status(401).json({ detail: 'Authentication required' });
  } catch (err) {
    return res.status(500).json({ detail: 'Authentication failed' });
  }
};

module.exports = authMiddleware;
