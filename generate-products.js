// Script to generate 50 products for each store using Cloudinary images

const fs = require('fs');

// Nigerian dish names
const dishNames = [
  "Jollof Rice", "Egusi Soup", "Pounded Yam", "Amala", "Efo Riro",
  "Pepper Soup", "Suya", "Moin Moin", "Akara", "Ewa Agoyin",
  "Boli", "Chin Chin", "Abacha", "Okro Soup", "Edikang Ikong",
  "Ofada Rice", "Asun", "Banga Soup", "Ogbono Soup", "Gizdodo",
  "Fufu", "Afang Soup", "Oha Soup", "Nkwobi", "Isi Ewu",
  "Ugba", "Ukodo", "Editan Soup", "White Rice", "Fried Rice"
];

// Descriptions
const descriptions = [
  "Jollof rice is a popular Nigerian dish made with rice",
  "Egusi soup is a traditional Nigerian soup made with melon seeds",
  "and meat. It is usually eaten with pounded yam or fufu.",
  "and spices. It is often served with fried plantains.",
  "vegetables",
  "tomatoes"
];

// Cloudinary images from your data
const cloudinaryImages = [
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/Amala_jlxqmn.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/vk-bro-al9eh9QkdPA-unsplash_hgb5fp.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/victoria-shes-UC0HZdUitWY-unsplash_wa1zr0.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919513/omotayo-tajudeen-ME416b6sp2I-unsplash_jxh2qx.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nathan-dumlao-1lAIRAsv3C4-unsplash_gmm3t6.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919508/nico-smit-9ZJOs9hmuKs-unsplash_n3fwbt.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/ASORTED_Food_tg6kzh.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919512/nAIJA_FOOD_n7daze.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919510/emile-mbunzama-cLpdEA23Z44-unsplash_qzxna1.jpg",
  "https://res.cloudinary.com/dntowouv0/image/upload/v1737919509/etty-fidele-oJpkjWcScyg-unsplash_b5htn1.jpg"
];

function generateProducts(storeId, count = 50) {
  const products = [];
  
  for (let i = 1; i <= count; i++) {
    const product = {
      itemid: i,
      name: dishNames[Math.floor(Math.random() * dishNames.length)],
      description: descriptions[Math.floor(Math.random() * descriptions.length)],
      price: parseFloat((Math.random() * 10).toFixed(2)),
      imageUrl: cloudinaryImages[Math.floor(Math.random() * cloudinaryImages.length)]
    };
    products.push(product);
  }
  
  return products;
}

// Read the input from stdin (piped data)
let inputData = '';
process.stdin.on('data', (chunk) => {
  inputData += chunk;
});

process.stdin.on('end', () => {
  try {
    const stores = JSON.parse(inputData);
    
    // Update each store with 50 products
    const updatedStores = stores.map(store => ({
      ...store,
      popularItems: generateProducts(store.storeId, 50)
    }));
    
    // Output the result
    console.log(JSON.stringify(updatedStores, null, 2));
  } catch (error) {
    console.error('Error:', error.message);
    process.exit(1);
  }
});
