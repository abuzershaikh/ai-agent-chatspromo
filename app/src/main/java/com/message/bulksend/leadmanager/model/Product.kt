package com.message.bulksend.leadmanager.model

data class Product(
    val id: String,
    val name: String,
    val type: ProductType,
    val category: String = "",
    val subcategory: String = "",
    val mrp: String = "",
    val sellingPrice: String = "",
    val description: String = "",
    val isActive: Boolean = true,
    // Physical Product Fields
    val color: String = "",
    val size: String = "",
    val height: String = "",
    val width: String = "",
    val weight: String = "",
    // Digital Product Fields
    val downloadLink: String = "",
    val licenseType: String = "",
    val version: String = "",
    // Service Fields
    val serviceType: ServiceType = ServiceType.ONLINE,
    val duration: String = "",
    val deliveryTime: String = ""
)

enum class ProductType(val displayName: String, val color: Long) {
    PHYSICAL("Physical Product", 0xFF3B82F6),
    DIGITAL("Digital Product", 0xFF10B981),
    SERVICE("Service", 0xFFF59E0B),
    SOFTWARE("Software", 0xFF8B5CF6)
}

enum class ServiceType(val displayName: String) {
    ONLINE("Online Service"),
    OFFLINE("Offline Service"),
    HYBRID("Hybrid Service")
}

data class ProductFieldSettings(
    val physicalFields: Map<String, Boolean> = mapOf(
        "category" to true,
        "subcategory" to true,
        "mrp" to true,
        "sellingPrice" to true,
        "color" to true,
        "size" to true,
        "height" to true,
        "width" to true,
        "weight" to true,
        "description" to true
    ),
    val digitalFields: Map<String, Boolean> = mapOf(
        "category" to true,
        "subcategory" to true,
        "mrp" to true,
        "sellingPrice" to true,
        "downloadLink" to true,
        "licenseType" to true,
        "version" to true,
        "description" to true
    ),
    val serviceFields: Map<String, Boolean> = mapOf(
        "category" to true,
        "subcategory" to true,
        "mrp" to true,
        "sellingPrice" to true,
        "serviceType" to true,
        "duration" to true,
        "deliveryTime" to true,
        "description" to true
    ),
    val softwareFields: Map<String, Boolean> = mapOf(
        "category" to true,
        "subcategory" to true,
        "mrp" to true,
        "sellingPrice" to true,
        "version" to true,
        "licenseType" to true,
        "downloadLink" to true,
        "description" to true
    )
) {
    fun isFieldEnabled(type: ProductType, fieldName: String): Boolean {
        return when (type) {
            ProductType.PHYSICAL -> physicalFields[fieldName] ?: false
            ProductType.DIGITAL -> digitalFields[fieldName] ?: false
            ProductType.SERVICE -> serviceFields[fieldName] ?: false
            ProductType.SOFTWARE -> softwareFields[fieldName] ?: false
        }
    }
    
    fun toggleField(type: ProductType, fieldName: String, enabled: Boolean): ProductFieldSettings {
        return when (type) {
            ProductType.PHYSICAL -> copy(physicalFields = physicalFields + (fieldName to enabled))
            ProductType.DIGITAL -> copy(digitalFields = digitalFields + (fieldName to enabled))
            ProductType.SERVICE -> copy(serviceFields = serviceFields + (fieldName to enabled))
            ProductType.SOFTWARE -> copy(softwareFields = softwareFields + (fieldName to enabled))
        }
    }
}
