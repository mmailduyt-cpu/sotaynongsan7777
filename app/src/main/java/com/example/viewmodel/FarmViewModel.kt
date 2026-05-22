package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.HarvestRecord
import com.example.data.HarvestRepository
import com.example.data.Product
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@JsonClass(generateAdapter = true)
data class BackupRecord(
    val id: String,
    val dateTime: String,
    val product: String,
    val goodQty: Double,
    val badQty: Double,
    val goodPrice: Double,
    val badPrice: Double
)

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val exportedAt: String?,
    val products: List<String>,
    val records: List<BackupRecord>
)

class FarmViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: HarvestRepository

    val allRecords: StateFlow<List<HarvestRecord>>
    val allProducts: StateFlow<List<Product>>

    val activeTab = MutableStateFlow("input") // "input", "history", "summary"

    // New record form state
    val formDate = MutableStateFlow("")
    val formProduct = MutableStateFlow("")
    val formGoodQty = MutableStateFlow("0")
    val formGoodPrice = MutableStateFlow("0")
    val formBadQty = MutableStateFlow("0")
    val formBadPrice = MutableStateFlow("0")

    // Editing states
    val editingRecordId = MutableStateFlow<String?>(null)
    val editProduct = MutableStateFlow("")
    val editGoodQty = MutableStateFlow("0")
    val editGoodPrice = MutableStateFlow("0")
    val editBadQty = MutableStateFlow("0")
    val editBadPrice = MutableStateFlow("0")

    // Dialog & UI flows
    val showProductManager = MutableStateFlow(false)
    val newProductNameInput = MutableStateFlow("")
    val pendingDeleteRecord = MutableStateFlow<HarvestRecord?>(null)
    val pendingDeleteProduct = MutableStateFlow<Product?>(null)

    // Alert Notification / Notice
    val noticeMessage = MutableStateFlow<String?>(null)
    val isNoticeError = MutableStateFlow(false)

    init {
        val database = AppDatabase.getDatabase(application, viewModelScope)
        repository = HarvestRepository(database.harvestDao())

        allRecords = repository.allRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
        allProducts = repository.allProducts.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Set default values
        formDate.value = getCurrentDateStr()

        // Sync default selected product
        viewModelScope.launch {
            allProducts.collectLatest { products ->
                if (formProduct.value.isEmpty() && products.isNotEmpty()) {
                    formProduct.value = products.first().name
                }
            }
        }
    }

    private fun getCurrentDateStr(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    fun showNotice(message: String, isError: Boolean) {
        noticeMessage.value = message
        isNoticeError.value = isError
    }

    fun clearNotice() {
        noticeMessage.value = null
    }

    fun selectProduct(productName: String) {
        formProduct.value = productName
    }

    fun addHarvestRecord() {
        val date = formDate.value
        val prodName = formProduct.value
        val goodQ = formGoodQty.value.toDoubleOrNull() ?: 0.0
        val goodP = formGoodPrice.value.toDoubleOrNull() ?: 0.0
        val badQ = formBadQty.value.toDoubleOrNull() ?: 0.0
        val badP = formBadPrice.value.toDoubleOrNull() ?: 0.0

        if (date.isEmpty() || prodName.isEmpty()) {
            showNotice("Lỗi: Vui lòng nhập đầy đủ ngày và loại nông sản.", true)
            return
        }

        if (goodQ < 0.0 || goodP < 0.0 || badQ < 0.0 || badP < 0.0) {
            showNotice("Lỗi: Số lượng và đơn giá không thể nhỏ hơn 0.", true)
            return
        }

        if (goodQ <= 0.0 && badQ <= 0.0 && goodP <= 0.0 && badP <= 0.0) {
            showNotice("Dòng ghi chép đang trống, chưa có số lượng và giá.", true)
            return
        }
        if (goodQ <= 0.0 && badQ <= 0.0) {
            showNotice("Bạn đã nhập giá nhưng chưa nhập số lượng.", true)
            return
        }
        if (goodQ > 0.0 && goodP <= 0.0) {
            showNotice("Hàng ngon có số lượng nhưng chưa có giá.", true)
            return
        }
        if (badQ > 0.0 && badP <= 0.0) {
            showNotice("Hàng dạt có số lượng nhưng chưa có giá.", true)
            return
        }

        val record = HarvestRecord(
            id = UUID.randomUUID().toString(),
            dateTime = date,
            product = prodName,
            goodQty = goodQ,
            badQty = badQ,
            goodPrice = goodP,
            badPrice = badP
        )

        viewModelScope.launch {
            repository.insertRecord(record)
            // Reset quantities, keep prices & date
            formGoodQty.value = "0"
            formBadQty.value = "0"
            showNotice("Đã lưu ghi chép thành công.", false)
        }
    }

    fun startEditing(record: HarvestRecord) {
        editingRecordId.value = record.id
        editProduct.value = record.product
        editGoodQty.value = record.goodQty.toInt().toString()
        editGoodPrice.value = record.goodPrice.toInt().toString()
        editBadQty.value = record.badQty.toInt().toString()
        editBadPrice.value = record.badPrice.toInt().toString()
    }

    fun cancelEditing() {
        editingRecordId.value = null
    }

    fun saveEditing(recordId: String, dateTime: String) {
        val prodName = editProduct.value
        val goodQ = editGoodQty.value.toDoubleOrNull() ?: 0.0
        val goodP = editGoodPrice.value.toDoubleOrNull() ?: 0.0
        val badQ = editBadQty.value.toDoubleOrNull() ?: 0.0
        val badP = editBadPrice.value.toDoubleOrNull() ?: 0.0

        if (prodName.isEmpty()) {
            showNotice("Lỗi: Loại nông sản không được để trống.", true)
            return
        }

        if (goodQ < 0.0 || goodP < 0.0 || badQ < 0.0 || badP < 0.0) {
            showNotice("Lỗi: Số lượng và đơn giá không thể nhỏ hơn 0.", true)
            return
        }

        if (goodQ <= 0.0 && badQ <= 0.0 && goodP <= 0.0 && badP <= 0.0) {
            showNotice("Dòng ghi chép đang trống, chưa có số lượng và giá.", true)
            return
        }
        if (goodQ <= 0.0 && badQ <= 0.0) {
            showNotice("Bạn đã nhập giá nhưng chưa nhập số lượng.", true)
            return
        }
        if (goodQ > 0.0 && goodP <= 0.0) {
            showNotice("Hàng ngon có số lượng nhưng chưa có giá.", true)
            return
        }
        if (badQ > 0.0 && badP <= 0.0) {
            showNotice("Hàng dạt có số lượng nhưng chưa có giá.", true)
            return
        }

        val record = HarvestRecord(
            id = recordId,
            dateTime = dateTime,
            product = prodName,
            goodQty = goodQ,
            badQty = badQ,
            goodPrice = goodP,
            badPrice = badP
        )

        viewModelScope.launch {
            repository.updateRecord(record)
            editingRecordId.value = null
            showNotice("Đã lưu chỉnh sửa thành công.", false)
        }
    }

    fun addProduct() {
        val name = newProductNameInput.value.trim()
        if (name.isEmpty()) {
            showNotice("Vui lòng nhập tên nông sản cần thêm.", true)
            return
        }
        if (allProducts.value.any { it.name.equals(name, ignoreCase = true) }) {
            showNotice("Loại nông sản này đã có trong danh sách.", true)
            return
        }
        viewModelScope.launch {
            repository.insertProduct(Product(name))
            newProductNameInput.value = ""
            showNotice("Đã thêm nông sản: $name.", false)
        }
    }

    fun requestDeleteProduct(product: Product) {
        val hasRecord = allRecords.value.any { it.product == product.name }
        if (hasRecord) {
            showNotice("Không thể xóa loại nông sản đang có trong sổ thu hoạch.", true)
            return
        }
        if (allProducts.value.size <= 1) {
            showNotice("Cần giữ lại ít nhất một loại nông sản.", true)
            return
        }
        pendingDeleteProduct.value = product
    }

    fun confirmDeleteProduct() {
        val product = pendingDeleteProduct.value ?: return
        viewModelScope.launch {
            repository.deleteProduct(product)
            if (formProduct.value == product.name) {
                val firstProd = allProducts.value.firstOrNull { it.name != product.name }
                formProduct.value = firstProd?.name ?: ""
            }
            pendingDeleteProduct.value = null
            showNotice("Đã xóa nông sản: ${product.name}.", false)
        }
    }

    fun cancelDeleteProduct() {
        pendingDeleteProduct.value = null
    }

    fun requestDeleteRecord(record: HarvestRecord) {
        pendingDeleteRecord.value = record
    }

    fun confirmDeleteRecord() {
        val record = pendingDeleteRecord.value ?: return
        viewModelScope.launch {
            repository.deleteRecord(record)
            if (editingRecordId.value == record.id) {
                cancelEditing()
            }
            pendingDeleteRecord.value = null
            showNotice("Đã xóa dòng ghi chép.", false)
        }
    }

    fun cancelDeleteRecord() {
        pendingDeleteRecord.value = null
    }

    fun generateBackupJson(): String {
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(BackupPayload::class.java)
            val formatStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            formatStr.timeZone = TimeZone.getTimeZone("UTC")
            val pList = allProducts.value.map { it.name }
            val rList = allRecords.value.map {
                BackupRecord(
                    id = it.id,
                    dateTime = it.dateTime,
                    product = it.product,
                    goodQty = it.goodQty,
                    badQty = it.badQty,
                    goodPrice = it.goodPrice,
                    badPrice = it.badPrice
                )
            }
            val payload = BackupPayload(
                exportedAt = formatStr.format(Date()),
                products = pList,
                records = rList
            )
            adapter.toJson(payload)
        } catch (e: Exception) {
            ""
        }
    }

    fun restoreBackupJson(jsonStr: String): Boolean {
        return try {
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(BackupPayload::class.java)
            val payload = adapter.fromJson(jsonStr) ?: return false
            if (payload.products.isEmpty()) return false

            viewModelScope.launch {
                val dbRecords = payload.records.map {
                    HarvestRecord(
                        id = it.id,
                        dateTime = it.dateTime,
                        product = it.product,
                        goodQty = it.goodQty,
                        badQty = it.badQty,
                        goodPrice = it.goodPrice,
                        badPrice = it.badPrice
                    )
                }
                repository.restoreBackup(payload.products, dbRecords)

                if (payload.products.isNotEmpty()) {
                    formProduct.value = payload.products.first()
                }
                showNotice("Đã khôi phục dữ liệu thành công.", false)
            }
            true
        } catch (e: Exception) {
            showNotice("Lỗi: Định dạng file sao lưu không hợp lệ.", true)
            false
        }
    }
}

class FarmViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FarmViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FarmViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
