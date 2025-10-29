const express = require('express');
const router = express.Router();
const multer = require('multer');
const upload = require('../middleware/upload');
const { cache } = require('../middleware/cache'); // ✅ Add this import

const {
  getUsers,
  getDeletedUsers,
  getDepartments,
  getUserById,
  createUser,
  updateUser,
  deleteUser,
  restoreUser,
  permanentDelete,
  bulkDelete,
  bulkRestore,
  bulkPermanentDelete,
  exportCSV,
  importPreview,
  importConfirm
} = require('../controllers/userController');

const uploadCSV = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 }
});

// ========================================
// ROUTES - ORDER IS CRITICAL!
// ========================================

// 1. Static GET routes (no params) with caching
router.get('/departments', cache(300), getDepartments); // ✅ Cache for 5 minutes
router.get('/deleted', cache(60), getDeletedUsers);     // ✅ Cache for 1 minute
router.get('/export', exportCSV);                       // Don't cache CSV exports
router.get('/', cache(60), getUsers);                   // ✅ Cache for 1 minute

// 2. POST routes (specific paths first)
router.post('/bulk-delete', bulkDelete);
router.post('/bulk-restore', bulkRestore);
router.post('/bulk-permanent-delete', bulkPermanentDelete);
router.post('/import/preview', uploadCSV.single('file'), importPreview);
router.post('/import/confirm', uploadCSV.single('file'), importConfirm);
router.post('/', upload.single('image'), createUser);

// 3. PATCH/POST with :id and specific action
router.patch('/:id/restore', restoreUser);
router.post('/:id/restore', restoreUser);
router.put('/:id/restore', restoreUser);

// 4. PUT with :id
router.put('/:id', upload.single('image'), updateUser);

// 5. DELETE with specific path
router.delete('/:id/permanent', permanentDelete);
router.delete('/:id', deleteUser);

// 6. GET with :id (MUST BE LAST)
router.get('/:id', cache(120), getUserById); // ✅ Cache for 2 minutes

module.exports = router;
