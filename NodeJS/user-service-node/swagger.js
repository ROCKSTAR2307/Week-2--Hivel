const swaggerAutogen = require('swagger-autogen')();

const doc = {
  info: {
    title: 'User Management API',
    description: 'Complete REST API for user management with authentication, CRUD operations, bulk actions, and CSV import/export',
    version: '1.0.0',
  },
  host: 'localhost:8000',
  schemes: ['http'],
  securityDefinitions: {
    bearerAuth: {
      type: 'apiKey',
      name: 'Authorization',
      in: 'header',
      description: 'Enter JWT token as: Bearer <token>'
    }
  }
};

const outputFile = './swagger-output.json';
const routes = ['./index.js']; // Your main file where routes are mounted


swaggerAutogen(outputFile, routes, doc);