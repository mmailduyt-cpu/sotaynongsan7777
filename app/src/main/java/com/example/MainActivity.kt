package com.example

import android.app.DatePickerDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.HarvestRecord
import com.example.data.Product
import com.example.ui.theme.MyApplicationTheme
import com.example.util.LunarCalendarConverter
import com.example.viewmodel.FarmViewModel
import com.example.viewmodel.FarmViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FarmAppWrapper()
            }
        }
    }
}

// Help format price dynamically to Vietnam Dong format
fun formatVnd(amount: Double): String {
    val formatter = java.text.NumberFormat.getIntegerInstance(Locale("vi", "VN"))
    return "${formatter.format(amount)} đ"
}

// Helper to calculate total revenue
fun calcRevenue(record: HarvestRecord): Double {
    return (record.goodQty * record.goodPrice) + (record.badQty * record.badPrice)
}

// Format date String: yyyy-MM-dd to dd/MM/yyyy
fun formatDisplayDate(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        if (parts.size == 3) {
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } else {
            dateStr
        }
    } catch (e: Exception) {
        dateStr
    }
}

// Generate matching organic background hue depending on string name
fun getProductColorAccent(name: String): Color {
    val total = name.sumOf { it.code }
    val colors = listOf(
        Color(0xFF2E7D32), // Emerald Green
        Color(0xFF558B2F), // Lime Green
        Color(0xFFEF6C00), // Pumpkin Orange
        Color(0xFF00695C), // Teal Green
        Color(0xFFAD1457)  // Herb Pink
    )
    return colors[total % colors.size]
}

@Composable
fun FarmAppWrapper() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: FarmViewModel = viewModel(
        factory = FarmViewModelFactory(app)
    )

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val products by viewModel.allProducts.collectAsStateWithLifecycle()

    val formDate by viewModel.formDate.collectAsStateWithLifecycle()
    val formProduct by viewModel.formProduct.collectAsStateWithLifecycle()
    val formGoodQty by viewModel.formGoodQty.collectAsStateWithLifecycle()
    val formGoodPrice by viewModel.formGoodPrice.collectAsStateWithLifecycle()
    val formBadQty by viewModel.formBadQty.collectAsStateWithLifecycle()
    val formBadPrice by viewModel.formBadPrice.collectAsStateWithLifecycle()

    val editingRecordId by viewModel.editingRecordId.collectAsStateWithLifecycle()
    val editProduct by viewModel.editProduct.collectAsStateWithLifecycle()
    val editGoodQty by viewModel.editGoodQty.collectAsStateWithLifecycle()
    val editGoodPrice by viewModel.editGoodPrice.collectAsStateWithLifecycle()
    val editBadQty by viewModel.editBadQty.collectAsStateWithLifecycle()
    val editBadPrice by viewModel.editBadPrice.collectAsStateWithLifecycle()

    val showProductManager by viewModel.showProductManager.collectAsStateWithLifecycle()
    val newProductNameInput by viewModel.newProductNameInput.collectAsStateWithLifecycle()

    val noticeMessage by viewModel.noticeMessage.collectAsStateWithLifecycle()
    val isNoticeError by viewModel.isNoticeError.collectAsStateWithLifecycle()

    val pendingDeleteRecord by viewModel.pendingDeleteRecord.collectAsStateWithLifecycle()
    val pendingDeleteProduct by viewModel.pendingDeleteProduct.collectAsStateWithLifecycle()

    // Activity Result Launchers for modern local JSON import & export backup of DThanh Farm
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            try {
                val json = viewModel.generateBackupJson()
                context.contentResolver.openOutputStream(it)?.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }
                viewModel.showNotice("Đã xuất tệp tin sao lưu thành công.", false)
            } catch (e: Exception) {
                viewModel.showNotice("Lỗi: Không thể xuất tệp sao lưu.", true)
            }
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val json = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (json != null) {
                    val success = viewModel.restoreBackupJson(json)
                    if (success) {
                        viewModel.showNotice("Đã phục hồi dữ liệu nông sản.", false)
                    }
                } else {
                    viewModel.showNotice("Lỗi: Không thể đọc tệp tin chọn.", true)
                }
            } catch (e: Exception) {
                viewModel.showNotice("Lỗi: Không thể nhập tệp tin.", true)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            // Floating Navigation Pill in Vietnamese bottom bar format
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.dp, Color(0xFFDDE5D6), RoundedCornerShape(32.dp))
                        .background(Color.White)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "input" to "Nhập mới",
                        "history" to "Lịch sử",
                        "summary" to "Tổng kết"
                    ).forEach { (tabId, label) ->
                        val isSelected = activeTab == tabId
                        val bg = if (isSelected) Color(0xFF214D3A) else Color.Transparent
                        val textCol = if (isSelected) Color.White else Color(0xFF55604C)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(bg)
                                .clickable { viewModel.activeTab.value = tabId }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = textCol
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // Radial gradients represent the freshness and lush soils of Vietnam farms
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF3F5EF))
                .drawBehind {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFAFBF9),
                                Color(0xFFEEF2E8),
                                Color(0xFFE6EBDF)
                            )
                        )
                    )
                }
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Premium Compact Brand Header with Small Organic Logo
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B3E1C) // Deep Forest Green matching DThanh leaf border background
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, Color(0xFFD4A373)) // Elegant gold woven bamboo border
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Compact circular logo image (scaled down elegantly to 88.dp instead of a giant full screen box)
                        Image(
                            painter = painterResource(id = R.drawable.logo_no_text_1779382711881),
                            contentDescription = "DThanh Farm Logo Icon",
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.5.dp, Color(0xFFFFF4D6), RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "DThanh Farm",
                                color = Color(0xFFFFF4D6), // Gold cream color
                                style = TextStyle(
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = "SỔ TAY NÔNG SẢN",
                                color = Color.White.copy(alpha = 0.9f),
                                style = TextStyle(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.0.sp
                                )
                            )
                        }
                    }
                }

                // File Operations Menu: Backup management Import & Export buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { getContentLauncher.launch("application/json") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1B5E20)),
                        border = BorderStroke(1.dp, Color(0xFFB7DE97)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).drawBehind {
                                // Draw a custom styled "up-arrow" concept since import
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nhập tệp sao lưu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            // Create shares intent or document writing
                            createDocumentLauncher.launch("sotay_backup.json")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xuất tệp sao lưu", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Float Share Dialog choice option
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE8F5E9))
                        .clickable {
                            val backupStr = viewModel.generateBackupJson()
                            if (backupStr.isNotEmpty()) {
                                shareBackupString(context, backupStr)
                            } else {
                                viewModel.showNotice("Không có dữ liệu để chia sẻ.", true)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Chia sẻ dữ liệu qua QR/Zalo/Mạng xã hội",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                // Inline notification alert
                AnimatedVisibility(
                    visible = noticeMessage != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    noticeMessage?.let { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isNoticeError) Color(0xFFFFEBEE) else Color(0xFFE6F9D8)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isNoticeError) Color(0xFFFFCDD2) else Color(0xFFB7DE97)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isNoticeError) Color(0xFFEF5350) else Color(0xFF74B35D)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isNoticeError) Icons.Default.Close else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = msg,
                                    modifier = Modifier.weight(1f),
                                    style = TextStyle(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isNoticeError) Color(0xFFB71C1C) else Color(0xFF2E7D32)
                                    )
                                )
                                IconButton(
                                    onClick = { viewModel.clearNotice() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Tắt",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Render respective tab layouts smoothly
                when (activeTab) {
                    "input" -> InputTab(viewModel, products, formDate, formProduct, formGoodQty, formGoodPrice, formBadQty, formBadPrice, showProductManager, newProductNameInput)
                    "history" -> HistoryTab(viewModel, records, products, editingRecordId, editProduct, editGoodQty, editGoodPrice, editBadQty, editBadPrice)
                    "summary" -> SummaryTab(records)
                }

                // Add nice breathing spacer under tabs
                Spacer(modifier = Modifier.height(72.dp))
            }
        }

        // Action confirmation modals and overlay dialogs
        if (pendingDeleteRecord != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDeleteRecord() },
                title = { Text("Xóa ghi chép?", fontWeight = FontWeight.Bold) },
                text = { Text("Dòng dữ liệu này sẽ bị xóa vĩnh viễn khỏi sổ tay thu hoạch.") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        onClick = { viewModel.confirmDeleteRecord() }
                    ) {
                        Text("Xóa", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDeleteRecord() }) {
                        Text("Hủy")
                    }
                }
            )
        }

        if (pendingDeleteProduct != null) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelDeleteProduct() },
                title = { Text("Xóa loại nông sản?", fontWeight = FontWeight.Bold) },
                text = { Text("Bạn có muốn xóa \"${pendingDeleteProduct?.name}\" khỏi danh sách lựa chọn?") },
                confirmButton = {
                    TextButton(
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                        onClick = { viewModel.confirmDeleteProduct() }
                    ) {
                        Text("Xóa", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDeleteProduct() }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}

// Share backup JSON intent launcher helper
fun shareBackupString(context: Context, json: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, json)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Chia sẻ sao lưu Sổ Tay Nông Sản")
    context.startActivity(shareIntent)
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .drawBehind {
                drawLine(
                    color = Color(0xFFB7DE97),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = 2f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(10f, 10f), 0f
                    )
                )
            }
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFD9F7C8), Color(0xFFEFFFD0)))),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                )
            }
        }
    }
}

@Composable
fun InputTab(
    viewModel: FarmViewModel,
    products: List<Product>,
    formDate: String,
    formProduct: String,
    formGoodQty: String,
    formGoodPrice: String,
    formBadQty: String,
    formBadPrice: String,
    showProductManager: Boolean,
    newProductNameInput: String
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFDFE7D7))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "1. Nhập ghi chép mới",
                subtitle = "Chọn loại nông sản, số lượng thu hoạch và giá bán",
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF1B5E20)
                    )
                }
            )

            // Date picker row + Vietnamese Lunar Calendar day display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8DC), RoundedCornerShape(16.dp))
                    .background(Color(0xFFFBFCF8))
                    .clickable {
                        val parts = formDate.split("-")
                        val calendar = Calendar.getInstance()
                        val currentY = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(Calendar.YEAR)
                        val currentM = (parts.getOrNull(1)?.toIntOrNull() ?: (calendar.get(Calendar.MONTH) + 1)) - 1
                        val currentD = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(Calendar.DAY_OF_MONTH)

                        DatePickerDialog(
                            context,
                            { _, year, monthOfYear, dayOfMonth ->
                                val formatted = String.format("%04d-%02d-%02d", year, monthOfYear + 1, dayOfMonth)
                                viewModel.formDate.value = formatted
                            },
                            currentY,
                            currentM,
                            currentD
                        ).show()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("NGÀY DƯƠNG LỊCH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDisplayDate(formDate),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Black
                    )
                }

                // Show computed Vietnamese traditional Lunar Day
                val lunarString = LunarCalendarConverter.lunarTextFromDateTime(formDate)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF74B35D))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lunarString,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = Color(0xFFFBFBF9)
                    )
                }
            }

            // Products list dropdown & custom products additions manager view
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE2E8DC), RoundedCornerShape(16.dp))
                    .background(Color(0xFFFBFCF8))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOẠI NÔNG SẢN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Gray
                    )

                    Surface(
                        onClick = { viewModel.showProductManager.value = !showProductManager },
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFFE6F9D8),
                        contentColor = Color(0xFF2E7D32)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("Sửa loại", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }

                if (showProductManager) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE6F9D8).copy(alpha = 0.5f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Thêm/Xóa loại nông sản:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF1B5E20)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicTextField(
                                value = newProductNameInput,
                                onValueChange = { viewModel.newProductNameInput.value = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFB7DE97), RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                textStyle = TextStyle(fontSize = 14.sp)
                            )
                            Button(
                                onClick = { viewModel.addProduct() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Thêm", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Display list of editable items with individual cross buttons
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            products.forEach { prod ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFB7DE97), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = prod.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1B5E20)
                                    )
                                    IconButton(
                                        onClick = { viewModel.requestDeleteProduct(prod) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa",
                                            tint = Color.Red.copy(alpha = 0.7f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Product Selector Combo drop down box item
                var expandedCombo by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFE2E8DC), RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable { expandedCombo = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (formProduct.isEmpty()) "(Chưa chọn)" else formProduct,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown"
                        )
                    }

                    DropdownMenu(
                        expanded = expandedCombo,
                        onDismissRequest = { expandedCombo = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        products.forEach { prod ->
                            DropdownMenuItem(
                                text = { Text(prod.name, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    viewModel.selectProduct(prod.name)
                                    expandedCombo = false
                                }
                            )
                        }
                    }
                }
            }

            // Harvest Item Line: Hàng Ngon / Ngon
            HarvestInputCard(
                title = "Hàng ngon",
                qty = formGoodQty,
                price = formGoodPrice,
                onQtyChange = { viewModel.formGoodQty.value = it },
                onPriceChange = { viewModel.formGoodPrice.value = it },
                accentColor = Color(0xFFE8F5E9),
                titleColor = Color(0xFF1B5E20)
            )

            // Harvest Item Line: Hàng Dạt / Dạt
            HarvestInputCard(
                title = "Hàng dạt",
                qty = formBadQty,
                price = formBadPrice,
                onQtyChange = { viewModel.formBadQty.value = it },
                onPriceChange = { viewModel.formBadPrice.value = it },
                accentColor = Color(0xFFFFF3E0),
                titleColor = Color(0xFFE65100)
            )

            // Dynamic preview computation indicator display box
            val rawGoodQty = formGoodQty.toDoubleOrNull() ?: 0.0
            val rawGoodPrice = formGoodPrice.toDoubleOrNull() ?: 0.0
            val rawBadQty = formBadQty.toDoubleOrNull() ?: 0.0
            val rawBadPrice = formBadPrice.toDoubleOrNull() ?: 0.0
            val totalPreview = (rawGoodQty * rawGoodPrice) + (rawBadQty * rawBadPrice)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF4F8C3F), Color(0xFF74B35D))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("DOANH THU ƯỚC TÍNH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE8F5E9))
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatVnd(totalPreview),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                Button(
                    onClick = { viewModel.addHarvestRecord() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF214D3A)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text("Lưu ghi chép", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun MiniStepper(
    value: String,
    onValueChange: (String) -> Unit,
    step: Double,
    suffix: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFFE2E8DC), RoundedCornerShape(12.dp))
            .background(Color.White),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                val current = value.toDoubleOrNull() ?: 0.0
                val next = (current - step).coerceAtLeast(0.0)
                onValueChange(if (next % 1.0 == 0.0) next.toInt().toString() else next.toString())
            },
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(Color(0xFFFBFBF9))
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Giảm",
                tint = Color(0xFF4F8C3F)
            )
        }

        BasicTextField(
            value = value,
            onValueChange = {
                if (it.isEmpty() || it.toDoubleOrNull() != null) {
                    onValueChange(it)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    innerTextField()
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = suffix,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray
                    )
                }
            }
        )

        IconButton(
            onClick = {
                val current = value.toDoubleOrNull() ?: 0.0
                val next = current + step
                onValueChange(if (next % 1.0 == 0.0) next.toInt().toString() else next.toString())
            },
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
                .background(Color(0xFFFBFBF9))
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Tăng",
                tint = Color(0xFF4F8C3F)
            )
        }
    }
}

@Composable
fun HarvestInputCard(
    title: String,
    qty: String,
    price: String,
    onQtyChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    accentColor: Color,
    titleColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, titleColor.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .background(accentColor)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    color = titleColor
                )
                Text(
                    text = "kg ±1 · giá ±500 đ",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("SỐ LƯỢNG", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Spacer(modifier = Modifier.height(3.dp))
                    MiniStepper(
                        value = qty,
                        onValueChange = onQtyChange,
                        step = 1.0,
                        suffix = "kg"
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("ĐƠN GIÁ", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Spacer(modifier = Modifier.height(3.dp))
                    MiniStepper(
                        value = price,
                        onValueChange = onPriceChange,
                        step = 500.0,
                        suffix = "đ"
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryTab(
    viewModel: FarmViewModel,
    records: List<HarvestRecord>,
    products: List<Product>,
    editingRecordId: String?,
    editProduct: String,
    editGoodQty: String,
    editGoodPrice: String,
    editBadQty: String,
    editBadPrice: String
) {
    if (records.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFDFE7D7))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Chưa có dữ liệu thu hoạch nào.",
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Hãy nhập bản ghi ở tab 'Nhập mới'.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    } else {
        // Group harvest records by date Str
        val grouped = remember(records) {
            records.groupBy { it.dateTime }.toList().sortedByDescending { it.first }
        }

        // Space-saving collapsible dates tracker
        val collapsedDates = remember { mutableStateMapOf<String, Boolean>() }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionHeader(
                title = "2. Sổ tay lịch sử thu hoạch",
                subtitle = "Chạm mỗi dòng Ngày để thu phóng danh sách",
                icon = {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = null,
                        tint = Color(0xFF1B5E20)
                    )
                }
            )

            grouped.forEach { (dateStr, dateRecords) ->
                val daysTotalRevenue = dateRecords.sumOf { calcRevenue(it) }
                val lunarDateStr = LunarCalendarConverter.lunarTextFromDateTime(dateStr)
                // Default older dates (older than 3 days) to collapsed to save precious space
                val isCollapsed = collapsedDates[dateStr] ?: (grouped.indexOfFirst { it.first == dateStr } > 2)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8DC))
                ) {
                    Column {
                        // Date header band corresponding to the Web styled bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF5F9A4D))
                                .clickable { collapsedDates[dateStr] = !isCollapsed }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Thu phóng lịch sử ngày",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = formatDisplayDate(dateStr),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.White.copy(alpha = 0.18f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = lunarDateStr,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color(0xFFF5FFE9)
                                    )
                                }
                            }

                            Text(
                                text = "Tổng thu: " + formatVnd(daysTotalRevenue),
                                style = TextStyle(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = Color(0xFF244234)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEEF3E8))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Record entries inside this date group
                        AnimatedVisibility(
                            visible = !isCollapsed,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                dateRecords.forEach { rec ->
                                    val isEditing = editingRecordId == rec.id
                                    val colorAccent = getProductColorAccent(rec.product)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colorAccent.copy(alpha = 0.06f))
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    colorAccent.copy(alpha = 0.18f)
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .drawBehind {
                                                // Draw organic vertical line color sign on the left
                                                drawRect(
                                                    color = colorAccent,
                                                    size = androidx.compose.ui.geometry.Size(
                                                        8f,
                                                        size.height
                                                    )
                                                )
                                            }
                                            .padding(start = 14.dp)
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            if (isEditing) {
                                                // Editable Product Spinner choices form
                                                var comboExpanded by remember { mutableStateOf(false) }
                                                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                                    OutlinedButton(
                                                        onClick = { comboExpanded = true },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(
                                                            horizontal = 8.dp,
                                                            vertical = 4.dp
                                                        )
                                                    ) {
                                                        Text(
                                                            editProduct,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = comboExpanded,
                                                        onDismissRequest = { comboExpanded = false }
                                                    ) {
                                                        products.forEach { p ->
                                                            DropdownMenuItem(
                                                                text = { Text(p.name, fontWeight = FontWeight.Bold) },
                                                                onClick = {
                                                                    viewModel.editProduct.value = p.name
                                                                    comboExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }

                                                // Number Steppers edit values form
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Ngon (kg)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            MiniStepper(
                                                                value = editGoodQty,
                                                                onValueChange = { viewModel.editGoodQty.value = it },
                                                                step = 1.0,
                                                                suffix = "kg"
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Đơn giá (đ)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            MiniStepper(
                                                                value = editGoodPrice,
                                                                onValueChange = { viewModel.editGoodPrice.value = it },
                                                                step = 500.0,
                                                                suffix = "đ"
                                                            )
                                                        }
                                                    }

                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Dạt (kg)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            MiniStepper(
                                                                value = editBadQty,
                                                                onValueChange = { viewModel.editBadQty.value = it },
                                                                step = 1.0,
                                                                suffix = "kg"
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Đơn giá (đ)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                                            MiniStepper(
                                                                value = editBadPrice,
                                                                onValueChange = { viewModel.editBadPrice.value = it },
                                                                step = 500.0,
                                                                suffix = "đ"
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = rec.product,
                                                        style = TextStyle(
                                                            fontWeight = FontWeight.Black,
                                                            fontSize = 16.sp,
                                                            color = colorAccent
                                                        )
                                                    )
                                                    Text(
                                                        text = "= " + formatVnd(calcRevenue(rec)),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 14.sp,
                                                        color = Color.Black
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (rec.goodQty > 0 || rec.goodPrice > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color(0xFFE8F5E9))
                                                                .padding(6.dp)
                                                        ) {
                                                            Column {
                                                                Text("Hàng ngon", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                                                Text(
                                                                    text = "${rec.goodQty} kg / ${formatVnd(rec.goodPrice)}",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    color = Color(0xFF2E7D32)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (rec.badQty > 0 || rec.badPrice > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color(0xFFFFF3E0))
                                                                .padding(6.dp)
                                                        ) {
                                                            Column {
                                                                Text("Hàng dạt", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                                                Text(
                                                                    text = "${rec.badQty} kg / ${formatVnd(rec.badPrice)}",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Black,
                                                                    color = Color(0xFFFF9800)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Controls buttons
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            if (isEditing) {
                                                IconButton(
                                                    onClick = { viewModel.saveEditing(rec.id, rec.dateTime) },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFDCEDC8))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Lưu sửa",
                                                        tint = Color(0xFF33691E),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.cancelEditing() },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFF5F5F5))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Hủy sửa",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { viewModel.startEditing(rec) },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFF5F5F5))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Sửa",
                                                        tint = Color.Gray,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { viewModel.requestDeleteRecord(rec) },
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0xFFFFEBEE))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Xóa",
                                                        tint = Color(0xFFC62828),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom data visualizer showing total harvest stats summarized beautifully
@Composable
fun SummaryTab(records: List<HarvestRecord>) {
    val totalRevenueAll = remember(records) { records.sumOf { calcRevenue(it) } }

    val summarizedList = remember(records) {
        val groups = records.groupBy { it.product }
        groups.map { (prodName, recList) ->
            val totalGoodQty = recList.sumOf { it.goodQty }
            val totalBadQty = recList.sumOf { it.badQty }
            val totalRev = recList.sumOf { calcRevenue(it) }
            prodName to Triple(totalGoodQty, totalBadQty, totalRev)
        }.sortedByDescending { it.second.third } // sort by highest revenue dec
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = "Tổng kết vụ mùa",
            subtitle = "Thống kê tổng doanh thu và sản lượng các nông sản",
            icon = {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFF1B5E20)
                )
            }
        )

        // Great overall card displays the total accumulated revenue sum
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        text = "TỔNG DOANH THU HOẠT ĐỘNG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC8E6C9),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatVnd(totalRevenueAll),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }

        if (summarizedList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDFE7D7))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Chưa có dữ liệu thống kê.",
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                summarizedList.forEach { (prodName, stats) ->
                    val goodQ = stats.first
                    val badQ = stats.second
                    val rev = stats.third
                    val itemAccent = getProductColorAccent(prodName)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8DC))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRect(
                                        color = itemAccent,
                                        size = androidx.compose.ui.geometry.Size(
                                            8f,
                                            size.height
                                        )
                                    )
                                }
                                .padding(start = 12.dp)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = prodName,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = itemAccent
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFE8F5E9))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Hàng ngon", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                        Text(
                                            text = "$goodQ kg",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFFE0B2))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("Hàng dạt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                        Text(
                                            text = "$badQ kg",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFEF6C00)
                                        )
                                    }
                                }
                            }

                            // Individual row accumulated revenue band
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(itemAccent.copy(alpha = 0.85f), itemAccent)
                                        )
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TỔNG THU NHẬP",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                    Text(
                                        text = formatVnd(rev),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// FlowRow implementation for older Compose support helper
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val layoutWidth = constraints.maxWidth
        
        var currentX = 0
        var currentY = 0
        var maxRowHeight = 0
        
        val itemPositions = mutableListOf<Pair<androidx.compose.ui.layout.Placeable, androidx.compose.ui.unit.IntOffset>>()
        
        placeables.forEach { placeable ->
            if (currentX + placeable.width > layoutWidth && currentX > 0) {
                currentX = 0
                currentY += maxRowHeight + verticalArrangement.spacing.roundToPx()
                maxRowHeight = 0
            }
            
            itemPositions.add(placeable to androidx.compose.ui.unit.IntOffset(currentX, currentY))
            
            currentX += placeable.width + horizontalArrangement.spacing.roundToPx()
            maxRowHeight = maxOf(maxRowHeight, placeable.height)
        }
        
        val totalHeight = currentY + maxRowHeight
        
        layout(layoutWidth, totalHeight.coerceAtLeast(0)) {
            itemPositions.forEach { (placeable, offset) ->
                placeable.placeRelative(offset.x, offset.y)
            }
        }
    }
}

// Support older compiler remember mutableStateOf helper
@Composable
fun <T> rememberStateOf(initial: T): MutableState<T> {
    return remember { mutableStateOf(initial) }
}
