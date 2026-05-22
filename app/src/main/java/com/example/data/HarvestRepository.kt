package com.example.data

import kotlinx.coroutines.flow.Flow

class HarvestRepository(private val harvestDao: HarvestDao) {
    val allRecords: Flow<List<HarvestRecord>> = harvestDao.getAllRecords()
    val allProducts: Flow<List<Product>> = harvestDao.getAllProducts()

    suspend fun insertRecord(record: HarvestRecord) {
        harvestDao.insertRecord(record)
    }

    suspend fun updateRecord(record: HarvestRecord) {
        harvestDao.updateRecord(record)
    }

    suspend fun deleteRecord(record: HarvestRecord) {
        harvestDao.deleteRecord(record)
    }

    suspend fun insertProduct(product: Product) {
        harvestDao.insertProduct(product)
    }

    suspend fun deleteProduct(product: Product) {
        harvestDao.deleteProduct(product)
    }

    suspend fun restoreBackup(productsList: List<String>, recordsList: List<HarvestRecord>) {
        harvestDao.clearAllRecords()
        harvestDao.clearAllProducts()
        harvestDao.insertProducts(productsList.map { Product(it) })
        for (record in recordsList) {
            harvestDao.insertRecord(record)
        }
    }
}
