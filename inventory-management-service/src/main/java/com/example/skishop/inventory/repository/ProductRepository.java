package com.example.skishop.inventory.repository;

import com.example.skishop.inventory.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    /** SKU リストに一致する商品を取得 (sales-management の集計で利用)。 */
    List<Product> findBySkuIn(Collection<String> skus);

    Page<Product> findByCategoryId(String categoryId, Pageable pageable);

    Page<Product> findByStatus(Product.ProductStatus status, Pageable pageable);

    Page<Product> findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(String name, String brand, Pageable pageable);

    @Query("""
            {
              'status': 'ACTIVE',
              '$expr': { '$gt': ['$stockQuantity', '$reservedQuantity'] },
              '$or': [
                { 'name': { '$regex': ?0, '$options': 'i' } },
                { 'brand': { '$regex': ?0, '$options': 'i' } },
                { 'description': { '$regex': ?0, '$options': 'i' } },
                { 'tags': { '$regex': ?0, '$options': 'i' } }
              ]
            }
            """)
    Page<Product> searchActiveAvailable(String query, Pageable pageable);

              @Query("""
                {
                  'categoryId': ?1,
                  'status': 'ACTIVE',
                  '$expr': { '$gt': ['$stockQuantity', '$reservedQuantity'] },
                  '$or': [
              { 'name': { '$regex': ?0, '$options': 'i' } },
              { 'brand': { '$regex': ?0, '$options': 'i' } },
              { 'description': { '$regex': ?0, '$options': 'i' } },
              { 'tags': { '$regex': ?0, '$options': 'i' } }
                  ]
                }
                """)
              Page<Product> searchActiveAvailableByCategoryId(String query, String categoryId, Pageable pageable);

    @Query("""
            {
              'categoryId': ?0,
              'status': 'ACTIVE',
              '$expr': { '$gt': ['$stockQuantity', '$reservedQuantity'] }
            }
            """)
    Page<Product> findActiveAvailableByCategoryId(String categoryId, Pageable pageable);

    @Query("{ 'status': 'ACTIVE', 'stockQuantity': { $gte: 0, $lte: ?0 } }")
    Page<Product> findLowStockProducts(int threshold, Pageable pageable);
}
