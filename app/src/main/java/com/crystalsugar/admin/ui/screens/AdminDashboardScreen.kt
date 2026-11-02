package com.crystalsugar.admin.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crystalsugar.admin.data.MockAdminData

@Composable
fun AdminDashboardScreen(
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Text(
            text = "Master Dashboard",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Crystal Sugar Vision Administration",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Stats grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Active Clients", "${MockAdminData.customers.size}", Color(0xFF10B981), Modifier.weight(1f))
            StatCard("Total Licenses", "${MockAdminData.licenses.size}", Color(0xFF38BDF8), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard("Subscriptions", "${MockAdminData.subscriptions.size}", Color(0xFFF59E0B), Modifier.weight(1f))
            StatCard("Devices Online", "${MockAdminData.devices.count { it.status == "Online" }}", Color(0xFF8B5CF6), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Management Modules",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { ModuleButton("Customers Portal", "Manage client organizations & profiles", Icons.Default.People) { onNavigate("admin_customers") } }
            item { ModuleButton("License Keys", "Generate, sign and extend offline licenses", Icons.Default.VpnKey) { onNavigate("admin_licenses") } }
            item { ModuleButton("Subscription Tiers", "Manage commercial plans and duration locks", Icons.Default.CardMembership) { onNavigate("admin_subscriptions") } }
            item { ModuleButton("Registered Devices", "Monitor device IDs and OS builds", Icons.Default.PhoneAndroid) { onNavigate("admin_devices") } }
            item { ModuleButton("System Reports", "View analytics and usage telemetry", Icons.Default.BarChart) { onNavigate("admin_reports") } }
            item { ModuleButton("Admin Settings", "Console security, PIN and system logs", Icons.Default.Settings) { onNavigate("admin_settings") } }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, color: Color, modifier: Modifier) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ModuleButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
