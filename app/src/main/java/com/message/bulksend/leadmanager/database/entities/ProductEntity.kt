package com.message.bulksend.leadmanager.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.message.bulksend.leadmanager.model.ProductType
import com.message.bulksend.leadmanager.model.ServiceType

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: ProductType,
    val category: String = "",
    val subcategory: String = "",
    val mrp: String = "",
    val sellingPrice: String = "",
    val description: String = "",
    
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
    val serviceType: ServiceType? = null,
    val duration: String = "",
    val deliveryTime: String = ""
)