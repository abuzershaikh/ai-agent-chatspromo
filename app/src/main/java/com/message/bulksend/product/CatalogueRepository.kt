package com.message.bulksend.product

import android.content.Context
import com.message.bulksend.autorespond.database.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

/**
 * Business logic for Catalogues, Attribute Groups, Options & Variants
 */
class CatalogueRepository(context: Context) {

    private val db = com.message.bulksend.autorespond.database.MessageDatabase.getDatabase(context)
    private val dao = db.catalogueDao()
    private val productDao = db.productDao()

    // ============= CATALOGUE CRUD =============

    fun getAllCatalogues(): Flow<List<Catalogue>> = dao.getAllCatalogues()

    suspend fun getAllCataloguesSync(): List<Catalogue> = dao.getAllCataloguesSync()

    suspend fun getCatalogueById(id: Long): Catalogue? = dao.getCatalogueById(id)

    suspend fun createCatalogue(name: String, description: String = "", themeColor: String = "#8B5CF6"): Long {
        return dao.insertCatalogue(
            Catalogue(name = name, description = description, themeColor = themeColor)
        )
    }

    suspend fun updateCatalogue(catalogue: Catalogue) {
        dao.updateCatalogue(catalogue.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCatalogue(id: Long) {
        // Products will remain but their catalogueId will be orphaned
        dao.deleteCatalogue(id)
    }

    // ============= PRODUCTS IN CATALOGUE =============

    fun getProductsForCatalogue(catalogueId: Long): Flow<List<Product>> =
        dao.getProductsInCatalogue(catalogueId)

    suspend fun getProductsForCatalogueSync(catalogueId: Long): List<Product> =
        dao.getProductsInCatalogueSync(catalogueId)

    suspend fun getProductCount(catalogueId: Long): Int =
        dao.getProductCountInCatalogue(catalogueId)

    // ============= ATTRIBUTE GROUPS =============

    suspend fun getAttributeGroups(productId: Long): List<AttributeGroup> =
        dao.getAttributeGroups(productId)

    fun getAttributeGroupsFlow(productId: Long): Flow<List<AttributeGroup>> =
        dao.getAttributeGroupsFlow(productId)

    suspend fun addAttributeGroup(productId: Long, name: String, type: String = "SELECT"): Long {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return -1L

        val existing = dao.findAttributeGroupByName(productId, normalizedName)
        if (existing != null) return existing.id

        val existingGroups = dao.getAttributeGroups(productId)
        return dao.insertAttributeGroup(
            AttributeGroup(
                productId = productId,
                name = normalizedName,
                type = type,
                displayOrder = existingGroups.size
            )
        )
    }

    suspend fun deleteAttributeGroup(groupId: Long) {
        dao.deleteOptionsForGroup(groupId)
        dao.deleteAttributeGroup(groupId)
    }

    // ============= ATTRIBUTE OPTIONS =============

    suspend fun getOptions(groupId: Long): List<AttributeOption> = dao.getOptions(groupId)

    fun getOptionsFlow(groupId: Long): Flow<List<AttributeOption>> = dao.getOptionsFlow(groupId)

    suspend fun addOption(groupId: Long, value: String, hexColor: String = ""): Long {
        val normalizedValue = value.trim()
        if (normalizedValue.isBlank()) return -1L

        val existingOption = dao.findOptionByValue(groupId, normalizedValue)
        if (existingOption != null) return existingOption.id

        val existingOptions = dao.getOptions(groupId)
        return dao.insertOption(
            AttributeOption(
                groupId = groupId,
                value = normalizedValue,
                hexColor = normalizeHexColor(hexColor),
                displayOrder = existingOptions.size
            )
        )
    }

    suspend fun addOptions(groupId: Long, values: List<String>) {
        val normalizedValues =
            values.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        if (normalizedValues.isEmpty()) return

        val existing = dao.getOptions(groupId).map { it.value.lowercase() }.toSet()
        val filteredValues = normalizedValues.filter { it.lowercase() !in existing }
        if (filteredValues.isEmpty()) return

        val currentCount = dao.getOptions(groupId).size
        val options = filteredValues.mapIndexed { i, v ->
            AttributeOption(groupId = groupId, value = v, displayOrder = currentCount + i)
        }
        dao.insertOptions(options)
    }

    suspend fun deleteOption(optionId: Long) = dao.deleteOption(optionId)

    // ============= VARIANTS =============

    suspend fun getVariants(productId: Long): List<ProductVariant> = dao.getVariants(productId)

    fun getVariantsFlow(productId: Long): Flow<List<ProductVariant>> = dao.getVariantsFlow(productId)

    /**
     * Auto-generate all variant combinations (cartesian product)
     * Example: Size[S,M,L] x Color[Red,Blue] = 6 variants
     */
    suspend fun generateVariants(productId: Long, basePrice: Double = 0.0): List<ProductVariant> {
        val groups = dao.getAttributeGroups(productId).sortedBy { it.displayOrder }
        if (groups.isEmpty()) return emptyList()

        // Get options per group
        val groupOptions = groups.map { group -> dao.getOptions(group.id).sortedBy { it.displayOrder } }
        if (groupOptions.any { it.isEmpty() }) {
            return dao.getVariants(productId)
        }

        // Cartesian product of all option lists
        val combinations = cartesianProduct(groupOptions).map { canonicalOptionIds(it) }.distinct()
        val existingVariants = dao.getVariants(productId)
        val existingByKey = existingVariants.associateBy { canonicalOptionIds(parseOptionIds(it.optionIds)) }

        // Delete old variants and recreate while preserving user-edited fields wherever possible.
        dao.deleteAllVariantsForProduct(productId)

        val variants =
            combinations.map { optionKey ->
                val existingVariant = existingByKey[optionKey]
                ProductVariant(
                    id = existingVariant?.id ?: 0L,
                    productId = productId,
                    price =
                        if (existingVariant != null && existingVariant.price > 0.0) {
                            existingVariant.price
                        } else {
                            basePrice
                        },
                    stock = existingVariant?.stock ?: -1,
                    sku = existingVariant?.sku ?: "",
                    imageOverride = existingVariant?.imageOverride ?: "",
                    isAvailable = existingVariant?.isAvailable ?: true,
                    optionIds = optionKey
                )
            }

        if (variants.isNotEmpty()) {
            dao.insertVariants(variants)
        }
        return dao.getVariants(productId)
    }

    /**
     * Find variant matching given option values (e.g. "M" + "Red")
     */
    suspend fun findVariantByOptions(productId: Long, optionValues: List<String>): ProductVariant? {
        if (optionValues.isEmpty()) return null
        val variants = dao.getVariants(productId)
        val lowerValues = optionValues.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (lowerValues.isEmpty()) return null

        val matchedVariants = mutableListOf<Pair<ProductVariant, List<String>>>()

        for (variant in variants) {
            val ids = parseOptionIds(variant.optionIds)
            val options = dao.getOptionsByIds(ids)
            val variantValues = options.map { it.value.trim().lowercase() }
            if (lowerValues.all { it in variantValues }) {
                matchedVariants.add(variant to variantValues)
            }
        }

        if (matchedVariants.isEmpty()) return null
        if (matchedVariants.size == 1) return matchedVariants.first().first

        val exactCardinalityMatches =
            matchedVariants.filter { (_, values) -> values.size == lowerValues.size }
        if (exactCardinalityMatches.size == 1) {
            return exactCardinalityMatches.first().first
        }

        // Ambiguous match - caller should ask follow-up question.
        return null
    }

    suspend fun updateVariant(variant: ProductVariant) = dao.updateVariant(variant)

    suspend fun deleteVariant(variantId: Long) = dao.deleteVariant(variantId)

    // ============= AI CONTEXT =============

    /**
     * Build a rich text summary of a catalogue for AI system prompt
     */
    suspend fun getCatalogueAsText(catalogueId: Long): String {
        val catalogue = dao.getCatalogueById(catalogueId) ?: return ""
        val products = dao.getProductsInCatalogueSync(catalogueId)

        return buildString {
            appendLine("CATALOGUE: ${catalogue.name}")
            if (catalogue.description.isNotBlank()) appendLine("Description: ${catalogue.description}")
            appendLine("Products: ${products.size}")
            appendLine()

            products.forEach { product ->
                appendLine("  Product: ${product.name}")
                if (product.price > 0) appendLine("  Base Price: ${product.currency} ${product.price}")
                if (product.description.isNotBlank()) appendLine("  Desc: ${product.description}")

                // Attribute groups and options
                val groups = dao.getAttributeGroups(product.id)
                groups.forEach { group ->
                    val opts = dao.getOptions(group.id)
                    appendLine("  ${group.name}: ${opts.joinToString(", ") { it.value }}")
                }

                // Variants summary
                val variants = dao.getVariants(product.id)
                if (variants.isNotEmpty()) {
                    val variantLines = variants.filter { it.isAvailable }.mapNotNull { v ->
                        val ids = parseOptionIds(v.optionIds)
                        val opts = dao.getOptionsByIds(ids)
                        val combo = opts.joinToString(" + ") { it.value }
                        if (combo.isBlank()) null
                        else "  Variant: $combo${if (v.price > 0) " = ${product.currency} ${v.price}" else ""}${if (v.stock >= 0) " (stock: ${v.stock})" else ""}"
                    }
                    variantLines.forEach { appendLine(it) }
                }
                appendLine()
            }
        }
    }

    // ============= PRIVATE HELPERS =============

    private fun parseOptionIds(json: String): List<Long> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun canonicalOptionIds(ids: List<Long>): String {
        val sortedDistinct = ids.distinct().sorted()
        return JSONArray(sortedDistinct).toString()
    }

    private fun normalizeHexColor(raw: String): String {
        val cleaned = raw.trim().trimStart('#')
        return if (cleaned.length == 6 && cleaned.all { it.isLetterOrDigit() }) {
            "#$cleaned"
        } else {
            ""
        }
    }

    private fun cartesianProduct(lists: List<List<AttributeOption>>): List<List<Long>> {
        if (lists.isEmpty()) return emptyList()
        var result = lists[0].map { listOf(it.id) }
        for (i in 1 until lists.size) {
            result = result.flatMap { existing ->
                lists[i].map { opt -> existing + opt.id }
            }
        }
        return result
    }
}
