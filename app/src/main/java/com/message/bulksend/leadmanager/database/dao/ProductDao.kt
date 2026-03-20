package com.message.bulksend.leadmanager.database.dao

import androidx.room.*
import com.message.bulksend.leadmanager.database.entities.ProductEntity
import com.message.bulksend.leadmanager.model.ProductType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsList(): List<ProductEntity>
    
    @Query("SELECT * FROM products WHERE type = :type ORDER BY name ASC")
    suspend fun getProductsByType(type: ProductType): List<ProductEntity>
    
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: String): ProductEntity?
    
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%'")
    suspend fun searchProducts(query: String): List<ProductEntity>
    
    @Query("SELECT DISTINCT category FROM products WHERE category != '' ORDER BY category ASC")
    suspend fun getAllCategories(): List<String>
    
    @Query("SELECT DISTINCT subcategory FROM products WHERE subcategory != '' AND category = :category ORDER BY subcategory ASC")
    suspend fun getSubcategoriesByCategory(category: String): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)
    
    @Update
    suspend fun updateProduct(product: ProductEntity)
    
    @Delete
    suspend fun deleteProduct(product: ProductEntity)
    
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: String)
    
    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
    
    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductsCount(): Int
    
    @Query("SELECT COUNT(*) FROM products WHERE type = :type")
    suspend fun getProductsCountByType(type: ProductType): Int
    
    @Query("SELECT * FROM products WHERE name = :name LIMIT 1")
    suspend fun getProductByName(name: String): ProductEntity?
}