// Copy this script and run it to generate 50 products for all stores
// Usage: node generate-50-products.js > updated-stores.json

const dishNames = [
  "Jollof Rice", "Egusi Soup", "Pounded Yam", "Amala", "Efo Riro",
  "Pepper Soup", "Suya", "Moin Moin", "Akara", "Ewa Agoyin",
  "Boli", "Chin Chin", "Abacha", "Okro Soup", "Edikang Ikong",
  "Ofada Rice", "Asun", "Banga Soup", "Ogbono Soup", "Gizdodo"
];

const descriptions = [
  "Jollof rice is a popular Nigerian dish made with rice",
  "Egusi soup is a traditional Nigerian soup made with melon seeds",
  "and meat. It is usually eaten with pounded yam or fufu.",
  "and spices. It is often served with fried plantains.",
  "vegetables",
  "tomatoes"
];

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

function generateProducts(count = 50) {
  const products = [];
  for (let i = 1; i <= count; i++) {
    products.push({
      itemid: i,
      name: dishNames[Math.floor(Math.random() * dishNames.length)],
      description: descriptions[Math.floor(Math.random() * descriptions.length)],
      price: parseFloat((Math.random() * 10).toFixed(2)),
      imageUrl: cloudinaryImages[Math.floor(Math.random() * cloudinaryImages.length)]
    });
  }
  return products;
}

// Example usage - you can call this function for each store
// For demonstration, generating for one store:
const exampleStore = {
  storeId: 1,
  name: "Example Store",
  popularItems: generateProducts(50)
};

console.log(JSON.stringify(exampleStore.popularItems, null, 2));
