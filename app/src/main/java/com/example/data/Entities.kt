package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "harvest_records")
data class HarvestRecord(
    @PrimaryKey val id: String,
    val dateTime: String,       // YYYY-MM-DD
    val product: String,
    val goodQty: Double,
    val badQty: Double,
    val goodPrice: Double,
    val badPrice: Double
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val name: String
)
