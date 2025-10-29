const express = require('express');
const dotenv = require('dotenv');
const cors = require('cors');
const path = require('path');
const { PrismaClient } = require('@prisma/client');
const { logger, correlationMiddleware } = require('./utils/logger');
const { limiter, authLimiter } = require('./middleware/rateLimiter'); // ✅ Updated import
const authRoutes = require('./routes/auth'); // GETTING AUTH
const authMiddleware = require('./middleware/auth'); // GETTING MIDDLEWARE
const userRoutes = require('./routes/users'); //GETTING ROUTES
const swaggerUi = require('swagger-ui-express');
const swaggerDocument = require('./swagger-output.json');

dotenv.config();
const app = express();
const prisma = new PrismaClient();
const PORT = process.env.PORT||3000;

// CORS
app.use(cors({
  origin: ['http://localhost:5174','http://localhost:5173'],
  credentials: true,
  methods: ['*'],
  allowedHeaders: ['*'],
  exposedHeaders: ['*']
}));

app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerDocument));

// Body parsers
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));

// Logging
app.use(correlationMiddleware);

// ✅ Apply auth-specific rate limiter BEFORE general rate limiter


// General rate limiting (skip health and metrics)
// General rate limiting (skip health and metrics)
app.use((req, res, next) => {
  if (req.path === '/health' || req.path === '/metrics') {
    return next();
  } 
  return limiter(req, res, next);
});


// Health & Metrics
app.get('/health', async (req, res) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.json({ status: 'UP', database: 'connected' });
  } catch (err) {
    res.status(503).json({ status: 'DOWN', database: 'disconnected' });
  }
});

app.get('/metrics', async (req, res) => {
  try {
    const totalUsers = await prisma.user.count();
    const activeUsers = await prisma.user.count({ where: { isDeleted: false } });
    const deletedUsers = await prisma.user.count({ where: { isDeleted: true } });
    res.json({ totalUsers, activeUsers, deletedUsers });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
});

// Routes
app.use('/auth', authRoutes);
app.use('/api/users', authMiddleware, userRoutes);

app.get('/', (req, res) => {
  res.json({ message: '🚀 Node.js backend running' });
});

app.listen(PORT, () => {
  console.log(`✅ Server: http://localhost:${PORT}`);
});
