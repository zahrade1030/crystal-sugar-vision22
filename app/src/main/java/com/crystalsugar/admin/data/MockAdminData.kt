package com.crystalsugar.admin.data

data class AdminCustomer(
    val id: String,
    val name: String,
    val company: String,
    val email: String,
    val activeLicenses: Int,
    val status: String
)

data class AdminLicense(
    val licenseKey: String,
    val customerName: String,
    val deviceId: String,
    val issuedDate: String,
    val expiryDate: String,
    val status: String // Active, Expired, Pending
)

data class AdminSubscription(
    val subscriptionId: String,
    val planType: String,
    val durationDays: Int,
    val autoRenew: Boolean,
    val priceUsd: Double
)

data class AdminDevice(
    val deviceId: String,
    val model: String,
    val osVersion: String,
    val lastOnline: String,
    val status: String
)

object MockAdminData {
    val customers = listOf(
        AdminCustomer("CUST-001", "Aria Laboratory Solutions", "Aria Lab", "info@arialab.com", 5, "Active"),
        AdminCustomer("CUST-002", "Alborz Sugar Refineries", "Alborz Group", "tech@alborzsugar.com", 12, "Active"),
        AdminCustomer("CUST-003", "Kaspian Industrial QC", "Kaspian QC", "support@kaspianqc.org", 2, "Expired"),
        AdminCustomer("CUST-004", "Zagros Quality Control", "Zagros Corp", "admin@zagrosqc.io", 8, "Active")
    )

    val licenses = listOf(
        AdminLicense("CSLIC-365-8A5F2E6B-0012", "Aria Laboratory Solutions", "DEV-CS8899001122", "2026-01-01", "2027-01-01", "Active"),
        AdminLicense("CSLIC-30-14785236-9911", "Alborz Sugar Refineries", "DEV-CS994821039", "2026-07-01", "2026-07-31", "Active"),
        AdminLicense("CSLIC-180-99001122-3344", "Kaspian Industrial QC", "DEV-CS1100223344", "2025-12-01", "2026-05-30", "Expired")
    )

    val subscriptions = listOf(
        AdminSubscription("SUB-30D", "Monthly Professional", 30, false, 149.00),
        AdminSubscription("SUB-180D", "Semi-Annual Enterprise", 180, true, 699.00),
        AdminSubscription("SUB-365D", "Annual Master Tier", 365, true, 1299.00),
        AdminSubscription("SUB-UNL", "Unlimited Lifetime License", 9999, false, 4999.00)
    )

    val devices = listOf(
        AdminDevice("DEV-CS8899001122", "Samsung Galaxy S23 Ultra", "Android 14", "2 mins ago", "Online"),
        AdminDevice("DEV-CS994821039", "Google Pixel 8 Pro", "Android 14", "1 hour ago", "Online"),
        AdminDevice("DEV-CS1100223344", "Xiaomi 13 Pro", "Android 13", "12 days ago", "Offline")
    )
}
