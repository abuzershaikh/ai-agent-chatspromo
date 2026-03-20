package com.message.bulksend.autorespond.ui.catalogue

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import com.message.bulksend.autorespond.database.*
import com.message.bulksend.product.CatalogueRepository
import com.message.bulksend.product.ProductRepository
import kotlinx.coroutines.launch

private data class AddOptionDialogState(
    val groupId: Long,
    val groupName: String,
    val groupType: String
)

private data class AttributeOptionDraft(
    val value: String,
    val hexColor: String = ""
)

// ==================== CATALOGUE LIST SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueListScreen(
    catalogueRepo: CatalogueRepository,
    onBack: () -> Unit,
    onCatalogueClick: (Long) -> Unit,
    onAddCatalogue: () -> Unit,
    onStandaloneProducts: () -> Unit
) {
    val catalogues by catalogueRepo.getAllCatalogues().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "My Catalogues",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${catalogues.size} catalogue${if (catalogues.size != 1) "s" else ""}",
                            fontSize = 14.sp,
                            color = Color.White.copy(0.8f)
                        )
                    }
                    IconButton(onClick = onStandaloneProducts) {
                        Icon(Icons.Default.ViewList, "Standalone", tint = Color.White.copy(0.8f))
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddCatalogue,
                containerColor = Color(0xFF8B5CF6),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(Modifier.width(8.dp))
                Text("New Catalogue")
            }
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        if (catalogues.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CollectionsBookmark, null,
                        Modifier.size(80.dp), Color.White.copy(0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text("No Catalogues Yet", color = Color.White.copy(0.5f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap + to create your first catalogue", color = Color.White.copy(0.3f), fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Group multiple products together", color = Color.White.copy(0.25f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(catalogues, key = { it.id }) { catalogue ->
                    CatalogueCard(
                        catalogue = catalogue,
                        catalogueRepo = catalogueRepo,
                        onClick = { onCatalogueClick(catalogue.id) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun CatalogueCard(
    catalogue: Catalogue,
    catalogueRepo: CatalogueRepository,
    onClick: () -> Unit
) {
    var productCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(catalogue.id) {
        productCount = catalogueRepo.getProductCount(catalogue.id)
    }

    val themeColor = try {
        Color(android.graphics.Color.parseColor(catalogue.themeColor))
    } catch (e: Exception) { Color(0xFF8B5CF6) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Color dot
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(themeColor.copy(0.2f), RoundedCornerShape(14.dp))
                    .border(2.dp, themeColor.copy(0.5f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CollectionsBookmark, null,
                    Modifier.size(28.dp), themeColor)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(catalogue.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                if (catalogue.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(catalogue.description, fontSize = 13.sp,
                        color = Color.White.copy(0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = themeColor.copy(0.15f)) {
                    Text(
                        "$productCount product${if (productCount != 1) "s" else ""}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = themeColor, fontSize = 12.sp
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(0.3f))
        }
    }
}

// ==================== CATALOGUE DETAIL SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueDetailScreen(
    catalogueRepo: CatalogueRepository,
    productRepo: ProductRepository,
    cataloguePrefs: com.message.bulksend.product.CataloguePrefs,
    itemLabel: String,
    catalogueId: Long,
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onProductClick: (Long) -> Unit,
    onEditCatalogue: () -> Unit
) {
    val products by catalogueRepo.getProductsForCatalogue(catalogueId).collectAsState(initial = emptyList())
    var catalogue by remember { mutableStateOf<Catalogue?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(catalogueId) {
        catalogue = catalogueRepo.getCatalogueById(catalogueId)
    }

    LaunchedEffect(products.map { it.id to it.sortOrder }) {
        val requiresNormalization = products.withIndex().any { (index, product) -> product.sortOrder != index }
        if (requiresNormalization) {
            products.forEachIndexed { index, product ->
                if (product.sortOrder != index) {
                    productRepo.updateProduct(product.copy(sortOrder = index))
                }
            }
        }
    }

    val themeColor = try {
        Color(android.graphics.Color.parseColor(catalogue?.themeColor ?: "#8B5CF6"))
    } catch (e: Exception) { Color(0xFF8B5CF6) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(themeColor.copy(0.8f), Color(0xFF764ba2))))
                    .padding(top = 40.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            catalogue?.name ?: "Catalogue",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("${products.size} ${itemLabel}${if (products.size != 1) "s" else ""}",
                            fontSize = 13.sp, color = Color.White.copy(0.8f))
                    }
                    IconButton(onClick = onEditCatalogue) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.White.copy(0.8f))
                    }
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        isSharing = true
                                        val shareText = catalogueRepo.getCatalogueAsText(catalogueId)
                                        val textToShare =
                                            shareText.ifBlank {
                                                "Catalogue ${catalogue?.name ?: ""} currently has no products."
                                            }
                                        val shareIntent =
                                            Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(
                                                    Intent.EXTRA_SUBJECT,
                                                    "${catalogue?.name ?: "Catalogue"} export"
                                                )
                                                putExtra(Intent.EXTRA_TEXT, textToShare)
                                            }
                                        context.startActivity(
                                            Intent.createChooser(shareIntent, "Share Catalogue")
                                        )
                                    } finally {
                                        isSharing = false
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, "Share", tint = Color.White.copy(0.8f))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                containerColor = themeColor,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "Add")
                Spacer(Modifier.width(8.dp))
                Text("Add $itemLabel")
            }
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        if (products.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory, null, Modifier.size(72.dp), Color.White.copy(0.2f))
                    Spacer(Modifier.height(16.dp))
                    Text("No $itemLabel's Yet", color = Color.White.copy(0.5f), fontSize = 18.sp)
                    Text("Tap + to add your first ${itemLabel.lowercase()}", color = Color.White.copy(0.3f), fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(products, key = { _, product -> product.id }) { index, product ->
                    ProductCard(product = product, onClick = { onProductClick(product.id) })
                    CatalogueProductActions(
                        product = product,
                        canMoveUp = index > 0,
                        canMoveDown = index < products.lastIndex,
                        onMoveUp = {
                            if (index <= 0) return@CatalogueProductActions
                            scope.launch {
                                val previous = products[index - 1]
                                val currentOrder = product.sortOrder
                                val previousOrder = previous.sortOrder
                                productRepo.updateProduct(previous.copy(sortOrder = currentOrder))
                                productRepo.updateProduct(product.copy(sortOrder = previousOrder))
                            }
                        },
                        onMoveDown = {
                            if (index >= products.lastIndex) return@CatalogueProductActions
                            scope.launch {
                                val next = products[index + 1]
                                val currentOrder = product.sortOrder
                                val nextOrder = next.sortOrder
                                productRepo.updateProduct(next.copy(sortOrder = currentOrder))
                                productRepo.updateProduct(product.copy(sortOrder = nextOrder))
                            }
                        },
                        onToggleVisibility = {
                            scope.launch {
                                productRepo.updateProduct(product.copy(isVisible = !product.isVisible))
                            }
                        },
                        onShare = {
                            val shareIntent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, product.name)
                                    putExtra(Intent.EXTRA_TEXT, productRepo.getProductAsText(product))
                                }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share ${itemLabel.lowercase()}")
                            )
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun CatalogueProductActions(
    product: Product,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleVisibility: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (product.isVisible) "Visible" else "Archived",
                fontSize = 12.sp,
                color = if (product.isVisible) Color(0xFF10B981) else Color(0xFFF59E0B)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, "Move Up", tint = Color.White.copy(0.75f))
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Move Down", tint = Color.White.copy(0.75f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleVisibility, modifier = Modifier.size(30.dp)) {
                Icon(
                    if (product.isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    "Toggle Visibility",
                    tint = Color.White.copy(0.75f)
                )
            }
            IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Share, "Share", tint = Color.White.copy(0.75f))
            }
        }
    }
}

// ==================== ADD/EDIT CATALOGUE SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCatalogueScreen(
    catalogueRepo: CatalogueRepository,
    catalogueId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var themeColor by remember { mutableStateOf("#8B5CF6") }

    val colorOptions = listOf(
        "#8B5CF6" to "Purple",
        "#EC4899" to "Pink",
        "#3B82F6" to "Blue",
        "#10B981" to "Green",
        "#F59E0B" to "Amber",
        "#EF4444" to "Red",
        "#6366F1" to "Indigo",
        "#14B8A6" to "Teal"
    )

    LaunchedEffect(catalogueId) {
        if (catalogueId != null) {
            val cat = catalogueRepo.getCatalogueById(catalogueId)
            if (cat != null) {
                name = cat.name
                description = cat.description
                themeColor = cat.themeColor
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (catalogueId == null) "New Catalogue" else "Edit Catalogue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                if (catalogueId == null) {
                                    catalogueRepo.createCatalogue(name.ifBlank { "My Catalogue" }, description, themeColor)
                                } else {
                                    val existing = catalogueRepo.getCatalogueById(catalogueId)
                                    if (existing != null) {
                                        catalogueRepo.updateCatalogue(
                                            existing.copy(name = name.ifBlank { "My Catalogue" },
                                                description = description, themeColor = themeColor)
                                        )
                                    }
                                }
                                onSaved()
                            }
                        }
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color(0xFF8B5CF6)
                )
            )
        },
        containerColor = Color(0xFF0F0F23)
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Catalogue Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }
            item {
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }
            item {
                Text("Theme Color", color = Color.White.copy(0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(colorOptions.size) { i ->
                        val (hex, label) = colorOptions[i]
                        val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color(0xFF8B5CF6) }
                        val selected = themeColor == hex
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                onClick = { themeColor = hex },
                                shape = RoundedCornerShape(14.dp),
                                color = c.copy(0.3f),
                                modifier = Modifier.size(52.dp).then(
                                    if (selected) Modifier.border(2.dp, c, RoundedCornerShape(14.dp)) else Modifier
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (selected) Icon(Icons.Default.Check, null, tint = c)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, fontSize = 11.sp, color = Color.White.copy(0.6f))
                        }
                    }
                }
            }
            item {
                // Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1A1A2E)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val c = try { Color(android.graphics.Color.parseColor(themeColor)) } catch (e: Exception) { Color(0xFF8B5CF6) }
                        Box(
                            Modifier.size(48.dp).background(c.copy(0.2f), RoundedCornerShape(12.dp))
                                .border(1.5.dp, c.copy(0.5f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.CollectionsBookmark, null, Modifier.size(26.dp), c) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name.ifBlank { "Catalogue Name" }, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("0 products", color = c, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==================== MANAGE VARIANTS SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageVariantsScreen(
    catalogueRepo: CatalogueRepository,
    productRepo: ProductRepository,
    productId: Long,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var productName by remember { mutableStateOf("") }
    var basePrice by remember { mutableStateOf("") }
    var bulkPrice by remember { mutableStateOf("") }
    var bulkStock by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(listOf<AttributeGroup>()) }
    var groupOptions by remember { mutableStateOf(mapOf<Long, List<AttributeOption>>()) }
    var variants by remember { mutableStateOf(listOf<ProductVariant>()) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var showAddOptionDialog by remember { mutableStateOf<AddOptionDialogState?>(null) }
    var matrixEditVariant by remember { mutableStateOf<ProductVariant?>(null) }

    // Load data
    suspend fun reload() {
        val product = productRepo.getProductById(productId)
        productName = product?.name ?: ""
        basePrice = if ((product?.price ?: 0.0) > 0) product!!.price.toString() else ""
        groups = catalogueRepo.getAttributeGroups(productId)
        val opts = mutableMapOf<Long, List<AttributeOption>>()
        groups.forEach { g -> opts[g.id] = catalogueRepo.getOptions(g.id) }
        groupOptions = opts
        variants = catalogueRepo.getVariants(productId)
    }

    suspend fun applyQuickPreset(groupName: String, type: String, options: List<AttributeOptionDraft>) {
        val currentGroups = catalogueRepo.getAttributeGroups(productId)
        val existingGroup = currentGroups.find { it.name.equals(groupName, ignoreCase = true) }
        val groupId = existingGroup?.id ?: catalogueRepo.addAttributeGroup(productId, groupName, type)

        val existingOptions = catalogueRepo.getOptions(groupId)
        options.forEach { opt ->
            val alreadyExists =
                existingOptions.any { it.value.equals(opt.value, ignoreCase = true) }
            if (!alreadyExists) {
                catalogueRepo.addOption(groupId, opt.value, opt.hexColor)
            }
        }
        reload()
    }

    fun parseOptionIdSet(json: String): Set<Long> {
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    LaunchedEffect(productId) { reload() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Manage Variants", color = Color.White)
                        Text(productName, fontSize = 13.sp, color = Color.White.copy(0.6f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Base price for auto-generate
            item {
                SectionHeader("Base Price for New Variants", Icons.Default.AttachMoney)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = basePrice, onValueChange = { basePrice = it },
                    label = { Text("Default Price (₹)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    colors = darkFieldColors()
                )
            }

            // Attribute groups
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Attribute Groups", Icons.Default.Tune)
                    TextButton(onClick = { showAddGroupDialog = true }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Group")
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Quick Setup",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            "One tap me common Size/Color options add ho jayenge.",
                            color = Color.White.copy(0.6f),
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        applyQuickPreset(
                                            groupName = "Size",
                                            type = "SELECT",
                                            options =
                                                listOf(
                                                    AttributeOptionDraft("XS"),
                                                    AttributeOptionDraft("S"),
                                                    AttributeOptionDraft("M"),
                                                    AttributeOptionDraft("L"),
                                                    AttributeOptionDraft("XL")
                                                )
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Straighten, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Size Preset", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        applyQuickPreset(
                                            groupName = "Color",
                                            type = "COLOR",
                                            options =
                                                listOf(
                                                    AttributeOptionDraft("Black", "#111827"),
                                                    AttributeOptionDraft("White", "#F9FAFB"),
                                                    AttributeOptionDraft("Red", "#EF4444"),
                                                    AttributeOptionDraft("Blue", "#3B82F6"),
                                                    AttributeOptionDraft("Green", "#10B981")
                                                )
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Palette, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Color Preset", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            items(groups) { group ->
                val options = groupOptions[group.id] ?: emptyList()
                AttributeGroupCard(
                    group = group,
                    options = options,
                    onDeleteGroup = {
                        scope.launch {
                            catalogueRepo.deleteAttributeGroup(group.id)
                            reload()
                        }
                    },
                    onAddOption = {
                        showAddOptionDialog =
                            AddOptionDialogState(
                                groupId = group.id,
                                groupName = group.name,
                                groupType = group.type
                            )
                    },
                    onDeleteOption = { optId ->
                        scope.launch {
                            catalogueRepo.deleteOption(optId)
                            reload()
                        }
                    }
                )
            }

            // Auto-generate button
            if (groups.isNotEmpty()) {
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                catalogueRepo.generateVariants(productId, basePrice.toDoubleOrNull() ?: 0.0)
                                reload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Default.AutoAwesome, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Auto-Generate All Variants", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (variants.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Bulk Update Variants",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = bulkPrice,
                                    onValueChange = { bulkPrice = it },
                                    label = { Text("Price (optional)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = darkFieldColors(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = bulkStock,
                                    onValueChange = { bulkStock = it },
                                    label = { Text("Stock (optional)") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = darkFieldColors(),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        val parsedPrice = bulkPrice.toDoubleOrNull()
                                        val parsedStock = bulkStock.toIntOrNull()
                                        variants.forEach { variant ->
                                            catalogueRepo.updateVariant(
                                                variant.copy(
                                                    price = parsedPrice ?: variant.price,
                                                    stock =
                                                        if (bulkStock.isBlank()) variant.stock
                                                        else parsedStock ?: variant.stock
                                                )
                                            )
                                        }
                                        reload()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                            ) {
                                Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Apply To All Variants")
                            }
                        }
                    }
                }
            }

            val primaryGroup =
                groups.find { it.name.equals("Size", ignoreCase = true) } ?: groups.getOrNull(0)
            val secondaryGroup =
                groups.find {
                    it.name.equals("Color", ignoreCase = true) &&
                        it.id != primaryGroup?.id
                } ?: groups.firstOrNull { it.id != primaryGroup?.id }
            val primaryOptions = primaryGroup?.let { groupOptions[it.id] } ?: emptyList()
            val secondaryOptions = secondaryGroup?.let { groupOptions[it.id] } ?: emptyList()

            if (primaryGroup != null &&
                secondaryGroup != null &&
                primaryOptions.isNotEmpty() &&
                secondaryOptions.isNotEmpty() &&
                variants.isNotEmpty()
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Variant Matrix (${primaryGroup.name} x ${secondaryGroup.name})",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Tap any filled cell to edit that variant.",
                                color = Color.White.copy(0.6f),
                                fontSize = 12.sp
                            )
                            val variantMap = variants.associateBy { parseOptionIdSet(it.optionIds) }
                            val scrollState = rememberScrollState()
                            Row(modifier = Modifier.horizontalScroll(scrollState)) {
                                Column {
                                    Row {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .width(120.dp)
                                                    .padding(6.dp)
                                        ) {
                                            Text(
                                                primaryGroup.name,
                                                color = Color.White.copy(0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        secondaryOptions.forEach { secondaryOpt ->
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .width(110.dp)
                                                        .padding(6.dp)
                                            ) {
                                                Text(
                                                    secondaryOpt.value,
                                                    color = Color.White.copy(0.75f),
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    primaryOptions.forEach { primaryOpt ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .width(120.dp)
                                                        .padding(6.dp)
                                            ) {
                                                Text(
                                                    primaryOpt.value,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            secondaryOptions.forEach { secondaryOpt ->
                                                val key = setOf(primaryOpt.id, secondaryOpt.id)
                                                val match = variantMap[key]
                                                val cellText =
                                                    if (match == null) "--"
                                                    else {
                                                        val priceText =
                                                            if (match.price > 0) "INR ${match.price}" else "INR 0"
                                                        val stockText =
                                                            if (match.stock >= 0) " | S:${match.stock}" else " | INF"
                                                        "$priceText$stockText"
                                                    }
                                                if (match != null) {
                                                    Surface(
                                                        onClick = { matrixEditVariant = match },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF252547),
                                                        modifier =
                                                            Modifier
                                                                .width(110.dp)
                                                                .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = cellText,
                                                            modifier = Modifier.padding(8.dp),
                                                            color = Color(0xFF93C5FD),
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                } else {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF252547),
                                                        modifier =
                                                            Modifier
                                                                .width(110.dp)
                                                                .padding(4.dp)
                                                    ) {
                                                        Text(
                                                            text = cellText,
                                                            modifier = Modifier.padding(8.dp),
                                                            color = Color.White.copy(0.6f),
                                                            fontSize = 11.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
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

            // Variants table
            if (variants.isNotEmpty()) {
                item {
                    HorizontalDivider(color = Color(0xFF2A2A4A))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Variants (${variants.size})", Icons.Default.GridView)
                    }
                }

                items(variants) { variant ->
                    VariantRow(
                        variant = variant,
                        allOptions = groupOptions.values.flatten(),
                        onUpdate = { updated ->
                            scope.launch {
                                catalogueRepo.updateVariant(updated)
                                reload()
                            }
                        },
                        onDelete = {
                            scope.launch {
                                catalogueRepo.deleteVariant(variant.id)
                                reload()
                            }
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // Add Group Dialog
    if (showAddGroupDialog) {
        AddGroupDialog(
            onDismiss = { showAddGroupDialog = false },
            onAdd = { groupName, type ->
                scope.launch {
                    catalogueRepo.addAttributeGroup(productId, groupName, type)
                    showAddGroupDialog = false
                    reload()
                }
            }
        )
    }

    // Add Option Dialog
    showAddOptionDialog?.let { groupId ->
        AddOptionDialog(
            groupName = groupId.groupName,
            groupType = groupId.groupType,
            onDismiss = { showAddOptionDialog = null },
            onAdd = { options ->
                scope.launch {
                    options.forEach { option ->
                        catalogueRepo.addOption(
                            groupId = groupId.groupId,
                            value = option.value,
                            hexColor = option.hexColor
                        )
                    }
                    showAddOptionDialog = null
                    reload()
                }
            }
        )
    }

    matrixEditVariant?.let { editing ->
        MatrixVariantEditDialog(
            variant = editing,
            onDismiss = { matrixEditVariant = null },
            onSave = { updated ->
                scope.launch {
                    catalogueRepo.updateVariant(updated)
                    matrixEditVariant = null
                    reload()
                }
            }
        )
    }
}

@Composable
fun AttributeGroupCard(
    group: AttributeGroup,
    options: List<AttributeOption>,
    onDeleteGroup: () -> Unit,
    onAddOption: () -> Unit,
    onDeleteOption: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, Modifier.size(20.dp), Color(0xFF8B5CF6))
                    Spacer(Modifier.width(8.dp))
                    Text(group.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF252547)) {
                        Text(group.type, fontSize = 10.sp, color = Color.White.copy(0.5f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Row {
                    IconButton(onClick = onAddOption, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Add, "Add Option", Modifier.size(18.dp), Color(0xFF10B981))
                    }
                    IconButton(onClick = onDeleteGroup, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete Group", Modifier.size(18.dp), Color.White.copy(0.4f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (options.isEmpty()) {
                Text("No options yet. Tap + to add", color = Color.White.copy(0.3f), fontSize = 13.sp)
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { opt ->
                        val optionColor =
                            if (group.type.equals("COLOR", ignoreCase = true) &&
                                opt.hexColor.isNotBlank()
                            ) {
                                try {
                                    Color(android.graphics.Color.parseColor(opt.hexColor))
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }

                        InputChip(
                            selected = false,
                            onClick = { },
                            leadingIcon =
                                optionColor?.let {
                                    {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = it,
                                            border = BorderStroke(1.dp, Color.White.copy(0.35f)),
                                            modifier = Modifier.size(12.dp)
                                        ) {}
                                    }
                                },
                            label = { Text(opt.value, color = Color.White) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteOption(opt.id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        modifier = Modifier.size(12.dp),
                                        tint = Color.White.copy(0.5f)
                                    )
                                }
                            },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = Color(0xFF252547)
                            ),
                            modifier = Modifier.then(Modifier)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VariantRow(
    variant: ProductVariant,
    allOptions: List<AttributeOption>,
    onUpdate: (ProductVariant) -> Unit,
    onDelete: () -> Unit
) {
    val optionIds = try {
        val arr = org.json.JSONArray(variant.optionIds)
        (0 until arr.length()).map { arr.getLong(it) }
    } catch (e: Exception) { emptyList() }

    val matchedOptions = allOptions.filter { it.id in optionIds }
    val comboLabel = matchedOptions.joinToString(" + ") { it.value }.ifBlank { "Variant #${variant.id}" }

    var price by remember(variant.price) { mutableStateOf(if (variant.price > 0) variant.price.toString() else "") }
    var stock by remember(variant.stock) { mutableStateOf(if (variant.stock >= 0) variant.stock.toString() else "") }
    var sku by remember(variant.sku) { mutableStateOf(variant.sku) }
    var available by remember(variant.isAvailable) { mutableStateOf(variant.isAvailable) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (available) Color(0xFF1A1A2E) else Color(0xFF1A1A2E).copy(0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(comboLabel, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (available) "Active" else "Off", fontSize = 12.sp,
                        color = if (available) Color(0xFF10B981) else Color.White.copy(0.4f))
                    Switch(
                        checked = available,
                        onCheckedChange = {
                            available = it
                            onUpdate(variant.copy(isAvailable = it,
                                price = price.toDoubleOrNull() ?: variant.price,
                                stock = stock.toIntOrNull() ?: variant.stock,
                                sku = sku))
                        },
                        modifier = Modifier.scale(0.8f),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(0.3f))
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, "Delete", Modifier.size(16.dp), Color.White.copy(0.3f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = price, onValueChange = { price = it },
                    label = { Text("Price (₹)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
                OutlinedTextField(
                    value = stock, onValueChange = { stock = it },
                    label = { Text("Stock") },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("∞") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
                OutlinedTextField(
                    value = sku, onValueChange = { sku = it },
                    label = { Text("SKU") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = darkFieldColors(),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = {
                    onUpdate(variant.copy(
                        price = price.toDoubleOrNull() ?: variant.price,
                        stock = stock.toIntOrNull() ?: -1,
                        sku = sku,
                        isAvailable = available
                    ))
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.Save, null, Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save Variant", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MatrixVariantEditDialog(
    variant: ProductVariant,
    onDismiss: () -> Unit,
    onSave: (ProductVariant) -> Unit
) {
    var price by remember(variant.id, variant.price) {
        mutableStateOf(if (variant.price > 0) variant.price.toString() else "")
    }
    var stock by remember(variant.id, variant.stock) {
        mutableStateOf(if (variant.stock >= 0) variant.stock.toString() else "")
    }
    var sku by remember(variant.id, variant.sku) { mutableStateOf(variant.sku) }
    var available by remember(variant.id, variant.isAvailable) { mutableStateOf(variant.isAvailable) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Edit Matrix Variant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price (INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = darkFieldColors()
                )
                OutlinedTextField(
                    value = stock,
                    onValueChange = { stock = it },
                    label = { Text("Stock (blank = unlimited)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = darkFieldColors()
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it },
                    label = { Text("SKU") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = darkFieldColors()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Available", color = Color.White.copy(0.8f), fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = available,
                        onCheckedChange = { available = it },
                        modifier = Modifier.scale(0.85f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981),
                            checkedTrackColor = Color(0xFF10B981).copy(0.3f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        variant.copy(
                            price = price.toDoubleOrNull() ?: variant.price,
                            stock = if (stock.isBlank()) -1 else stock.toIntOrNull() ?: variant.stock,
                            sku = sku.trim(),
                            isAvailable = available
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== DIALOGS ====================

@Composable
fun AddGroupDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var groupName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("SELECT") }
    val types = listOf("SELECT" to "Select (Dropdown)", "COLOR" to "Color Swatch", "NUMBER" to "Number")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Attribute Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = groupName, onValueChange = { groupName = it },
                    label = { Text("Group Name (e.g. Size, Color, Weight)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = darkFieldColors(),
                    singleLine = true
                )
                Text("Type:", fontWeight = FontWeight.Medium, color = Color.White.copy(0.8f))
                types.forEach { (type, label) ->
                    Surface(
                        onClick = { selectedType = type },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedType == type) Color(0xFF8B5CF6).copy(0.2f) else Color(0xFF252547)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF8B5CF6)))
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (groupName.isNotBlank()) onAdd(groupName.trim(), selectedType) },
                enabled = groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddOptionDialog(
    groupName: String,
    groupType: String = "SELECT",
    onDismiss: () -> Unit,
    onAdd: (List<AttributeOptionDraft>) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val isColorGroup = groupType.equals("COLOR", ignoreCase = true)
    val quickColors =
        listOf(
            "Black" to "#111827",
            "White" to "#F9FAFB",
            "Red" to "#EF4444",
            "Blue" to "#3B82F6",
            "Green" to "#10B981",
            "Yellow" to "#F59E0B",
            "Pink" to "#EC4899"
        )

    fun appendToken(token: String) {
        input =
            if (input.isBlank()) token
            else "$input, $token"
    }

    fun parseOptionDrafts(raw: String): List<AttributeOptionDraft> {
        val tokens = raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        return tokens.mapNotNull { token ->
            if (!isColorGroup) {
                return@mapNotNull AttributeOptionDraft(value = token)
            }

            val splitByColon = token.split(":", limit = 2)
            val splitByHash = token.split("#", limit = 2)

            val (name, rawHex) =
                when {
                    splitByColon.size == 2 -> splitByColon[0].trim() to splitByColon[1].trim()
                    splitByHash.size == 2 ->
                        splitByHash[0].trim() to "#${splitByHash[1].trim().trimStart('#')}"
                    else -> token to ""
                }

            if (name.isBlank()) return@mapNotNull null

            val normalizedHex =
                if (rawHex.isBlank()) ""
                else {
                    val h = rawHex.trim().trimStart('#')
                    if (h.length == 6 && h.all { it.isLetterOrDigit() }) "#$h" else ""
                }
            AttributeOptionDraft(value = name, hexColor = normalizedHex)
        }.distinctBy { "${it.value.lowercase()}|${it.hexColor.lowercase()}" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text("Add Options to $groupName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (isColorGroup) {
                        "Use quick colors or enter values as Name:#HEX (comma separated)."
                    } else {
                        "Enter multiple values separated by comma:"
                    },
                    color = Color.White.copy(0.7f),
                    fontSize = 13.sp
                )

                if (isColorGroup) {
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickColors.forEach { (name, hex) ->
                            FilterChip(
                                selected = false,
                                onClick = { appendToken("$name:$hex") },
                                label = { Text(name, fontSize = 11.sp) },
                                leadingIcon = {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = try {
                                            Color(android.graphics.Color.parseColor(hex))
                                        } catch (e: Exception) {
                                            Color.Gray
                                        },
                                        border = BorderStroke(1.dp, Color.White.copy(0.35f)),
                                        modifier = Modifier.size(12.dp)
                                    ) {}
                                },
                                colors =
                                    FilterChipDefaults.filterChipColors(
                                        containerColor = Color(0xFF252547),
                                        labelColor = Color.White
                                    )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    label = {
                        Text(
                            if (isColorGroup) {
                                "e.g. Red:#EF4444, Blue:#3B82F6"
                            } else {
                                "e.g. S, M, L, XL"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = darkFieldColors()
                )
                if (input.isNotBlank()) {
                    val preview = parseOptionDrafts(input)
                    val previewText =
                        preview.joinToString(", ") {
                            if (it.hexColor.isBlank()) it.value else "${it.value} (${it.hexColor})"
                        }
                    Text(
                        "Will add ${preview.size} option(s): $previewText",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val drafts = parseOptionDrafts(input)
                    if (drafts.isNotEmpty()) onAdd(drafts)
                },
                enabled = input.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
