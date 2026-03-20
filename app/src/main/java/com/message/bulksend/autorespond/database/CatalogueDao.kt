package com.message.bulksend.autorespond.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogueDao {

    // =================== CATALOGUE ===================

    @Query("SELECT * FROM catalogues WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllCatalogues(): Flow<List<Catalogue>>

    @Query("SELECT * FROM catalogues WHERE isActive = 1 ORDER BY updatedAt DESC")
    suspend fun getAllCataloguesSync(): List<Catalogue>

    @Query("SELECT * FROM catalogues WHERE id = :id")
    suspend fun getCatalogueById(id: Long): Catalogue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogue(catalogue: Catalogue): Long

    @Update
    suspend fun updateCatalogue(catalogue: Catalogue)

    @Query("DELETE FROM catalogues WHERE id = :id")
    suspend fun deleteCatalogue(id: Long)

    // =================== PRODUCTS IN CATALOGUE ===================

    @Query("SELECT * FROM products WHERE catalogueId = :catalogueId ORDER BY sortOrder ASC, updatedAt DESC")
    fun getProductsInCatalogue(catalogueId: Long): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE catalogueId = :catalogueId ORDER BY sortOrder ASC")
    suspend fun getProductsInCatalogueSync(catalogueId: Long): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE catalogueId = :catalogueId AND isVisible = 1")
    suspend fun getProductCountInCatalogue(catalogueId: Long): Int

    // =================== ATTRIBUTE GROUPS ===================

    @Query("SELECT * FROM attribute_groups WHERE productId = :productId ORDER BY displayOrder ASC")
    suspend fun getAttributeGroups(productId: Long): List<AttributeGroup>

    @Query(
        "SELECT * FROM attribute_groups WHERE productId = :productId AND LOWER(name) = LOWER(:name) LIMIT 1"
    )
    suspend fun findAttributeGroupByName(productId: Long, name: String): AttributeGroup?

    @Query("SELECT * FROM attribute_groups WHERE productId = :productId ORDER BY displayOrder ASC")
    fun getAttributeGroupsFlow(productId: Long): Flow<List<AttributeGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttributeGroup(group: AttributeGroup): Long

    @Update
    suspend fun updateAttributeGroup(group: AttributeGroup)

    @Query("DELETE FROM attribute_groups WHERE id = :id")
    suspend fun deleteAttributeGroup(id: Long)

    @Query("DELETE FROM attribute_groups WHERE productId = :productId")
    suspend fun deleteAllGroupsForProduct(productId: Long)

    // =================== ATTRIBUTE OPTIONS ===================

    @Query("SELECT * FROM attribute_options WHERE groupId = :groupId ORDER BY displayOrder ASC")
    suspend fun getOptions(groupId: Long): List<AttributeOption>

    @Query(
        "SELECT * FROM attribute_options WHERE groupId = :groupId AND LOWER(value) = LOWER(:value) LIMIT 1"
    )
    suspend fun findOptionByValue(groupId: Long, value: String): AttributeOption?

    @Query("SELECT * FROM attribute_options WHERE groupId = :groupId ORDER BY displayOrder ASC")
    fun getOptionsFlow(groupId: Long): Flow<List<AttributeOption>>

    @Query("SELECT * FROM attribute_options WHERE id IN (:ids)")
    suspend fun getOptionsByIds(ids: List<Long>): List<AttributeOption>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOption(option: AttributeOption): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOptions(options: List<AttributeOption>)

    @Update
    suspend fun updateOption(option: AttributeOption)

    @Query("DELETE FROM attribute_options WHERE id = :id")
    suspend fun deleteOption(id: Long)

    @Query("DELETE FROM attribute_options WHERE groupId = :groupId")
    suspend fun deleteOptionsForGroup(groupId: Long)

    // =================== PRODUCT VARIANTS ===================

    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    suspend fun getVariants(productId: Long): List<ProductVariant>

    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    fun getVariantsFlow(productId: Long): Flow<List<ProductVariant>>

    @Query("SELECT * FROM product_variants WHERE id = :id")
    suspend fun getVariantById(id: Long): ProductVariant?

    @Query(
        "SELECT * FROM product_variants WHERE productId = :productId AND optionIds = :optionIds LIMIT 1"
    )
    suspend fun findVariantByOptionIds(productId: Long, optionIds: String): ProductVariant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariant): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<ProductVariant>)

    @Update
    suspend fun updateVariant(variant: ProductVariant)

    @Query("DELETE FROM product_variants WHERE id = :id")
    suspend fun deleteVariant(id: Long)

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteAllVariantsForProduct(productId: Long)
}
