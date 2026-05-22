package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HarvestDao {
    @Query("SELECT * FROM harvest_records ORDER BY dateTime DESC")
    fun getAllRecords(): Flow<List<HarvestRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HarvestRecord)

    @Update
    suspend fun updateRecord(record: HarvestRecord)

    @Delete
    suspend fun deleteRecord(record: HarvestRecord)

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("DELETE FROM harvest_records")
    suspend fun clearAllRecords()

    @Query("DELETE FROM products")
    suspend fun clearAllProducts()
}
