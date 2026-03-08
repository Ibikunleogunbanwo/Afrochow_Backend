package com.afrochow.review.repository;
import com.afrochow.review.model.Review;
import com.afrochow.user.model.User;
import com.afrochow.vendor.model.VendorProfile;
import com.afrochow.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {


    List<Review> findByUser(User user);

    List<Review> findByUserOrderByCreatedAtDesc(User user);


    List<Review> findByVendor(VendorProfile vendor);

    List<Review> findByVendorAndIsVisible(VendorProfile vendor, Boolean isVisible);

    List<Review> findByVendorOrderByCreatedAtDesc(VendorProfile vendor);


    List<Review> findByProduct(Product product);

    List<Review> findByProductAndIsVisible(Product product, Boolean isVisible);

    List<Review> findByProductOrderByCreatedAtDesc(Product product);


    List<Review> findByRating(Integer rating);

    List<Review> findByRatingGreaterThanEqual(Integer rating);

    List<Review> findByVendorAndRatingGreaterThanEqual(VendorProfile vendor, Integer rating);


    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.vendor = :vendor AND r.isVisible = true")
    Double calculateVendorAverageRating(@Param("vendor") VendorProfile vendor);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.product = :product AND r.isVisible = true")
    Double calculateProductAverageRating(@Param("product") Product product);


    Long countByVendor(VendorProfile vendor);

    Long countByVendorAndIsVisible(VendorProfile vendor, Boolean isVisible);

    Long countByProduct(Product product);

    Long countByProductAndIsVisible(Product product, Boolean isVisible);


    List<Review> findByIsVisible(Boolean isVisible);
}
