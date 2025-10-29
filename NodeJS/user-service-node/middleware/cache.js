const redis = require('redis');

let redisClient = null;
let isRedisConnected = false;

// Try to connect to Redis (gracefully fail if unavailable)
(async () => {
  try {
    const redisUrl = process.env.REDIS_URL || 'redis://localhost:6379';
    console.log('🔌 Attempting to connect to Redis at:', redisUrl);

    redisClient = redis.createClient({
      url: redisUrl,
      socket: {
        connectTimeout: 5000, // ✅ 5 second timeout
        reconnectStrategy: false // ✅ Don't retry on failure
      }
    });

    redisClient.on('error', (err) => {
      console.error('⚠️ Redis Error:', err.message);
      isRedisConnected = false;
    });

    redisClient.on('connect', () => {
      console.log('✅ Redis connected for caching');
      isRedisConnected = true;
    });

    redisClient.on('disconnect', () => {
      console.warn('⚠️ Redis disconnected');
      isRedisConnected = false;
    });

    await redisClient.connect();
  } catch (err) {
    console.warn('⚠️ Redis unavailable. Running without cache.');
    redisClient = null;
    isRedisConnected = false;
  }
})();

const cache = (duration) => async (req, res, next) => {
  // ✅ Skip cache if Redis not available or not a GET request
  if (!isRedisConnected || !redisClient || req.method !== 'GET') {
    return next();
  }

  const key = `cache:${req.originalUrl}`;
  
  try {
    const cached = await redisClient.get(key); // KEY VALUE PAIRS, URL AND DATA.
    if (cached) {
      console.log(`Cache HIT: ${key}`);
      return res.json(JSON.parse(cached));
    }
    
    console.log(`Cache MISS: ${key}`);
    
    const originalJson = res.json.bind(res);
    res.json = (data) => {
      if (isRedisConnected && redisClient) {
        redisClient.setEx(key, duration, JSON.stringify(data)).catch(console.error);
      }
      return originalJson(data);
    };
    next();
  } catch (err) {
    console.error('Cache error:', err.message);
    next(); // ✅ Always call next() even on error
  }
};

module.exports = { cache, redisClient };