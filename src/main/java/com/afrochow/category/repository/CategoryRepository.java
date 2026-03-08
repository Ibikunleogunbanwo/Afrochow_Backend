package com.afrochow.category.repository;

import com.afrochow.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    // ======= BASIC QUERIES =======

    Optional<Category> findByName(String name);

    Boolean existsByName(String name);

    Boolean existsByNameIgnoreCase(String name);

    // ======= ACTIVE STATUS QUERIES =======

    List<Category> findByIsActive(Boolean isActive);

    // FIXED: Added missing parameter
    List<Category> findByIsActiveOrderByDisplayOrderAsc(Boolean isActive);

    Long countByIsActive(Boolean isActive);

    // ======= SEARCH QUERIES =======

    List<Category> findByNameContainingIgnoreCaseAndIsActive(String name, Boolean isActive);

    // ======= SORTING QUERIES =======

    List<Category> findAllByOrderByDisplayOrderAsc();

    // ======= OPTIMIZED QUERIES WITH JOIN FETCH =======

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.products WHERE c.isActive = true ORDER BY c.displayOrder ASC")
    List<Category> findAllActiveCategoriesWithProducts();

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.products WHERE c.isActive = :isActive ORDER BY c.displayOrder ASC")
    List<Category> findByIsActiveWithProducts(@Param("isActive") Boolean isActive);

    // ======= COUNT QUERIES =======

    @Query("SELECT COUNT(c) FROM Category c WHERE c.isActive = true")
    Long countActiveCategories();

    @Query("SELECT COUNT(c) FROM Category c WHERE c.isActive = false")
    Long countInactiveCategories();

    // ======= ADMIN DASHBOARD QUERIES =======

    @Query("SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.products ORDER BY c.displayOrder ASC")
    List<Category> findAllCategoriesWithProducts();

    @Query("SELECT c FROM Category c WHERE c.name LIKE %:searchTerm% OR c.description LIKE %:searchTerm% ORDER BY c.displayOrder ASC")
    List<Category> searchCategories(@Param("searchTerm") String searchTerm);
}