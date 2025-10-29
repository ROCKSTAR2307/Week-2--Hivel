const { PrismaClient } = require('@prisma/client');
const { stringify } = require('csv-stringify/sync');
const { parse } = require('csv-parse/sync');

const prisma = new PrismaClient();
const APP_BASE_URL = process.env.PUBLIC_APP_URL || `http://localhost:${process.env.PORT || 3000}`;

const toInt = (value) => {
  const parsed = Number(value);
  return Number.isNaN(parsed) ? null : parsed;
};

const normalizeIds = (ids = []) => (
  ids
    .map(toInt)
    .filter((id) => Number.isInteger(id))
);

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

const addMongoId = (user) => {
  if (!user) return user;
  let imageUrl = user.image;
  if (imageUrl && !imageUrl.startsWith('http')) {
    imageUrl = `${APP_BASE_URL}${imageUrl}`;
  }
  return {
    ...user,
    _id: String(user.id),
    image: imageUrl || null
  };
};

const normalizeCsvRecord = (record = {}) => {
  const value = (key) => (record[key] ?? record[key?.replace(/[A-Z]/g, (m) => `_${m.toLowerCase()}`)] ?? '').toString().trim();
  const normalizedGender = value('gender').toLowerCase();

  return {
    firstName: value('firstName'),
    lastName: value('lastName') || null,
    email: value('email'),
    phone: value('phone') || null,
    gender: normalizedGender ? normalizedGender : null,
    city: value('city') || null,
    department: value('department') || null,
    image: value('image') || null
  };
};

const validateCsvRecord = (row) => {
  const errors = [];
  if (!row.firstName) {
    errors.push('First name is required');
  }
  if (!row.email) {
    errors.push('Email is required');
  } else if (!EMAIL_REGEX.test(row.email)) {
    errors.push('Email is invalid');
  }
  if (row.gender && !['male', 'female'].includes(row.gender)) {
    errors.push('Gender must be male or female');
  }
  return errors;
};

const getUsers = async (req, res) => {
  try {
    const rawSkip = Number(req.query.skip ?? 0);
    const rawLimit = Number(req.query.limit ?? 30);
    const skip = Number.isNaN(rawSkip) ? 0 : rawSkip;
    const limit = Number.isNaN(rawLimit) ? 30 : rawLimit;
    const search = (req.query.search || '').trim();
    const department = req.query.department && req.query.department !== 'all'
      ? req.query.department
      : undefined;
    const gender = req.query.gender && req.query.gender !== 'all'
      ? req.query.gender
      : undefined;

    const allowedSortFields = ['firstName', 'lastName', 'email', 'city', 'department', 'createdAt'];
    const sortByRaw = req.query.sort_by || req.query.sortBy || 'createdAt';
    const sortBy = allowedSortFields.includes(sortByRaw) ? sortByRaw : 'createdAt';
    const sortOrderRaw = (req.query.sort_order || req.query.sortOrder || 'desc').toLowerCase();
    const sortOrder = sortOrderRaw === 'asc' ? 'asc' : 'desc';

    const where = {
      isDeleted: false,
      ...(search && {
        OR: [
          { firstName: { contains: search } },
          { lastName: { contains: search } },
          { email: { contains: search } }
        ]
      }),
      ...(department && { department }),
      ...(gender && { gender })
    };

    const [users, total] = await Promise.all([
      prisma.user.findMany({
        where,
        skip,
        take: limit,
        orderBy: { [sortBy]: sortOrder }
      }),
      prisma.user.count({ where })
    ]);

    res.json({
      users: users.map(addMongoId),
      total,
      skip,
      limit
    });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const getDeletedUsers = async (req, res) => {
  try {
    const rawLimit = Number(req.query.limit ?? 100);
    const limit = Number.isNaN(rawLimit) ? undefined : rawLimit;

    const users = await prisma.user.findMany({
      where: { isDeleted: true },
      orderBy: { updatedAt: 'desc' },
      ...(limit ? { take: limit } : {})
    });
    
    res.json({ users: users.map(addMongoId) });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const getDepartments = async (req, res) => {
  try {
    const departments = await prisma.user.findMany({
      where: {
        isDeleted: false,
        department: { not: null }
      },
      select: { department: true },
      distinct: ['department']
    });
    
    const list = departments
      .map((d) => d.department)
      .filter(Boolean)
      .sort((a, b) => a.localeCompare(b));

    res.json(list);
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const getUserById = async (req, res) => {
  try {
    const id = toInt(req.params.id);
    if (id === null) {
      return res.status(400).json({ detail: 'Invalid user id' });
    }
    
    const user = await prisma.user.findUnique({
      where: { id }
    });
    
    if (!user || user.isDeleted) {
      return res.status(404).json({ detail: 'User not found' });
    }
    
    res.json(addMongoId(user));
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const createUser = async (req, res) => {
  try {
    const { firstName, lastName, email, phone, gender, city, department } = req.body;
    const image = req.file ? `/uploads/${req.file.filename}` : null;
    const trimmedFirst = firstName?.trim();
    const trimmedEmail = email?.trim();

    if (!trimmedFirst) {
      return res.status(400).json({ detail: 'First name is required' });
    }

    if (!trimmedEmail || !EMAIL_REGEX.test(trimmedEmail)) {
      return res.status(400).json({ detail: 'Valid email is required' });
    }
    
    const newUser = await prisma.user.create({
      data: {
        firstName: trimmedFirst,
        lastName: lastName?.trim() || null,
        email: trimmedEmail,
        phone: phone?.trim() || null,
        gender: gender?.trim() || null,
        city: city?.trim() || null,
        department: department?.trim() || null,
        image,
        createdBy: req.user?.email || 'system'
      }
    });
    
    res.status(201).json(addMongoId(newUser));
  } catch (err) {
    if (err.code === 'P2002') {
      return res.status(400).json({ detail: 'Email already exists' });
    }
    res.status(500).json({ detail: err.message });
  }
};

const updateUser = async (req, res) => {
  try {
    const id = toInt(req.params.id);
    if (id === null) {
      return res.status(400).json({ detail: 'Invalid user id' });
    }
    const { firstName, lastName, phone, gender, city, department } = req.body;
    const image = req.file ? `/uploads/${req.file.filename}` : undefined;
    
    const updatedUser = await prisma.user.update({
      where: { id },
      data: {
        ...(firstName && { firstName: firstName.trim() }),
        ...(lastName && { lastName: lastName.trim() }),
        ...(phone && { phone: phone.trim() }),
        ...(gender && { gender: gender.trim() }),
        ...(city && { city: city.trim() }),
        ...(department && { department: department.trim() }),
        ...(image && { image }),
        updatedBy: req.user?.email || 'system'
      }
    });
    
    res.json(addMongoId(updatedUser));
  } catch (err) {
    if (err.code === 'P2025') {
      return res.status(404).json({ detail: 'User not found' });
    }
    res.status(500).json({ detail: err.message });
  }
};

const deleteUser = async (req, res) => {
  try {
    const id = toInt(req.params.id);
    if (id === null) {
      return res.status(400).json({ detail: 'Invalid user id' });
    }
    
    await prisma.user.update({
      where: { id },
      data: { 
        isDeleted: true,
        updatedBy: req.user?.email || 'system'
      }
    });
    
    res.json({ message: 'User deleted successfully' });
  } catch (err) {
    if (err.code === 'P2025') {
      return res.status(404).json({ detail: 'User not found' });
    }
    res.status(500).json({ detail: err.message });
  }
};

const restoreUser = async (req, res) => {
  try {
    const id = toInt(req.params.id);
    if (id === null) {
      return res.status(400).json({ detail: 'Invalid user id' });
    }
    
    await prisma.user.update({
      where: { id },
      data: { 
        isDeleted: false,
        updatedBy: req.user?.email || 'system'
      }
    });
    
    res.json({ message: 'User restored successfully' });
  } catch (err) {
    if (err.code === 'P2025') {
      return res.status(404).json({ detail: 'User not found' });
    }
    res.status(500).json({ detail: err.message });
  }
};

const permanentDelete = async (req, res) => {
  try {
    const id = toInt(req.params.id);
    if (id === null) {
      return res.status(400).json({ detail: 'Invalid user id' });
    }
    
    await prisma.user.delete({
      where: { id }
    });
    
    res.json({ message: 'User permanently deleted' });
  } catch (err) {
    if (err.code === 'P2025') {
      return res.status(404).json({ detail: 'User not found' });
    }
    res.status(500).json({ detail: err.message });
  }
};

const bulkDelete = async (req, res) => {
  try {
    const { ids } = req.body;
    
    if (!ids || !Array.isArray(ids)) {
      return res.status(400).json({ detail: 'Invalid request' });
    }
    
    const validIds = normalizeIds(ids);
    if (!validIds.length) {
      return res.status(400).json({ detail: 'No valid ids provided' });
    }
    
    await prisma.user.updateMany({
      where: { id: { in: validIds } },
      data: { 
        isDeleted: true,
        updatedBy: req.user?.email || 'system'
      }
    });
    
    res.json({ message: `${validIds.length} users deleted` });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const bulkRestore = async (req, res) => {
  try {
    const { ids } = req.body;
    
    if (!ids || !Array.isArray(ids)) {
      return res.status(400).json({ detail: 'Invalid request' });
    }
    
    const validIds = normalizeIds(ids);
    if (!validIds.length) {
      return res.status(400).json({ detail: 'No valid ids provided' });
    }
    
    await prisma.user.updateMany({
      where: { id: { in: validIds } },
      data: { 
        isDeleted: false,
        updatedBy: req.user?.email || 'system'
      }
    });
    
    res.json({ message: `${validIds.length} users restored` });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const bulkPermanentDelete = async (req, res) => {
  try {
    const { ids } = req.body;
    
    if (!ids || !Array.isArray(ids)) {
      return res.status(400).json({ detail: 'Invalid request' });
    }
    
    const validIds = normalizeIds(ids);
    if (!validIds.length) {
      return res.status(400).json({ detail: 'No valid ids provided' });
    }
    
    await prisma.user.deleteMany({
      where: { id: { in: validIds } }
    });
    
    res.json({ message: `${validIds.length} users permanently deleted` });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const exportCSV = async (req, res) => {
  try {
    const search = (req.query.search || '').trim();
    const department = req.query.department && req.query.department !== 'all'
      ? req.query.department
      : undefined;
    const gender = req.query.gender && req.query.gender !== 'all'
      ? req.query.gender
      : undefined;
    const allowedSortFields = ['firstName', 'lastName', 'email', 'city', 'department', 'createdAt'];
    const sortByRaw = req.query.sort_by || req.query.sortBy || 'createdAt';
    const sortBy = allowedSortFields.includes(sortByRaw) ? sortByRaw : 'createdAt';
    const sortOrderRaw = (req.query.sort_order || req.query.sortOrder || 'desc').toLowerCase();
    const sortOrder = sortOrderRaw === 'asc' ? 'asc' : 'desc';
    
    const where = {
      isDeleted: false,
      ...(search && {
        OR: [
          { firstName: { contains: search } },
          { lastName: { contains: search } },
          { email: { contains: search } }
        ]
      }),
      ...(department && { department }),
      ...(gender && { gender })
    };
    
    const users = await prisma.user.findMany({
      where,
      orderBy: { [sortBy]: sortOrder }
    });
    
    const formatDate = (value) => new Date(value).toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    });
    
    const formattedUsers = users.map(user => {
      const imageUrl = user.image && !user.image.startsWith('http')
        ? `${APP_BASE_URL}${user.image}`
        : user.image;
      
      return {
        id: user.id,
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        phone: user.phone,
        gender: user.gender,
        city: user.city,
        department: user.department,
        image: imageUrl,
        createdAt: formatDate(user.createdAt),
        updatedAt: formatDate(user.updatedAt),
        createdBy: user.createdBy || 'system',
        updatedBy: user.updatedBy || user.createdBy || 'system'
      };
    });
    
    const csvData = stringify(formattedUsers, {
      header: true,
      columns: ['id', 'firstName', 'lastName', 'email', 'phone', 'gender', 'city', 'department', 'image', 'createdAt', 'updatedAt', 'createdBy', 'updatedBy']
    });
    
    res.setHeader('Content-Type', 'text/csv');
    res.setHeader('Content-Disposition', 'attachment; filename=users.csv');
    res.send(csvData);
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};
const importPreview = async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ detail: 'No file uploaded' });
    }

    const csvContent = req.file.buffer.toString('utf-8');
    const records = parse(csvContent, {
      columns: true,
      skip_empty_lines: true,
      trim: true
    });

    const analysis = records.map((record, index) => {
      const normalized = normalizeCsvRecord(record);
      const errors = validateCsvRecord(normalized);
      return {
        rowNumber: index + 2, // account for header row
        normalized,
        errors
      };
    });

    const errors = analysis
      .filter((row) => row.errors.length)
      .map((row) => ({
        row: row.rowNumber,
        email: row.normalized.email || null,
        reason: row.errors.join('; ')
      }));

    res.json({
      total_rows: analysis.length,
      valid_users: analysis.length - errors.length,
      invalid_users: errors.length,
      errors
    });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

const importConfirm = async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ detail: 'No file uploaded' });
    }

    const csvContent = req.file.buffer.toString('utf-8');
    const records = parse(csvContent, {
      columns: true,
      skip_empty_lines: true,
      trim: true
    });

    const normalizedRows = records.map((record, index) => ({
      ...normalizeCsvRecord(record),
      rowNumber: index + 2
    }));

    const validRows = [];
    const errors = [];

    normalizedRows.forEach((row) => {
      const validationErrors = validateCsvRecord(row);
      if (validationErrors.length) {
        errors.push({
          row: row.rowNumber,
          email: row.email || null,
          reason: validationErrors.join('; ')
        });
      } else {
        validRows.push(row);
      }
    });

    if (!validRows.length) {
      return res.status(400).json({
        detail: 'No valid users to import',
        errors
      });
    }

    const insertPayload = validRows.map((row) => ({
      firstName: row.firstName,
      lastName: row.lastName,
      email: row.email,
      phone: row.phone,
      gender: row.gender,
      city: row.city,
      department: row.department,
      image: row.image,
      createdBy: req.user?.email || 'system'
    }));

    const result = await prisma.user.createMany({
      data: insertPayload,
      skipDuplicates: true
    });

    res.json({
      imported: result.count,
      attempted: validRows.length,
      skipped: validRows.length - result.count,
      errors
    });
  } catch (err) {
    res.status(500).json({ detail: err.message });
  }
};

module.exports = {
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
};

