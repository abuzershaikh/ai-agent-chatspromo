package com.message.bulksend.autorespond.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE isVisible = 1 ORDER BY updatedAt DESC")
    fun getVisibleProducts(): Flow<List<Product>>
    
    @Query("SELECT * FROM products WHERE isVisible = 1")
    suspend fun getVisibleProductsSync(): List<Product>
    
    @Query("SELECT * FROM products ORDER BY updatedAt DESC")
    fun getAllProductsSync(): List<Product>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductById(productId: Long): Product?
    
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' OR customFields LIKE '%' || :query || '%'")
    suspend fun searchProducts(query: String): List<Product>
    
    @Query("SELECT * FROM products WHERE category = :category AND isVisible = 1 ORDER BY name ASC")
    suspend fun getProductsByCategory(category: String): List<Product>
    
    @Query("SELECT DISTINCT category FROM products WHERE category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)
    
    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: Long)
    
    @Query("SELECT COUNT(*) FROM products WHERE isVisible = 1")
    suspend fun getProductCount(): Int
}
