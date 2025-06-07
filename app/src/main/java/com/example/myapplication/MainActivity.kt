@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource

/* ---------- фирменный синий ---------- */
private val OzonBlue = Color(0xFF1684F8)

/* ---------- константы реквизитов для QR-режима ---------- */
private const val RECIPIENT_SHORT_QR = "ИП Ибрагимов Э Г"
private const val RECIPIENT_FULL_QR =
    "Индивидуальный предприниматель Ибрагимов Эльвин Гаай Оглы"
private const val PAYMENT_TYPE_QR = "Платёж через СБП (QR)"
private const val PAYMENT_TYPE_PHONE = "Платёж по номеру телефона"
private const val ACCOUNT_FROM = "Основной счёт"
data class OperationDetail(
    val amount: String,
    val recipientShort: String,
    val recipientFull: String?,
    val dateTime: String,
    val isPhoneMode: Boolean,
    val customRecipient: String?
)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val askPermission = registerForActivityResult(RequestPermission()) {}

        setContent {
            val scheme = darkColorScheme(
                primary = OzonBlue,
                primaryContainer = OzonBlue,
                secondary = OzonBlue
            )

            /* режим: false — QR-код, true — по номеру телефона */
            var isPhoneMode by rememberSaveable { mutableStateOf(true) }
            /* сумма перевода или null, если ещё не задано */
            var amount by rememberSaveable { mutableStateOf<String?>(null) }
            /* номер телефона или null */
            var phoneNumber by rememberSaveable { mutableStateOf<String?>(null) }
            /* дополнительное поле "Кому переводим" */
            var customRecipient by rememberSaveable { mutableStateOf<String?>(null) }
            /* детали последнего перевода */
            var lastOperation by rememberSaveable { mutableStateOf<OperationDetail?>(null) }

            MaterialTheme(colorScheme = scheme) {
                if (amount == null
                    || (isPhoneMode && phoneNumber == null)
                ) {
                    ModeAndInputScreen(
                        isPhoneMode = isPhoneMode,
                        onModeChange = { isPhoneMode = it },
                        onPhoneEntered = { phone -> phoneNumber = phone },
                        onAmountEntered = { amt -> amount = amt },
                        onCustomRecipientEntered = { name -> customRecipient = name }
                    )
                } else {
                    val amt = amount!!
                    val phone = phoneNumber
                    val custom = customRecipient
                    val nav = rememberNavController()
                    MainNavGraph(
                        navController = nav,
                        amount = amt,
                        phoneNumber = phone,
                        isPhoneMode = isPhoneMode,
                        customRecipient = custom,
                        askCamera = { askPermission.launch(Manifest.permission.CAMERA) },
                        lastOperation = lastOperation,
                        onOperationRecorded = { detail -> lastOperation = detail }
                    )
                }
            }
        }
    }
}

/* ----------------------- навигация ----------------------- */
@Composable
private fun MainNavGraph(
    navController: NavHostController,
    amount: String,
    phoneNumber: String?,
    isPhoneMode: Boolean,
    customRecipient: String?,
    askCamera: () -> Unit,
    lastOperation: OperationDetail?,
    onOperationRecorded: (OperationDetail) -> Unit
) {
    NavHost(navController, startDestination = "home") {
        // 1) HomeScreen
        composable("home") {
            HomeScreen(
                amount = amount,
                phoneNumber = phoneNumber,
                isPhoneMode = isPhoneMode,
                customRecipient = customRecipient,
                lastOperation = lastOperation,
                onQrClick = { navController.navigate("loading") },
                onTransferClick = {
                    navController.navigate("transfer") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onDetailClick = {
                    if (lastOperation != null) {
                        navController.navigate("details")
                    }
                }
            )
        }

        // 2) LoadingScreen — задержка 5 секунд перед переходом к сканеру
        composable("loading") {
            LoadingScreen {
                askCamera()
                navController.navigate("qr") {
                    popUpTo("loading") { inclusive = true }
                }
            }
        }

        // 3) QrScreen — сканирование QR
        composable("qr") {
            QrScreen(
                onExit = { navController.popBackStack() },
                onResult = {
                    navController.navigate("transfer") {
                        popUpTo("qr") { inclusive = true }
                    }
                }
            )
        }

        // 4) TransferScreen
        composable("transfer") {
            TransferScreen(
                amount = amount,
                phoneNumber = phoneNumber,
                isPhoneMode = isPhoneMode,
                customRecipient = customRecipient,
                onNext = {
                    navController.navigate("confirm") {
                        popUpTo("transfer") { inclusive = true }
                    }
                }
            )
        }

        // 5) ConfirmationScreen
        composable("confirm") {
            ConfirmationScreen(
                amount = amount,
                phoneNumber = phoneNumber,
                isPhoneMode = isPhoneMode,
                customRecipient = customRecipient,
                recipientShort = if (isPhoneMode) phoneNumber!! else RECIPIENT_SHORT_QR,
                recipientFull = if (isPhoneMode) phoneNumber!! else RECIPIENT_FULL_QR,
                paymentType = if (isPhoneMode) PAYMENT_TYPE_PHONE else PAYMENT_TYPE_QR,
                accountFrom = ACCOUNT_FROM,
                senderName = "Равилов Роман Ринатович",
                onConfirm = {
                    // При подтверждении сохраняем дату и время перевода
                    val now =
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
                    onOperationRecorded(
                        OperationDetail(
                            amount = "-$amount ₽",
                            recipientShort = if (isPhoneMode) phoneNumber!! else RECIPIENT_SHORT_QR,
                            recipientFull = if (isPhoneMode) phoneNumber!! else RECIPIENT_FULL_QR,
                            dateTime = now,
                            isPhoneMode = isPhoneMode,
                            customRecipient = customRecipient
                        )
                    )
                    navController.navigate("confirm_loading") {
                        popUpTo("confirm") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // 6) ConfirmLoadingScreen — задержка 2 секунды перед успехом
        composable("confirm_loading") {
            LaunchedEffect(Unit) {
                delay(2_000)
                navController.navigate("success") {
                    popUpTo("confirm_loading") { inclusive = true }
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = OzonBlue)
            }
        }

        // 7) PaymentSuccessScreen
        composable("success") {
            PaymentSuccessScreen(
                amount = amount,
                detail = lastOperation,
                isPhoneMode = isPhoneMode,
                onDocuments = {
                    if (lastOperation != null) {
                        navController.navigate("documents")
                    }
                },
                onFinish = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }

        // 8) DetailScreen — показывает детали конкретной операции
        composable("details") {
            if (lastOperation != null) {
                OperationDetailScreen(detail = lastOperation!!) {
                    navController.popBackStack()
                }
            }
        }

        // 9) DocumentScreen — официальный документ
        composable("documents") {
            if (lastOperation != null) {
                DocumentScreen(detail = lastOperation!!) {
                    navController.popBackStack()
                }
            }
        }
    }
}

/* -------------------- экран выбора режима и ввода данных -------------------- */

@Composable
fun ModeAndInputScreen(
    isPhoneMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onPhoneEntered: (String) -> Unit,
    onAmountEntered: (String) -> Unit,
    onCustomRecipientEntered: (String) -> Unit
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var sum by rememberSaveable { mutableStateOf("") }
    var custom by rememberSaveable { mutableStateOf("") }

    val validAmount = sum.toLongOrNull()?.let { it > 0L } == true
    val validPhone = phone.all { it.isDigit() } && phone.length >= 10

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Выберите режим платежа",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isPhoneMode,
                    onClick = { onModeChange(true) }
                )
                Text("По номеру телефона", modifier = Modifier.clickable { onModeChange(true) })
                Spacer(Modifier.width(16.dp))
                RadioButton(
                    selected = !isPhoneMode,
                    onClick = { onModeChange(false) }
                )
                Text("По QR-коду", modifier = Modifier.clickable { onModeChange(false) })
            }

            Spacer(Modifier.height(24.dp))

            // Поле "Кому переводим (можно оставить пустым)" — общий для обоих режимов
            OutlinedTextField(
                value = custom,
                onValueChange = { new -> custom = new },
                singleLine = true,
                label = { Text("Кому переводим (можно оставить пустым)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            if (isPhoneMode) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() }) {
                            phone = new
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    label = { Text("Номер телефона") },
                    placeholder = { Text("Введите номер") },
                    isError = phone.isNotEmpty() && !validPhone,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = sum,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() }) sum = new
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Сумма, ₽") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    enabled = validPhone,
                    onClick = {
                        onCustomRecipientEntered(custom.ifBlank { "" })
                        onPhoneEntered(phone)
                        onAmountEntered(sum)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продолжить")
                }
            } else {
                OutlinedTextField(
                    value = sum,
                    onValueChange = { new ->
                        if (new.all { it.isDigit() }) sum = new
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text("Сумма, ₽") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    enabled = validAmount,
                    onClick = {
                        onCustomRecipientEntered(custom.ifBlank { "" })
                        onAmountEntered(sum)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Продолжить")
                }
            }
        }
    }
}

/* ---------------------------- HOME ---------------------------- */

@Composable
fun HomeScreen(
    amount: String,
    phoneNumber: String?,
    isPhoneMode: Boolean,
    customRecipient: String?,
    lastOperation: OperationDetail?,
    onQrClick: () -> Unit,
    onTransferClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    // Операции, первая зависит от режима
    val firstOpTitle = if (isPhoneMode) phoneNumber!! else RECIPIENT_SHORT_QR
    val firstOpAmount = lastOperation?.amount ?: "-$amount ₽"
    val firstOpSubtitle = when {
        lastOperation != null && lastOperation.customRecipient != null && lastOperation.customRecipient.isNotBlank() ->
            "Кому: ${lastOperation.customRecipient}"
        isPhoneMode -> "Перевод по телефону"
        else -> "Супермаркеты"
    }

    val ops = listOf(
        Op(
            title = firstOpTitle,
            amount = firstOpAmount,
            subtitle = firstOpSubtitle
        ),
        Op("Перевод между счётами", "115 ₽", "Ежедневный доход → Основной счёт"),
        Op("Проценты на остаток", "+0,60 ₽", "Пополнения")
    )

    Scaffold(bottomBar = { BottomBar() }) { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { Header() }
            item { Spacer(Modifier.height(8.dp)) }
            item { QuickActions(onQr = onQrClick, onTransfer = onTransferClick) }
            item { Spacer(Modifier.height(16.dp)) }
            item { DailyIncomeCard() }
            item { Spacer(Modifier.height(16.dp)) }
            item { NewAccountBtn() }
            item { Spacer(Modifier.height(24.dp)) }
            item { OperationsHeader() }
            items(ops) { op ->
                OperationRow(
                    op = op,
                    onClick = {
                        if (op.title == firstOpTitle && lastOperation != null) {
                            onDetailClick()
                        }
                    }
                )
            }
        }
    }
}

/* --------------------- 5-секундный лоадер --------------------- */

@Composable
fun LoadingScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(5_000)
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = OzonBlue)
    }
}

/* ---------------------------- QR ---------------------------- */

@Composable
fun QrScreen(
    onExit: () -> Unit,
    onResult: (String) -> Unit
) {
    val ctx = LocalContext.current
    val life = LocalLifecycleOwner.current
    var cam by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                androidx.camera.view.PreviewView(it).apply {
                    scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { view ->
            val provFuture = ProcessCameraProvider.getInstance(ctx)
            provFuture.addListener({
                val provider = provFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(view.surfaceProvider)
                }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val scanner = BarcodeScanning.getClient()

                analyzer.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { proxy ->
                    if (locked) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val media = proxy.image
                    if (media != null) {
                        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { list ->
                                if (list.isNotEmpty()) {
                                    locked = true
                                    onResult(list[0].rawValue ?: "")
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else proxy.close()
                }

                provider.unbindAll()
                cam = provider.bindToLifecycle(
                    life, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer
                )
                cam?.cameraControl?.enableTorch(torch)
            }, ctx.mainExecutor)
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 180.dp)
        ) {
            val len = 40.dp.toPx()
            val sw = 6.dp.toPx()
            val w = size.width
            val h = size.height

            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(OzonBlue, Offset(x, y), Offset(x + dx * len, y), sw, StrokeCap.Round)
                drawLine(OzonBlue, Offset(x, y), Offset(x, y + dy * len), sw, StrokeCap.Round)
            }
            corner(0f, 0f, +1f, +1f)
            corner(w, 0f, -1f, +1f)
            corner(0f, h, +1f, -1f)
            corner(w, h, -1f, -1f)
        }

        Text(
            "Наведите камеру на\nQR-код",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 160.dp)
        )

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(64.dp)
        ) {
            IconButton(
                onClick = {
                    torch = !torch
                    cam?.cameraControl?.enableTorch(torch)
                }
            ) {
                Icon(
                    if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    null, tint = Color.White, modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { /* галерея – заглушка */ }) {
                Icon(Icons.Default.Image, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            IconButton(onClick = onExit) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}

/* ---------------------------- TransferScreen ---------------------------- */

@Composable
fun TransferScreen(
    amount: String,
    phoneNumber: String?,
    isPhoneMode: Boolean,
    customRecipient: String?,
    onNext: () -> Unit
) {
    val recipientLabel = if (isPhoneMode) "Номер телефона получателя" else "Получатель (QR)"
    val recipientValue = if (isPhoneMode) phoneNumber!! else RECIPIENT_SHORT_QR

    Scaffold { inner ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowBack,
                        null,
                        modifier = Modifier.clickable { onNext() }
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Перевод",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            item { FieldBox("СЧЁТ СПИСАНИЯ", ACCOUNT_FROM) }

            item {
                Spacer(Modifier.height(10.dp))
                Text(recipientLabel, color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        recipientValue,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!isPhoneMode) {
                item { FieldBox("Тип платежа", PAYMENT_TYPE_QR) }
            } else {
                item { FieldBox("Тип платежа", PAYMENT_TYPE_PHONE) }
            }
            item { FieldBox("Сумма", "$amount ₽") }

            if (!customRecipient.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("КОМУ:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            customRecipient,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { onNext() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Перевести $amount ₽")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Банк получателя зачисляет деньги в течение 30 минут, реже — до 3 рабочих дней",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FieldBox(label: String, value: String) {
    Text(label.uppercase(), color = Color.Gray, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            value,
            modifier = Modifier.padding(16.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    Spacer(Modifier.height(8.dp))
}

/* ---------------- ConfirmationScreen ---------------- */

@Composable
fun ConfirmationScreen(
    amount: String,
    phoneNumber: String?,
    isPhoneMode: Boolean,
    customRecipient: String?,
    recipientShort: String,
    recipientFull: String?,
    paymentType: String,
    accountFrom: String,
    senderName: String,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Подтверждение перевода") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OzonBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(16.dp)
        ) {
            Text(
                "Пожалуйста, проверьте реквизиты:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
            Spacer(Modifier.height(16.dp))

            Text("ОТ:", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    senderName,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("СЧЁТ СПИСАНИЯ", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    accountFrom,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("ПОЛУЧАТЕЛЬ", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(recipientShort, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    if (!isPhoneMode && recipientFull != null) {
                        Text(recipientFull, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Тип платежа", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    paymentType,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Text("Сумма", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "$amount ₽",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!customRecipient.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text("КОМУ:", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        customRecipient,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = { onConfirm() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Подтвердить")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Нажимая «Подтвердить», вы соглашаетесь с правилами банка и подтверждаете списание средств.",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

/* ---------------- PaymentSuccessScreen ---------------- */

@Composable
fun PaymentSuccessScreen(
    amount: String,
    detail: OperationDetail?,
    isPhoneMode: Boolean,
    onDocuments: () -> Unit,
    onFinish: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Верхний бар: иконка СБП + "Система быстрых платежей"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fr),
                    contentDescription = "SBP Logo",
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Система быстрых платежей",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Блок с результатом (Surface + Column для скролла содержимого)
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Название банка
                    Text(
                        text = "Ozon Bank",
                        color = Color(0xFF1684F8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Иконка успеха
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Успех",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(60.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Формат суммы с двумя знаками after decimal
                    val formattedAmount = try {
                        String.format("%.2f", amount.toDouble())
                    } catch (e: Exception) {
                        amount
                    }

                    // Сумма перевода
                    Text(
                        text = "Успешно – $formattedAmount ₽",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Дата и время сразу после суммы (жирным)
                    if (detail != null) {
                        Text(
                            text = "Дата и время: ${detail.dateTime}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Повторно выводим название банка (белым)
                    Text(
                        text = "Ozon Bank",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // ФИО отправителя
                    Text(
                        text = "Равилов Роман Ринатович",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Блок «Получатель» в зависимости от режима
                    if (isPhoneMode) {
                        Text(
                            text = "Получатель: ${detail?.recipientShort ?: ""}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Перевод по телефону",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Получатель: $RECIPIENT_SHORT_QR",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Супермаркеты",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    // Кастомный получатель, если есть
                    if (!detail?.customRecipient.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Кому: ${detail?.customRecipient}",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Место по IP
                    Text(
                        text = "Санкт-Петербург Россия  Определено по IP-адресу",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }

            // Синяя панель «Кредитная карта…»
            Surface(
                color = Color(0xFF1684F8),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Кредитная карта с льготным периодом до 78 дней",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Нижний ряд из трех кнопок («Повторить», «Документы», «Шаблон»)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { /* TODO: Повторить */ }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Повторить",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Повторить", fontSize = 12.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onDocuments() }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "Документы",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Документы", fontSize = 12.sp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { /* TODO: Шаблон */ }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Шаблон",
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Шаблон", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Кнопка «Готово»
            Button(
                onClick = { onFinish() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Готово",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Реклама внизу
            Surface(
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(100.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Реклама: Получите кэшбэк до 5% с Ozon Bank!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


/* ------------- Экран деталей операции ------------- */

@Composable
fun OperationDetailScreen(
    detail: OperationDetail,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Детали перевода") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OzonBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(16.dp)
        ) {
            Text("Получатель", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(detail.recipientShort, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    if (!detail.isPhoneMode && detail.recipientFull != null) {
                        Text(detail.recipientFull, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Сумма перевода", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    detail.amount,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!detail.customRecipient.isNullOrBlank()) {
                Text("КОМУ", color = Color.Gray, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        detail.customRecipient,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("Дата и время перевода", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    detail.dateTime,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Санкт-Петербург Россия  Определено по IP-адресу",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Санкт-Петербург, Россия",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ------------- Экран "Документы" с официальным текстом ------------- */

@Composable
fun DocumentScreen(
    detail: OperationDetail,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Официальный документ") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OzonBlue,
                    titleContentColor = Color.White
                )
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(inner)
                .padding(16.dp)
        ) {
            Text(
                text = buildString {
                    append("ООО «Ozon Bank»\n")
                    append("ИНН 1234567890\n")
                    append("БИК 044525225\n\n")
                    append("Настоящим подтверждается, что ")
                    if (detail.customRecipient.isNullOrBlank()) {
                        append(detail.recipientFull ?: detail.recipientShort)
                    } else {
                        append(detail.customRecipient)
                    }
                    append(" получил(а) перевод в размере ${detail.amount}.\n\n")
                    append("Реквизиты получателя:\n")
                    append("   Название: ${detail.recipientShort}\n")
                    if (!detail.isPhoneMode && detail.recipientFull != null) {
                        append("   Полное имя: ${detail.recipientFull}\n")
                    }
                    if (!detail.customRecipient.isNullOrBlank()) {
                        append("   Кому: ${detail.customRecipient}\n")
                    }
                    append("   Счёт списания: $ACCOUNT_FROM\n")
                    append("   Тип платежа: ${if (detail.isPhoneMode) PAYMENT_TYPE_PHONE else PAYMENT_TYPE_QR}\n\n")
                    append("Дата и время проведения операции: ${detail.dateTime}\n")
                    append("Место проведения операции: Санкт-Петербург, Россия (определено по IP-адресу)\n\n")
                    append("Подпись уполномоченного лица:\n")
                    append("__________________________\n\n")
                    append("Печать банка:\n")
                    append("__________________________")
                },
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
    }
}

/* ----------------------- UI-блоки Home ----------------------- */

data class Op(val title: String, val amount: String, val subtitle: String)

@Composable
fun Header() = Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        Icons.Filled.AccountCircle, contentDescription = null,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
    )
    Spacer(Modifier.width(12.dp))
    Text("Роман Ринатович Р.", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun QuickActions(
    onQr: () -> Unit,
    onTransfer: () -> Unit
) = Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp)
) {
    Action(Icons.Filled.QrCode, "Оплата QR", onQr)
    Action(Icons.Filled.Add, "Пополнить")
    Action(Icons.Filled.NorthEast, "Перевести", onTransfer)
}

@Composable
private fun RowScope.Action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.padding(14.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun DailyIncomeCard() = Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clickable {}
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ежедневный доход", fontSize = 14.sp)
            Text("1 159,72 ₽", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("+16,11 ₽ в июне", fontSize = 12.sp, color = Color.Gray)
        }
        Icon(Icons.Filled.CalendarToday, contentDescription = null)
    }
}

@Composable
fun NewAccountBtn() = Button(
    onClick = {},
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
) { Text("Новый счёт или продукт") }

@Composable
fun OperationsHeader() = Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Последние операции", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    TextButton(onClick = {}) { Text("Все") }
}

@Composable
fun OperationRow(op: Op, onClick: () -> Unit) = Row(
    Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(40.dp)
    ) { Icon(Icons.Filled.ShoppingBag, contentDescription = null, modifier = Modifier.padding(8.dp)) }

    Spacer(Modifier.width(12.dp))

    Column(Modifier.weight(1f)) {
        Text(op.title, fontWeight = FontWeight.Medium)
        Text(op.subtitle, fontSize = 12.sp, color = Color.Gray)
    }

    val isPositive = op.amount.trim().startsWith("+")
    Text(
        op.amount,
        fontWeight = FontWeight.SemiBold,
        color = if (isPositive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onBackground
    )
}

@Composable
fun BottomBar() = NavigationBar {
    Nav(Icons.Filled.Payments, "Финансы")
    Nav(Icons.Filled.Send, "Платежи")
    Nav(Icons.Filled.Percent, "Бонусы")
    Nav(Icons.Filled.Chat, "Чат")
    Nav(Icons.Filled.MoreHoriz, "Ещё")
}

@Composable
private fun RowScope.Nav(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) = NavigationBarItem(
    selected = false,
    onClick = {},
    icon = { Icon(icon, contentDescription = label) },
    label = { Text(label) }
)
