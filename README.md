# 🍔 Food Delivery Application - Backend API


## 📝 Description

This is a robust backend API for a food delivery platform, developed using Spring Boot. It offers comprehensive endpoints for managing users, vendors (business owners), stores, and products. The API supports full CRUD operations, secure authentication, media uploads, and advanced filtering options to facilitate efficient data retrieval and management.


---

## 🚀 Features

<pre>

👤 User Management
- User registration, update, and deletion
- Profile and address information handling
- Password encryption

🧑‍💼 Vendor Management
- Vendor registration and profile management
- Vendor store linking and management
- Upload and store:
    • ID Card (Front & Back)
    • Business Logo

🏪 Store Management
- Register stores with name, address, category, and operating hours
- Enable/disable delivery and pickup options
- Search and filter stores by:
    • Name
    • Category
    • Keyword
    • Location

📦 Product Management
- Add, update, and delete products
- Retrieve products by:
    • ID
    • Name
    • Store
- Upload and associate product images

🖼️ File Upload
- Vendor media file upload (ID, logo)
- Product image upload
- Secure and type-checked file handling via FileStorageService

🔒 Security
- Password hashing
- Input validation for all endpoints
- HTTP status-based error handling

</pre>

## 🔧 Technologies Used

- Java 17  
- Spring Boot 3.x  
- Spring Data JPA  
- Hibernate  
- MySQL Database  
- Lombok for boilerplate code reduction  
- Maven for dependency management

---

## 📂 Project Structure


<pre>

src/
├── main/
│   ├── java/
│   │   └── com/Afrochow/food_app/
│   │       ├── config/          # Configuration classes and constants
│   │       ├── controller/      # REST controllers
│   │       ├── DTO/             # Data Transfer Objects
│   │       ├── model/           # Entity classes
│   │       ├── pojo/            # Plain Java Objects for requests/responses
│   │       ├── repository/      # JPA repositories
│   │       └── services/        # Business logic
│   └── resources/               # Configuration files

</pre>


## 🛠️ Installation & Setup

### Prerequisites

- Java 17 JDK  
- Maven 3.8+  
- MySQL 8.0+

### Clone the repository

```bash

git clone https://github.com/yourusername/food-delivery-api.git
cd food-delivery-api

Configure the database

	1.	Create a MySQL database
	2.	Update application.properties with your database credentials


Build and run
mvn clean install
mvn spring-boot:run
```

## 🌐 API Endpoints

<pre>

👤 User Endpoints

POST    /food-api/customer/register         - Register a new user
PUT     /food-api/customer/update           - Update user profile
GET     /food-api/customer                  - Get all users
GET     /food-api/customer/{id}             - Get user by ID
DELETE  /food-api/customer/delete-user      - Delete a user


🧑‍💼 Vendor Endpoints

POST    /food-api/vendor/register           - Register a new vendor
PUT     /food-api/vendor/update             - Update vendor profile
GET     /food-api/vendor                    - Get all vendors
GET     /food-api/vendor/{id}               - Get vendor by ID
DELETE  /food-api/vendor/delete             - Delete a vendor
POST    /food-api/vendor/vendor-media       - Upload vendor ID card front, back, and logo


🏪 Store Endpoints

POST    /food-api/store/register            - Create a new store
PUT     /food-api/store/update              - Update store details
GET     /food-api/store                     - Get all stores (with optional filters)
GET     /food-api/stores/{id}               - Get store by ID
GET     /food-api/stores/search             - Search stores by keyword
GET     /food-api/stores/location           - Search stores by location
DELETE  /food-api/store/delete-store        - Delete a store


📦 Product Endpoints

POST    /food-api/product/create            - Create a new product
PUT     /food-api/product/update            - Update product details
GET     /food-api/product                   - Get all products
GET     /food-api/product/{id}              - Get product by ID
GET     /food-api/product/search?name=...   - Search products by name
GET     /food-api/product/store/{storeId}   - Get all products by store
DELETE  /food-api/product/delete/{id}       - Delete a product


🖼️ File Upload Endpoints

POST    /food-api/vendor/vendor-media       - Upload vendor media (ID front, back, logo)
POST    /food-api/product/upload-image      - Upload a product image

</pre>

⸻
<pre>

🔒 Security
  
	•	Password encryption
	•	Input validation for all endpoints
	•	Proper error handling and status codes

</pre>
⸻

🤝 Contributing

Contributions are welcome!
Please fork the repository and create a pull request with your changes.

⸻

📧 Contact

For questions or support, reach out via email: ibikunleogunbanwo@gmail.com
