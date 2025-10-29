const { PrismaClient } = require('@prisma/client');
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const crypto = require('crypto');

const prisma = new PrismaClient();

const ACCESS_TOKEN_EXPIRE_MINUTES = 60 * 24; // 1 day

const register = async (req, res) => {
  try {
    const email = req.body.email?.trim();
    const password = req.body.password?.trim();
    
    if (!email || !password) {
      return res.status(400).json({ detail: 'Email and password cannot be empty' });
    }
    
    if (password.length < 6) {
      return res.status(400).json({ detail: 'Password must be at least 6 characters' });
    }
    
    const existingAuth = await prisma.auth.findUnique({
      where: { email }
    });
    
    if (existingAuth) {
      return res.status(400).json({ detail: 'Email already registered' });
    }
    
    const hashedPassword = await bcrypt.hash(password, 10);
    const apiKey = crypto.randomBytes(32).toString('hex');
    
    const auth = await prisma.auth.create({
      data: {
        email,
        password: hashedPassword,
        apiKey
      }
    });
    
    console.log(`✅ User registered: '${email}'`);
    
    res.status(201).json({
      id: auth.id,
      email: auth.email,
      message: 'User registered successfully'
    });
  } catch (err) {
    console.error('Register error:', err);
    res.status(500).json({ detail: 'Registration failed' });
  }
};

const login = async (req, res) => {
  try {
    const email = req.body.email?.trim();
    const password = req.body.password?.trim();
    
    console.log(`DEBUG LOGIN: '${email}'`);
    
    if (!email || !password) {
      return res.status(401).json({ detail: 'Invalid credentials' });
    }
    
    const user = await prisma.auth.findUnique({
      where: { email }
    });
    
    if (!user) {
      return res.status(401).json({ detail: 'Invalid credentials' });
    }
    
    const validPassword = await bcrypt.compare(password, user.password);
    
    if (!validPassword) {
      return res.status(401).json({ detail: 'Invalid credentials' });
    }
    
    const access_token = jwt.sign(
      { sub: user.email },
      process.env.JWT_SECRET,
      { expiresIn: `${ACCESS_TOKEN_EXPIRE_MINUTES}m` }
    );
    
    console.log(`✅ Login successful for: ${email}`);
    
    res.json({
      access_token: access_token,
      token_type: 'bearer',
      expires_in: ACCESS_TOKEN_EXPIRE_MINUTES * 60,
      email: user.email
    });
  } catch (err) {
    console.error('❌ Login error:', err);
    res.status(500).json({ detail: 'Login failed' });
  }
};

module.exports = {
  register,
  login
};
