const { PrismaClient } = require('@prisma/client');
const axios = require('axios');

const prisma = new PrismaClient();

async function seed() {
  try {
    console.log('🗑️  Flushing database...');
    
    // Delete all users
    await prisma.user.deleteMany({});
    console.log('✅ All users deleted');
    
    console.log('📥 Fetching 200 users from DummyJSON...');
    
    // DummyJSON has only 100 users, so we'll fetch them twice with different IDs
    const response = await axios.get('https://dummyjson.com/users?limit=200');
    const dummyUsers = response.data.users;
    
    console.log(`✅ Fetched ${dummyUsers.length} users from API`);
    
    // Map DummyJSON users to our schema
    const usersToInsert = [];
    
    // Insert first batch (original 100)
    for (const user of dummyUsers) {
      usersToInsert.push({
        firstName: user.firstName,
        lastName: user.lastName,
        email: user.email,
        phone: user.phone,
        gender: user.gender.toLowerCase(),
        city: user.address?.city || 'Unknown',
        department: user.company?.department || null,
        image: user.image,
        createdBy: 'seed_script',
        isDeleted: false
      });
    }
    
   
    
    console.log('💾 Inserting users into database...');
    
    // Insert all users
    const result = await prisma.user.createMany({
      data: usersToInsert,
      skipDuplicates: true
    });
    
    console.log(`✅ Inserted ${result.count} users`);
    
    // Get final count
    const totalUsers = await prisma.user.count();
    console.log(`📊 Total users in database: ${totalUsers}`);
    
  } catch (err) {
    console.error('❌ Error:', err.message);
  } finally {
    await prisma.$disconnect();
  }
}

seed();
