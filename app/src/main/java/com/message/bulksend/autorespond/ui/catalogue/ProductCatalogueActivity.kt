package com.message.bulksend.autorespond.ui.catalogue

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.message.bulksend.product.CatalogueRepository
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.message.bulksend.autorespond.database.Product
import com.message.bulksend.product.CustomField
import com.message.bulksend.product.CustomFieldType
import com.message.bulksend.product.MediaItem
import com.message.bulksend.product.ProductRepository
import com.message.bulksend.product.CataloguePrefs
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.launch
import java.io.File

class ProductCatalogueActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { ProductRepository(this@ProductCatalogueActivity) }
            val catalogueRepo = remember { CatalogueRepository(this@ProductCatalogueActivity) }
            val cataloguePrefs = remember { com.message.bulksend.product.CataloguePrefs(this@ProductCatalogueActivity) }
            var currentScreen by remember { mutableStateOf<ProductScreen>(ProductScreen.CatalogueList) }
            var itemLabel by remember { mutableStateOf(cataloguePrefs.itemLabel) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8B5CF6),
                    secondary = Color(0xFF6366F1),
                    surface = Color(0xFF1A1A2E),
                    background = Color(0xFF0F0F23),
                    onSurface = Color.White,
                    onBackground = Color.White,
                    surfaceVariant = Color(0xFF252547)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (val screen = currentScreen) {
                        is ProductScreen.CatalogueList -> CatalogueListScreen(
                            catalogueRepo = catalogueRepo,
                            onBack = { finish() },
                            onCatalogueClick = { currentScreen = ProductScreen.CatalogueDetail(it) },
                            onAddCatalogue = { currentScreen = ProductScreen.AddEditCatalogue(null) },
                            onStandaloneProducts = { currentScreen = ProductScreen.List }
                        )
                        is ProductScreen.CatalogueDetail -> CatalogueDetailScreen(
                            catalogueRepo = catalogueRepo,
                            productRepo = repository,
                            cataloguePrefs = cataloguePrefs,
                            itemLabel = itemLabel,
                            catalogueId = screen.catalogueId,
                            onBack = { currentScreen = ProductScreen.CatalogueList },
                            onAddProduct = { currentScreen = ProductScreen.AddEdit(null, screen.catalogueId) },
                            onProductClick = { currentScreen = ProductScreen.Detail(it) },
                            onEditCatalogue = { currentScreen = ProductScreen.AddEditCatalogue(screen.catalogueId) }
                        )
                        is ProductScreen.AddEditCatalogue -> AddEditCatalogueScreen(
                            catalogueRepo = catalogueRepo,
                            catalogueId = screen.catalogueId,
                            onBack = { currentScreen = ProductScreen.CatalogueList },
                            onSaved = { currentScreen = ProductScreen.CatalogueList }
                        )
                        is ProductScreen.List -> ProductListScreen(
                            repository = repository,
                            cataloguePrefs = cataloguePrefs,
                            itemLabel = itemLabel,
                            onItemLabelChanged = { newLabel ->
                                cataloguePrefs.itemLabel = newLabel
                                itemLabel = newLabel
                            },
                            onBack = { currentScreen = ProductScreen.CatalogueList },
                            onAddProduct = { currentScreen = ProductScreen.AddEdit(null, 0L) },
                            onProductClick = { currentScreen = ProductScreen.Detail(it) }
                        )
                        is ProductScreen.Detail -> ProductDetailScreen(
                            repository = repository,
                            productId = screen.productId,
                            itemLabel = itemLabel,
                            onBack = {
                                currentScreen = if (screen.catalogueId > 0)
                                    ProductScreen.CatalogueDetail(screen.catalogueId)
                                else ProductScreen.List
                            },
                            onEdit = { currentScreen = ProductScreen.AddEdit(screen.productId, screen.catalogueId) },
                            onManageVariants = { currentScreen = ProductScreen.ManageVariants(screen.productId) }
                        )
                        is ProductScreen.AddEdit -> AddEditProductScreen(
                            repository = repository,
                            cataloguePrefs = cataloguePrefs,
                            itemLabel = itemLabel,
                            productId = screen.productId,
                            catalogueId = screen.catalogueId,
                            onBack = {
                                currentScreen = if (screen.catalogueId > 0)
                                    ProductScreen.CatalogueDetail(screen.catalogueId)
                                else ProductScreen.List
                            },
                            onSaved = { savedProductId ->
                                currentScreen =
                                    if (savedProductId > 0L) {
                                        ProductScreen.Detail(savedProductId, screen.catalogueId)
                                    } else if (screen.catalogueId > 0) {
                                        ProductScreen.CatalogueDetail(screen.catalogueId)
                                    } else {
                                        ProductScreen.List
                                    }
                            }
                        )
                        is ProductScreen.ManageVariants -> ManageVariantsScreen(
                            catalogueRepo = catalogueRepo,
                            productRepo = repository,
                            productId = screen.productId,
                            onBack = { currentScreen = ProductScreen.Detail(screen.productId) }
                        )
                    }
                }
            }
        }
    }
}

sealed class ProductScreen {
    object CatalogueList : ProductScreen()
    data class CatalogueDetail(val catalogueId: Long) : ProductScreen()
    data class AddEditCatalogue(val catalogueId: Long?) : ProductScreen()
    object List : ProductScreen()
    data class Detail(val productId: Long, val catalogueId: Long = 0L) : ProductScreen()
    data class AddEdit(val productId: Long?, val catalogueId: Long = 0L) : ProductScreen()
    data class ManageVariants(val productId: Long) : ProductScreen()
}

// ==================== PRODUCT LIST SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    repository: ProductRepository,
    cataloguePrefs: com.message.bulksend.product.CataloguePrefs,
    itemLabel: String,
    onItemLabelChanged: (String) -> Unit,
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onProductClick: (Long) -> Unit
) {
    var showLabelDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val products by repository.getAllProducts().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Product>?>(null) }
    
    val displayProducts = searchResults ?: products
    
    Scaffold(
        topBar = {
            Column {
                // Gradient Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            )
                        )
                        .padding(top = 40.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$itemLabel Catalogue",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "${products.size} ${itemLabel}s",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = { showLabelDialog = true }) {
                                Icon(Icons.Default.Settings, "Settings", tint = Color.White.copy(0.7f))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                scope.launch {
                                    searchResults = if (query.isBlank()) null
                                    else repository.searchProducts(query)
                                }
                            },
                            placeholder = { Text("Search ${itemLabel.lowercase()}s...", color = Color.White.copy(0.5f)) },
                            leadingIcon = { Icon(Icons.Default.Search, "Search", tint = Color.White.copy(0.7f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White.copy(0.5f),
                                unfocusedBorderColor = Color.White.copy(0.3f),
                                cursorColor = Color.White,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                containerColor = Color(0xFF8B5CF6),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(Modifier.width(8.dp))
                Text("Add $itemLabel")
            }
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        if (displayProducts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.ShoppingCart, "Empty",
                        modifier = Modifier.size(72.dp),
                        tint = Color.White.copy(0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No ${itemLabel}s Yet", color = Color.White.copy(0.5f), fontSize = 18.sp)
                    Text("Tap + to add your first ${itemLabel.lowercase()}", color = Color.White.copy(0.3f), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(displayProducts, key = { it.id }) { product ->
                    ProductCard(product = product, onClick = { onProductClick(product.id) })
                    
                    // Divider between products
                    if (product != displayProducts.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFF2A2A4A),
                            thickness = 1.dp
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // Space for FAB
            }
        }
    }
    
    // Label Settings Dialog
    if (showLabelDialog) {
        ItemLabelDialog(
            currentLabel = itemLabel,
            onDismiss = { showLabelDialog = false },
            onSave = { newLabel ->
                onItemLabelChanged(newLabel)
                showLabelDialog = false
            }
        )
    }
}

@Composable
fun ItemLabelDialog(currentLabel: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by remember { mutableStateOf(currentLabel) }
    val suggestions = listOf("Product", "Service", "Course", "Item", "Package", "Plan", "Listing")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("What do you sell?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose or type your item type:", color = Color.White.copy(0.7f), fontSize = 13.sp)
                
                // Quick select chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions.size) { i ->
                        val s = suggestions[i]
                        FilterChip(
                            selected = label == s,
                            onClick = { label = s },
                            label = { Text(s) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8B5CF6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF252547),
                                labelColor = Color.White.copy(0.8f)
                            )
                        )
                    }
                }
                
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Custom Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                
                Text(
                    "Preview: \"$label Catalogue\" • \"Add $label\" • \"${label} Name\"",
                    color = Color.White.copy(0.4f), fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (label.isNotBlank()) onSave(label.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                enabled = label.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    val mediaItems = remember(product.mediaPaths) { MediaItem.listFromJson(product.mediaPaths) }
    val imageItem = mediaItems.firstOrNull { !it.isVideo && !it.isPdf && !it.isAudio }
    val customFields = remember(product.customFields) { CustomField.listFromJson(product.customFields) }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF252547)),
                contentAlignment = Alignment.Center
            ) {
                if (imageItem != null || product.thumbnailPath.isNotBlank()) {
                    val path = if (product.thumbnailPath.isNotBlank()) product.thumbnailPath else imageItem!!.path
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(path))
                            .crossfade(true)
                            .build(),
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color(0xFF8B5CF6).copy(0.5f)
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                // Name
                Text(
                    product.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Price Badge
                if (product.price > 0) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981).copy(0.15f)
                    ) {
                        Text(
                            "${product.currency} ${product.price}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = Color(0xFF10B981),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Description preview
                if (product.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        product.description,
                        fontSize = 13.sp,
                        color = Color.White.copy(0.5f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Category chip + Media count
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (product.category.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF8B5CF6).copy(0.15f)
                        ) {
                            Text(
                                product.category,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color(0xFF8B5CF6),
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (mediaItems.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF3B82F6).copy(0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(12.dp), Color(0xFF3B82F6))
                                Text("${mediaItems.size}", color = Color(0xFF3B82F6), fontSize = 11.sp)
                            }
                        }
                    }
                    if (customFields.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFF59E0B).copy(0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.Tune, null, Modifier.size(12.dp), Color(0xFFF59E0B))
                                Text("${customFields.size}", color = Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
            
            // Arrow
            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.align(Alignment.CenterVertically),
                tint = Color.White.copy(0.3f)
            )
        }
    }
}

// ==================== ADD/EDIT PRODUCT SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    repository: ProductRepository,
    cataloguePrefs: com.message.bulksend.product.CataloguePrefs,
    itemLabel: String,
    productId: Long?,
    catalogueId: Long = 0L,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = productId != null
    
    // Form State
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    var category by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf(listOf<MediaItem>()) }
    var customFields by remember { mutableStateOf(listOf<CustomField>()) }
    var originalProduct by remember { mutableStateOf<Product?>(null) }
    var showFieldTypePicker by remember { mutableStateOf(false) }
    var newFieldName by remember { mutableStateOf("") }
    var categories by remember { mutableStateOf(cataloguePrefs.getCategories()) }
    var categoryExpanded by remember { mutableStateOf(false) }
    
    // Load existing product if editing
    LaunchedEffect(productId) {
        if (productId != null) {
            val product = repository.getProductById(productId)
            if (product != null) {
                originalProduct = product
                name = product.name
                description = product.description
                price = if (product.price > 0) product.price.toString() else ""
                currency = product.currency
                category = product.category
                link = product.link
                mediaItems = MediaItem.listFromJson(product.mediaPaths)
                customFields = CustomField.listFromJson(product.customFields)
            }
        }
    }
    
    // Image picker
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = repository.saveMediaToStorage(it, isVideo = false)
            if (path != null) {
                mediaItems = mediaItems + MediaItem(path, false)
            }
        }
    }
    
    // Video picker
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = repository.saveMediaToStorage(it, isVideo = true)
            if (path != null) {
                mediaItems = mediaItems + MediaItem(path, isVideo = true)
            }
        }
    }
    
    // PDF picker
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = repository.saveMediaToStorage(it, isVideo = false, isPdf = true)
            if (path != null) {
                mediaItems = mediaItems + MediaItem(path, isPdf = true)
            }
        }
    }
    
    // Audio picker
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = repository.saveMediaToStorage(it, isVideo = false, isAudio = true)
            if (path != null) {
                mediaItems = mediaItems + MediaItem(path, isAudio = true)
            }
        }
    }
    
    // Custom field image picker
    var pendingFieldIndex by remember { mutableIntStateOf(-1) }
    val fieldImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val path = repository.saveMediaToStorage(it, isVideo = false)
            if (path != null && pendingFieldIndex >= 0 && pendingFieldIndex < customFields.size) {
                customFields = customFields.toMutableList().apply {
                    this[pendingFieldIndex] = this[pendingFieldIndex].copy(value = path)
                }
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit $itemLabel" else "Add $itemLabel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val imageThumbnailPath =
                                    mediaItems.firstOrNull { !it.isVideo && !it.isPdf && !it.isAudio }?.path
                                        ?: ""

                                val productToSave =
                                    (if (isEditing) originalProduct else null)?.copy(
                                        catalogueId = catalogueId,
                                        name = name.ifBlank { "Unnamed Product" },
                                        description = description,
                                        price = price.toDoubleOrNull() ?: 0.0,
                                        currency = currency,
                                        category = category,
                                        link = link,
                                        thumbnailPath = imageThumbnailPath,
                                        mediaPaths = MediaItem.listToJson(mediaItems),
                                        customFields = CustomField.listToJson(customFields)
                                    ) ?: Product(
                                        id = productId ?: 0L,
                                        catalogueId = catalogueId,
                                        name = name.ifBlank { "Unnamed Product" },
                                        description = description,
                                        price = price.toDoubleOrNull() ?: 0.0,
                                        currency = currency,
                                        category = category,
                                        link = link,
                                        thumbnailPath = imageThumbnailPath,
                                        mediaPaths = MediaItem.listToJson(mediaItems),
                                        customFields = CustomField.listToJson(customFields)
                                    )

                                val savedProductId =
                                    if (isEditing) {
                                        repository.updateProduct(productToSave)
                                        productToSave.id
                                    } else {
                                        repository.addProduct(productToSave.copy(id = 0L))
                                    }

                                // Save category if new
                                if (category.isNotBlank() && category !in categories) {
                                    cataloguePrefs.addCategory(category)
                                    categories = cataloguePrefs.getCategories()
                                }
                                onSaved(savedProductId)
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Basic Info Section
            item {
                SectionHeader("Basic Info", Icons.Default.Info)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("$itemLabel Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }
            
            item {
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }
            
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = price, onValueChange = { price = it },
                        label = { Text("Price") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkFieldColors()
                    )
                    OutlinedTextField(
                        value = currency, onValueChange = { currency = it },
                        label = { Text("Currency") },
                        modifier = Modifier.width(100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkFieldColors()
                    )
                }
            }
            
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category, 
                        onValueChange = { 
                            category = it
                            categoryExpanded = true
                        },
                        label = { Text("Category") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = darkFieldColors(),
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = !categoryExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, "Dropdown")
                            }
                        }
                    )
                    
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                        modifier = Modifier.background(Color(0xFF252547))
                    ) {
                        if (categories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No categories yet", color = Color.White.copy(0.5f)) },
                                onClick = { categoryExpanded = false }
                            )
                        } else {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = Color.White) },
                                    onClick = {
                                        category = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                OutlinedTextField(
                    value = link, onValueChange = { link = it },
                    label = { Text("$itemLabel Link") },
                    leadingIcon = { Icon(Icons.Default.Link, null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }
            
            // Media Section
            item {
                HorizontalDivider(color = Color(0xFF2A2A4A))
                Spacer(Modifier.height(8.dp))
                SectionHeader("Media", Icons.Default.PhotoLibrary)
                Spacer(Modifier.height(8.dp))
                
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mediaItems) { item ->
                        MediaThumbnail(
                            item = item,
                            onRemove = { mediaItems = mediaItems - item }
                        )
                    }
                    item {
                        // Add Image
                        AddMediaButton("Image", Icons.Default.Image) {
                            imagePicker.launch("image/*")
                        }
                    }
                    item {
                        // Add Video
                        AddMediaButton("Video", Icons.Default.Videocam) {
                            videoPicker.launch("video/*")
                        }
                    }
                    item {
                        // Add PDF
                        AddMediaButton("PDF", Icons.Default.PictureAsPdf) {
                            pdfPicker.launch("application/pdf")
                        }
                    }
                    item {
                        // Add Audio
                        AddMediaButton("Audio", Icons.Default.AudioFile) {
                            audioPicker.launch("audio/*")
                        }
                    }
                }
            }
            
            // Custom Fields Section
            item {
                HorizontalDivider(color = Color(0xFF2A2A4A))
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Custom Fields", Icons.Default.Tune)
                    TextButton(onClick = { showFieldTypePicker = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }
            }
            
            items(customFields.size) { index ->
                val field = customFields[index]
                CustomFieldEditor(
                    field = field,
                    onValueChange = { newValue ->
                        customFields = customFields.toMutableList().apply {
                            this[index] = this[index].copy(value = newValue)
                        }
                    },
                    onRemove = {
                        customFields = customFields.toMutableList().apply { removeAt(index) }
                    },
                    onPickImage = {
                        pendingFieldIndex = index
                        fieldImagePicker.launch("image/*")
                    }
                )
            }
            
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
    
    // Field Type Picker Bottom Sheet
    if (showFieldTypePicker) {
        AddCustomFieldDialog(
            onDismiss = { showFieldTypePicker = false },
            onAdd = { fieldName, type ->
                customFields = customFields + CustomField(fieldName, type, "")
                showFieldTypePicker = false
            }
        )
    }
}

@Composable
fun AddCustomFieldDialog(onDismiss: () -> Unit, onAdd: (String, CustomFieldType) -> Unit) {
    var fieldName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CustomFieldType.TEXT) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Custom Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text("Field Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
                Text("Select Type:", fontWeight = FontWeight.SemiBold)
                // Type Grid
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CustomFieldType.entries.forEach { type ->
                        Surface(
                            onClick = { selectedType = type },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedType == type) Color(0xFF8B5CF6).copy(0.2f) else Color.Transparent,
                            border = if (selectedType == type) BorderStroke(1.dp, Color(0xFF8B5CF6)) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type.icon, fontSize = 18.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(type.displayName, color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (fieldName.isNotBlank()) onAdd(fieldName, selectedType) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                enabled = fieldName.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CustomFieldEditor(
    field: CustomField,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    onPickImage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(field.type.icon, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(field.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF8B5CF6).copy(0.15f)
                    ) {
                        Text(
                            field.type.displayName,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            fontSize = 10.sp, color = Color(0xFF8B5CF6)
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, "Remove", Modifier.size(16.dp), Color.White.copy(0.5f))
                }
            }
            Spacer(Modifier.height(6.dp))
            
            when (field.type) {
                CustomFieldType.TEXT -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                CustomFieldType.NUMBER -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                CustomFieldType.DATE -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("YYYY-MM-DD") },
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                CustomFieldType.TIME -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("HH:MM") },
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                CustomFieldType.LINK -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://...") },
                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(16.dp)) },
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                CustomFieldType.IMAGE -> {
                    if (field.value.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(field.value))
                                .crossfade(true)
                                .build(),
                            contentDescription = field.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (field.value.isBlank()) "Pick Image" else "Change Image")
                    }
                }
                CustomFieldType.MULTI_TEXT -> OutlinedTextField(
                    value = field.value, onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors()
                )
            }
        }
    }
}

@Composable
fun MediaThumbnail(item: MediaItem, onRemove: () -> Unit) {
    Box(modifier = Modifier.size(90.dp)) {
        when {
            item.isPdf -> {
                // PDF Icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF252547)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PictureAsPdf, "PDF", Modifier.size(32.dp), Color(0xFFEF4444))
                        Spacer(Modifier.height(4.dp))
                        Text("PDF", fontSize = 10.sp, color = Color(0xFFEF4444))
                    }
                }
            }
            item.isAudio -> {
                // Audio Icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF252547)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AudioFile, "Audio", Modifier.size(32.dp), Color(0xFF10B981))
                        Spacer(Modifier.height(4.dp))
                        Text("Audio", fontSize = 10.sp, color = Color(0xFF10B981))
                    }
                }
            }
            item.isVideo -> {
                // Video Icon
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF252547)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, "Video", Modifier.size(32.dp), Color(0xFF3B82F6))
                        Spacer(Modifier.height(4.dp))
                        Text("Video", fontSize = 10.sp, color = Color(0xFF3B82F6))
                    }
                }
            }
            else -> {
                // Image Thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(item.path))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Media",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .background(Color.Black.copy(0.6f), CircleShape)
        ) {
            Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp), Color.White)
        }
    }
}

@Composable
fun AddMediaButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(90.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(0.3f)),
        contentPadding = PaddingValues(4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), Color(0xFF3B82F6))
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = Color(0xFF3B82F6))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), Color(0xFF8B5CF6))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF8B5CF6),
    unfocusedBorderColor = Color(0xFF2A2A4A),
    cursorColor = Color(0xFF8B5CF6),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF8B5CF6),
    unfocusedLabelColor = Color.White.copy(0.5f),
    focusedPlaceholderColor = Color.White.copy(0.3f),
    unfocusedPlaceholderColor = Color.White.copy(0.3f)
)

// ==================== PRODUCT DETAIL SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    repository: ProductRepository,
    productId: Long,
    itemLabel: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onManageVariants: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var product by remember { mutableStateOf<Product?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(productId) {
        product = repository.getProductById(productId)
    }
    
    val p = product ?: return
    val mediaItems = remember(p.mediaPaths) { MediaItem.listFromJson(p.mediaPaths) }
    val customFields = remember(p.customFields) { CustomField.listFromJson(p.customFields) }
    val images = mediaItems.filter { !it.isVideo && !it.isPdf && !it.isAudio }
    val videos = mediaItems.filter { it.isVideo }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$itemLabel Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (onManageVariants != null) {
                        TextButton(onClick = onManageVariants) {
                            Icon(Icons.Default.Tune, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Variants", fontSize = 13.sp)
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Image Carousel
            if (images.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(images) { img ->
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(File(img.path))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = p.name,
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(250.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            
            // Name + Price
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        p.name,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    if (p.price > 0) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(0.15f)
                        ) {
                            Text(
                                "${p.currency} ${p.price}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                color = Color(0xFF10B981),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    if (p.category.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8B5CF6).copy(0.15f)
                        ) {
                            Text(
                                p.category,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = Color(0xFF8B5CF6),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            
            // Description
            if (p.description.isNotBlank()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF2A2A4A))
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Description", Icons.Default.Description)
                        Spacer(Modifier.height(8.dp))
                        Text(p.description, color = Color.White.copy(0.8f), fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
            
            // Link
            if (p.link.isNotBlank()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF2A2A4A))
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Link", Icons.Default.Link)
                        Spacer(Modifier.height(8.dp))
                        Text(p.link, color = Color(0xFF3B82F6), fontSize = 14.sp)
                    }
                }
            }
            
            // Custom Fields
            if (customFields.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF2A2A4A))
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Details", Icons.Default.Tune)
                        Spacer(Modifier.height(12.dp))
                    }
                }
                items(customFields.size) { index ->
                    val field = customFields[index]
                    if (field.value.isNotBlank()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Text(
                                "${field.type.icon} ${field.name}",
                                fontSize = 12.sp,
                                color = Color.White.copy(0.5f)
                            )
                            Spacer(Modifier.height(2.dp))
                            when (field.type) {
                                CustomFieldType.IMAGE -> {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(File(field.value))
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = field.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                CustomFieldType.LINK -> {
                                    Text(field.value, color = Color(0xFF3B82F6), fontSize = 14.sp)
                                }
                                else -> {
                                    Text(field.value, color = Color.White, fontSize = 15.sp)
                                }
                            }
                            if (index < customFields.size - 1) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
            
            // Videos
            if (videos.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF2A2A4A))
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("Videos", Icons.Default.Videocam)
                        Spacer(Modifier.height(8.dp))
                        videos.forEachIndexed { i, video ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF252547))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayCircle, "Play", Modifier.size(40.dp), Color(0xFF3B82F6))
                                    Spacer(Modifier.width(12.dp))
                                    Text("Video ${i + 1}", color = Color.White)
                                }
                            }
                            if (i < videos.size - 1) Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF1A1A2E),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = { Text("Delete $itemLabel?") },
            text = { Text("Are you sure you want to delete \"${p.name}\"? This action cannot be undone.", color = Color.White.copy(0.7f)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteProduct(p)
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}
