package com.crystalsugar.admin.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalsugar.admin.data.MockAdminData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLicensesScreen(onBack: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("License Management", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = Color(0xFF38BDF8)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
            }
        },
        containerColor = Color(0xFF0F172A)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MockAdminData.licenses) { license ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(license.licenseKey, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Customer: ${license.customerName}", color = Color.LightGray, fontSize = 13.sp)
                        Text("Device: ${license.deviceId}", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expires: ${license.expiryDate}", color = Color.Gray, fontSize = 12.sp)
                            Text(
                                license.status,
                                color = if (license.status == "Active") Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Generate New License Key") },
                text = { Text("Mock Generator: Select duration and client target.") },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Generate & Sign")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
