package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel

@Composable
fun SubscriptionUnlockDialog(
    viewModel: MainViewModel,
    isFirstTime: Boolean = false,
    onDismiss: () -> Unit = {},
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val isPersian = viewModel.language.collectAsState().value == "fa"

    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var showPass1 by remember { mutableStateOf(false) }
    var showPass2 by remember { mutableStateOf(false) }

    var isVerified by remember { mutableStateOf(false) }

    var selectedDays by remember { mutableStateOf(30) }
    var customDaysText by remember { mutableStateOf("30") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isFirstTime) onDismiss()
        },
        icon = {
            Icon(
                imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.VpnKey,
                contentDescription = null,
                tint = if (isVerified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (!isVerified) {
                    if (isFirstTime) {
                        if (isPersian) "ورود رمزهای امنیتی اشتراک" else "Enter Security Subscription Passwords"
                    } else {
                        if (isPersian) "تایید رمزهای عبور اشتراک" else "Verify Subscription Passwords"
                    }
                } else {
                    if (isPersian) "تعیین مدت زمان اشتراک" else "Select Subscription Duration"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isVerified) {
                    // STEP 1: Enter Passwords Only
                    Text(
                        text = if (isFirstTime) {
                            if (isPersian) "جهت راه اندازی و ورود به سامانه، ابتدا دو رمز امنیتی اشتراک را وارد نمایید."
                            else "Please enter the two security subscription passwords to verify access."
                        } else {
                            if (isPersian) "جهت تمدید یا فعالسازی اشتراک، ابتدا دو رمز امنیتی را وارد نمایید."
                            else "Please enter the two security subscription passwords to proceed."
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    // Password 1
                    OutlinedTextField(
                        value = pass1,
                        onValueChange = {
                            pass1 = it
                            errorMessage = null
                        },
                        label = { Text(if (isPersian) "رمز عبور اول" else "Password 1", fontSize = 12.sp) },
                        visualTransformation = if (showPass1) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass1 = !showPass1 }) {
                                Icon(
                                    imageVector = if (showPass1) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    // Password 2
                    OutlinedTextField(
                        value = pass2,
                        onValueChange = {
                            pass2 = it
                            errorMessage = null
                        },
                        label = { Text(if (isPersian) "رمز عبور دوم" else "Password 2", fontSize = 12.sp) },
                        visualTransformation = if (showPass2) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPass2 = !showPass2 }) {
                                Icon(
                                    imageVector = if (showPass2) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                } else {
                    // STEP 2: Passwords Verified -> Select Duration
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPersian) "رمزهای عبور با موفقیت تایید شدند." else "Passwords verified successfully.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = if (isPersian) "مدت زمان اشتراک (روز) را انتخاب یا وارد نمایید:" else "Select or enter subscription duration (days):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(30, 90, 180, 365).forEach { days ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick = {
                                    selectedDays = days
                                    customDaysText = days.toString()
                                },
                                label = { Text("${days}d", fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customDaysText,
                        onValueChange = { text ->
                            customDaysText = text.filter { it.isDigit() }
                            selectedDays = customDaysText.toIntOrNull() ?: 30
                        },
                        label = { Text(if (isPersian) "تعداد روز دلخواه" else "Custom Days", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isVerified) {
                        if (viewModel.verifySubscriptionPasswords(pass1, pass2)) {
                            isVerified = true
                            errorMessage = null
                        } else {
                            errorMessage = if (isPersian) "رمزهای عبور وارد شده اشتباه است!" else "Invalid subscription passwords!"
                        }
                    } else {
                        val days = selectedDays.coerceAtLeast(1)
                        if (isFirstTime || !viewModel.subActivated.value) {
                            viewModel.activateSubscription(days)
                        } else {
                            viewModel.extendSubscription(days)
                        }
                        Toast.makeText(
                            context,
                            if (isPersian) "اشتراک با موفقیت فعال شد ($days روز)" else "Subscription successfully activated ($days days)",
                            Toast.LENGTH_LONG
                        ).show()
                        onSuccess()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = if (!isVerified) {
                        if (isPersian) "بررسی و تایید رمزها" else "Verify Passwords"
                    } else {
                        if (isPersian) "ثبت و فعالسازی اشتراک" else "Activate Subscription"
                    }
                )
            }
        },
        dismissButton = {
            if (!isFirstTime || isVerified) {
                TextButton(
                    onClick = {
                        if (isVerified) {
                            isVerified = false
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Text(
                        text = if (isVerified) {
                            if (isPersian) "بازگشت" else "Back"
                        } else {
                            if (isPersian) "انصراف" else "Cancel"
                        }
                    )
                }
            }
        }
    )
}

@Composable
fun ExpiredFeatureNoticeDialog(
    isPersian: Boolean,
    onDismiss: () -> Unit,
    onOpenRenewDialog: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = if (isPersian) "انقضای زمان اشتراک" else "Subscription Expired",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = if (isPersian)
                    "اشتراک شما به پایان رسیده است. جهت دسترسی مجدد به بخش‌های اصلی (دوربین، آنالیز، مراجع و...) لطفاً نسبت به تمدید اشتراک اقدام نمایید.\n\nبخش‌های اعضای تیم، تاریخچه، راهنما و تنظیمات همچنان در دسترس می‌باشند."
                else
                    "Your subscription has expired. Please renew your subscription to access processing and analysis tools.\n\nTeam, History, Help, and Settings remain accessible.",
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onOpenRenewDialog()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPersian) "تمدید اشتراک" else "Renew Subscription")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isPersian) "متوجه شدم" else "Got It")
            }
        }
    )
}
