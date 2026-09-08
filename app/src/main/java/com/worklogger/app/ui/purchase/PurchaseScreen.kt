package com.worklogger.app.ui.purchase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.worklogger.app.model.AdvancePurchaseRecord
import com.worklogger.app.ui.components.ConfirmDialog
import com.worklogger.app.ui.components.EmptyState
import com.worklogger.app.ui.components.StatsCard
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: PurchaseViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "垫资",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${DateUtils.getYear()}.${DateUtils.getMonth()}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = AdvancePurchase,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("垫资")
            }
        }
    ) { padding ->
        PurchaseContent(viewModel = viewModel, padding = padding)
    }
}

/**
 * 垫资页面的内容部分（不含 Scaffold/TopAppBar/FAB），用于嵌入统计 Tab
 */
@Composable
fun PurchaseContent(
    viewModel: PurchaseViewModel,
    padding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }

    if (uiState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        title = "本月垫资",
                        value = currencyFormat.format(uiState.monthlyAmount),
                        subtitle = "${DateUtils.getMonth()}月累计",
                        color = AdvancePurchase,
                        modifier = Modifier.weight(1f)
                    )
                    StatsCard(
                        title = "累计垫资",
                        value = currencyFormat.format(uiState.totalAmount),
                        subtitle = "共${uiState.allRecords.size}笔",
                        color = AdvancePurchase,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Text(
                    text = "垫资记录",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.allRecords.isEmpty()) {
                item {
                    EmptyState(
                        message = "暂无垫资记录，点击下方按钮添加",
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            } else {
                items(
                    items = uiState.allRecords,
                    key = { it.id }
                ) { record ->
                    PurchaseRecordCard(
                        record = record,
                        onClick = { viewModel.showEditDialog(record) },
                        onDelete = { viewModel.showDeleteConfirm(record.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (uiState.showAddDialog) {
        AddPurchaseDialog(
            record = uiState.editingRecord,
            recentLocations = uiState.recentLocations,
            onDismiss = { viewModel.hideAddDialog() },
            onSave = { date, itemName, location, amount, quantity, remark ->
                viewModel.saveRecord(date, itemName, location, amount, quantity, remark)
            }
        )
    }

    if (uiState.showDeleteConfirm) {
        ConfirmDialog(
            title = "确认删除",
            message = "确定要删除这条垫资记录吗？删除后可在回收站中恢复。",
            confirmText = "删除",
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.hideDeleteConfirm() },
            isDangerous = true
        )
    }
}

@Composable
fun PurchaseRecordCard(
    record: AdvancePurchaseRecord,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = AdvancePurchase
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = record.itemName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currencyFormat.format(record.amount),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = AdvancePurchase
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = DateUtils.formatDisplayFullDate(record.date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (record.quantity > 1) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "x${record.quantity}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (record.location.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = record.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (record.remark.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.remark,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
