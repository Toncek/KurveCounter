package com.motogeek.telemetry.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.motogeek.telemetry.domain.maintenance.MaintenanceTaskDto
import com.motogeek.telemetry.domain.onboarding.BikePurchaseCondition
import com.motogeek.telemetry.domain.onboarding.NewBikeTutorialDraft
import com.motogeek.telemetry.domain.onboarding.SpecsInputMode
import com.motogeek.telemetry.domain.onboarding.TutorialStage
import com.motogeek.telemetry.ui.theme.*
import java.time.LocalDate
import java.util.UUID

private data class QuickIntervalPreset(
    val idPrefix: String,
    val enLabel: String,
    val skLabel: String,
    val category: String,
    val actionType: String
)

private val QUICK_INTERVAL_PRESETS = listOf(
    QuickIntervalPreset("oil", "Engine Oil & Filter", "Motorový olej a filter", "Engine", "Replace"),
    QuickIntervalPreset("chain", "Chain", "Reťaz", "Drivetrain", "Inspect & Lube"),
    QuickIntervalPreset("air_filter", "Air Filter", "Vzduchový filter", "Engine", "Inspect / Replace"),
    QuickIntervalPreset("spark_plugs", "Spark Plugs", "Zapaľovacie sviečky", "Ignition", "Replace"),
    QuickIntervalPreset("brake_fluid", "Brake Fluid", "Brzdová kvapalina", "Brakes", "Replace"),
    QuickIntervalPreset("coolant", "Coolant", "Chladiaca kvapalina", "Cooling", "Replace"),
    QuickIntervalPreset("valves", "Valve Clearance", "Ventilová vôľa", "Engine", "Inspect / Adjust"),
    QuickIntervalPreset("tires", "Tires", "Pneumatiky", "Chassis", "Inspect")
)

private val CATEGORY_CHIPS = listOf(
    "Naked", "Sport", "Adventure", "Touring", "Cruiser", "Enduro", "Scooter"
)

private val CURRENCY_CHIPS = listOf("€", "฿", "$", "Kč", "£")

@Composable
fun AddMotorcycleTutorialDialog(
    draft: NewBikeTutorialDraft,
    statusMessage: String?,
    onDraftChanged: (NewBikeTutorialDraft) -> Unit,
    onBuildPrompt: (NewBikeTutorialDraft) -> String,
    onApplyLlmJson: (String) -> Unit,
    onProceedToMaintenanceReview: () -> Unit,
    onSaveBikeOnly: () -> Unit,
    onDiscard: (deletePersistedBike: Boolean) -> Unit,
    onMinimize: () -> Unit
) {
    val context = LocalContext.current
    val appLang = LocalAppLang.current
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showValidationError by remember { mutableStateOf(false) }
    var promptCopiedBanner by remember { mutableStateOf(false) }
    var showPromptPreview by remember { mutableStateOf(false) }
    var showTorquesSection by remember { mutableStateOf(false) }

    val isStep1 = draft.stage == TutorialStage.BIKE_BASICS
    val canProceedBasics = draft.make.isNotBlank() || draft.model.isNotBlank()

    BackHandler(onBack = onMinimize)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CarbonObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TutorialWindowHeader(
                stepTitle = if (isStep1) {
                    tr("Step 1/2: Bike Basics", "Krok 1/2: Základné údaje")
                } else {
                    tr("Step 2/2: Specs & Schedule", "Krok 2/2: Špecifikácie a plán")
                },
                nextHint = if (isStep1) {
                    tr("Next: Specs & Schedule -> Maintenance Review", "Ďalej: Špecifikácie -> Kontrola servisu")
                } else {
                    tr("Next: Maintenance Review", "Ďalej: Kontrola servisnej knižky")
                },
                stepProgress = if (isStep1) 0.5f else 1.0f,
                autosaved = draft.lastAutosavedEpochMs > 0L,
                onMinimize = onMinimize
            )
        },
        bottomBar = {
            Surface(
                color = TftSurfaceDark,
                border = BorderStroke(1.dp, TitaniumBorder)
            ) {
                val barModifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.navigationBars
                            .union(WindowInsets.ime)
                            .union(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)

                if (isStep1) {
                    Row(
                        modifier = barModifier,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { showDiscardConfirm = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tr("Discard", "Zahodiť"),
                                color = RedlineCrimson,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!canProceedBasics) {
                                    showValidationError = true
                                } else {
                                    onSaveBikeOnly()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tr("Save", "Uložiť"),
                                color = ApexEmeraldGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                if (!canProceedBasics) {
                                    showValidationError = true
                                } else {
                                    onDraftChanged(draft.copy(stage = TutorialStage.SPECS_AND_SCHEDULE))
                                }
                            },
                            modifier = Modifier.weight(1.15f),
                            colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tr("Next ->", "Ďalej ->"),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = barModifier,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    onDraftChanged(draft.copy(stage = TutorialStage.BIKE_BASICS))
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, TitaniumBorder),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tr("Back", "Späť"),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = {
                                    if (!canProceedBasics) {
                                        showValidationError = true
                                        onDraftChanged(draft.copy(stage = TutorialStage.BIKE_BASICS))
                                    } else {
                                        onProceedToMaintenanceReview()
                                    }
                                },
                                modifier = Modifier.weight(1.7f),
                                colors = ButtonDefaults.buttonColors(containerColor = RacingAmberPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tr("Review Maintenance ->", "Kontrola servisu ->"),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { showDiscardConfirm = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tr("Discard", "Zahodiť"),
                                    color = RedlineCrimson,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (!canProceedBasics) {
                                        showValidationError = true
                                    } else {
                                        onSaveBikeOnly()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, ApexEmeraldGreen.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = tr("Save", "Uložiť"),
                                    color = ApexEmeraldGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isStep1) {
                Step1BikeBasicsContent(
                    draft = draft,
                    showValidationError = showValidationError,
                    onDraftChanged = {
                        if (showValidationError && (it.make.isNotBlank() || it.model.isNotBlank())) {
                            showValidationError = false
                        }
                        onDraftChanged(it)
                    }
                )
            } else {
                Step2SpecsAndScheduleContent(
                    draft = draft,
                    appLangCode = appLang.code,
                    statusMessage = statusMessage,
                    promptCopiedBanner = promptCopiedBanner,
                    showPromptPreview = showPromptPreview,
                    showTorquesSection = showTorquesSection,
                    onTogglePromptPreview = { showPromptPreview = !showPromptPreview },
                    onToggleTorquesSection = { showTorquesSection = !showTorquesSection },
                    onCopyPrompt = {
                        val prompt = onBuildPrompt(draft)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("AI Mechanic Prompt", prompt))
                        promptCopiedBanner = true
                    },
                    onPasteClipboard = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (clipText.isNotBlank()) {
                            onDraftChanged(draft.copy(rawLlmJson = clipText))
                        }
                    },
                    onBuildPrompt = onBuildPrompt,
                    onApplyLlmJson = onApplyLlmJson,
                    onDraftChanged = onDraftChanged
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = TftSurfaceDark,
            title = {
                Text(
                    tr("Discard New Motorcycle Setup?", "Zahodiť nastavenie motorky?"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    tr(
                        "All entered details and unsaved draft progress for this bike will be discarded.",
                        "Všetky zadané údaje a rozpracovaný návrh pre tento motocykel budú zahodené."
                    ),
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        onDiscard(true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson)
                ) {
                    Text(tr("Discard", "Zahodiť"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(tr("Keep Editing", "Pokračovať v úpravách"), color = Color.Gray)
                }
            }
        )
    }
}

@Composable
private fun TutorialWindowHeader(
    stepTitle: String,
    nextHint: String,
    stepProgress: Float,
    autosaved: Boolean,
    onMinimize: () -> Unit
) {
    Surface(
        color = TftSurfaceDark,
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tr("NEW MOTORCYCLE SETUP", "NASTAVENIE NOVÉHO MOTOCYKLA"),
                        color = RacingAmberPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = stepTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = nextHint,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = (if (autosaved) ApexEmeraldGreen else Color.Gray).copy(alpha = 0.14f),
                        border = BorderStroke(
                            1.dp,
                            (if (autosaved) ApexEmeraldGreen else Color.Gray).copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (autosaved) {
                                tr("✓ Autosaved", "✓ Uložené")
                            } else {
                                tr("Draft", "Návrh")
                            },
                            color = if (autosaved) ApexEmeraldGreen else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(onClick = onMinimize) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = tr("Minimize tutorial", "Minimalizovať sprievodcu"),
                            tint = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { stepProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = RacingAmberPrimary,
                trackColor = TftSurfaceElevated
            )
        }
    }
}

@Composable
private fun Step1BikeBasicsContent(
    draft: NewBikeTutorialDraft,
    showValidationError: Boolean,
    onDraftChanged: (NewBikeTutorialDraft) -> Unit
) {
    // New vs Used Condition Cards
    Text(
        text = tr("BIKE CONDITION", "STAV MOTOCYKLA"),
        color = Color(0xFF6B7A99),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ConditionSelectCard(
            title = tr("New Bike", "Nový motocykel"),
            subtitle = tr("Brand new / break-in baseline", "Úplne nový / od zábehu"),
            selected = draft.purchaseCondition == BikePurchaseCondition.NEW,
            accentColor = ApexEmeraldGreen,
            icon = Icons.Default.AutoAwesome,
            modifier = Modifier.weight(1f),
            onClick = {
                onDraftChanged(draft.copy(purchaseCondition = BikePurchaseCondition.NEW))
            }
        )
        ConditionSelectCard(
            title = tr("Used Bike", "Ojazdený motocykel"),
            subtitle = tr("Pre-owned / service book history", "Z druhej ruky / servisná história"),
            selected = draft.purchaseCondition == BikePurchaseCondition.USED,
            accentColor = TelemetryIceCyan,
            icon = Icons.Default.History,
            modifier = Modifier.weight(1f),
            onClick = {
                onDraftChanged(draft.copy(purchaseCondition = BikePurchaseCondition.USED))
            }
        )
    }

    if (showValidationError && draft.make.isBlank() && draft.model.isBlank()) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = RedlineCrimson.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = tr(
                    "Please enter at least Make or Model before continuing.",
                    "Pred pokračovaním prosím zadajte aspoň značku alebo model."
                ),
                color = RedlineCrimson,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(10.dp)
            )
        }
    }

    // Identity Card
    TutorialSectionCard(title = tr("IDENTITY & ENGINE", "IDENTITA A MOTOR")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialTextField(
                value = draft.make,
                onValueChange = { onDraftChanged(draft.copy(make = it)) },
                label = tr("Make *", "Značka *"),
                placeholder = "e.g. Honda, Yamaha, KTM",
                modifier = Modifier.weight(1f)
            )
            TutorialTextField(
                value = draft.model,
                onValueChange = { onDraftChanged(draft.copy(model = it)) },
                label = tr("Model *", "Model *"),
                placeholder = "e.g. CB650R, MT-09",
                modifier = Modifier.weight(1f)
            )
        }

        TutorialTextField(
            value = draft.nickname,
            onValueChange = { onDraftChanged(draft.copy(nickname = it)) },
            label = tr("Nickname (optional)", "Prezývka (voliteľné)"),
            placeholder = tr("e.g. Track Beast, Daily Commuter", "napr. Môj miláčik, Denný stroj"),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialTextField(
                value = draft.yearText,
                onValueChange = { onDraftChanged(draft.copy(yearText = it)) },
                label = tr("Year", "Rok výroby"),
                placeholder = "e.g. 2023",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            TutorialTextField(
                value = draft.engineDisplacementCcText,
                onValueChange = { onDraftChanged(draft.copy(engineDisplacementCcText = it)) },
                label = tr("Displacement cc (optional)", "Objem cm³ (voliteľné)"),
                placeholder = "e.g. 649",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = tr("Category", "Kategória"),
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CATEGORY_CHIPS.forEach { cat ->
                val selected = draft.bikeType.equals(cat, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { onDraftChanged(draft.copy(bikeType = cat)) },
                    label = { Text(cat, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RacingAmberPrimary.copy(alpha = 0.25f),
                        selectedLabelColor = RacingAmberPrimary,
                        containerColor = TftSurfaceElevated,
                        labelColor = Color.LightGray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = TitaniumBorder,
                        selectedBorderColor = RacingAmberPrimary
                    )
                )
            }
        }
        TutorialTextField(
            value = draft.bikeType,
            onValueChange = { onDraftChanged(draft.copy(bikeType = it)) },
            label = tr("Category / Style", "Kategória / Typ"),
            placeholder = tr("e.g. Naked, Sport, Adventure", "napr. Naked, Sport, Adventure"),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Mileage & Purchase Card
    TutorialSectionCard(title = tr("MILEAGE & PURCHASE", "NÁJAZD A KÚPA")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialTextField(
                value = draft.currentOdometerKmText,
                onValueChange = { onDraftChanged(draft.copy(currentOdometerKmText = it)) },
                label = tr("Current Mileage (km)", "Aktuálny stav km"),
                placeholder = "e.g. 12500",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            if (draft.purchaseCondition == BikePurchaseCondition.USED || draft.purchaseOdometerKmText.isNotBlank()) {
                TutorialTextField(
                    value = draft.purchaseOdometerKmText,
                    onValueChange = { onDraftChanged(draft.copy(purchaseOdometerKmText = it)) },
                    label = tr("Purchase Mileage (km, optional)", "Km pri kúpe (voliteľné)"),
                    placeholder = "e.g. 10000",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        TutorialTextField(
            value = draft.buyingPriceText,
            onValueChange = { onDraftChanged(draft.copy(buyingPriceText = it)) },
            label = tr("Purchase Price (optional)", "Kúpna cena (voliteľné)"),
            placeholder = "e.g. 7500",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tr("Currency:", "Mena:"),
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            CURRENCY_CHIPS.forEach { symbol ->
                val selected = draft.currencySymbol == symbol
                FilterChip(
                    selected = selected,
                    onClick = { onDraftChanged(draft.copy(currencySymbol = symbol)) },
                    label = { Text(symbol, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelemetryIceCyan.copy(alpha = 0.2f),
                        selectedLabelColor = TelemetryIceCyan,
                        containerColor = TftSurfaceElevated,
                        labelColor = Color.LightGray
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = TitaniumBorder,
                        selectedBorderColor = TelemetryIceCyan
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TutorialTextField(
                value = draft.purchaseDateIso,
                onValueChange = { onDraftChanged(draft.copy(purchaseDateIso = it)) },
                label = tr("Purchase Date (YYYY-MM-DD)", "Dátum kúpy (RRRR-MM-DD)"),
                placeholder = "YYYY-MM-DD",
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = {
                    onDraftChanged(draft.copy(purchaseDateIso = LocalDate.now().toString()))
                },
                border = BorderStroke(1.dp, TelemetryIceCyan.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(tr("Today", "Dnes"), color = TelemetryIceCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Step2SpecsAndScheduleContent(
    draft: NewBikeTutorialDraft,
    appLangCode: String,
    statusMessage: String?,
    promptCopiedBanner: Boolean,
    showPromptPreview: Boolean,
    showTorquesSection: Boolean,
    onTogglePromptPreview: () -> Unit,
    onToggleTorquesSection: () -> Unit,
    onCopyPrompt: () -> Unit,
    onPasteClipboard: () -> Unit,
    onBuildPrompt: (NewBikeTutorialDraft) -> String,
    onApplyLlmJson: (String) -> Unit,
    onDraftChanged: (NewBikeTutorialDraft) -> Unit
) {
    // Mode Switcher
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeTabButton(
            title = tr("AI Manual / JSON", "AI Manuál / JSON"),
            subtitle = tr("Recommended • Fast", "Odporúčané • Rýchle"),
            selected = draft.specsInputMode == SpecsInputMode.LLM_JSON,
            modifier = Modifier.weight(1f),
            onClick = { onDraftChanged(draft.copy(specsInputMode = SpecsInputMode.LLM_JSON)) }
        )
        ModeTabButton(
            title = tr("Manual Entry", "Manuálne zadanie"),
            subtitle = tr("Specs & intervals", "Parametre a intervaly"),
            selected = draft.specsInputMode == SpecsInputMode.MANUAL,
            modifier = Modifier.weight(1f),
            onClick = { onDraftChanged(draft.copy(specsInputMode = SpecsInputMode.MANUAL)) }
        )
    }

    if (draft.specsInputMode == SpecsInputMode.LLM_JSON) {
        // 3-step AI guide
        TutorialSectionCard(title = tr("3-STEP AI SERVICE MANUAL IMPORT", "3-KROKOVÝ IMPORT Z MANUÁLU CEZ AI")) {
            StepGuideRow("1", tr("Copy the tailored Master Mechanic prompt for your motorcycle.", "Skopírujte pripravený mechanický prompt pre váš motocykel."))
            StepGuideRow("2", tr("Paste it into ChatGPT, Claude, or Gemini (attach PDF manual if available).", "Vložte ho do ChatGPT, Claude alebo Gemini (pridajte PDF manuál ak ho máte)."))
            StepGuideRow("3", tr("Paste the generated JSON below and tap Parse & Apply JSON.", "Vložte vygenerovaný JSON nižšie a stlačte Spracovať a použiť JSON."))

            Button(
                onClick = onCopyPrompt,
                colors = ButtonDefaults.buttonColors(containerColor = TftSurfaceElevated),
                border = BorderStroke(1.dp, RacingAmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(tr("Copy LLM Prompt", "Skopírovať LLM prompt"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
            }

            if (promptCopiedBanner) {
                Text(
                    text = tr("✓ Prompt copied to clipboard", "✓ Prompt skopírovaný do schránky"),
                    color = ApexEmeraldGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTogglePromptPreview)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr("Preview Prompt", "Náhľad promptu"),
                    color = TelemetryIceCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showPromptPreview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TelemetryIceCyan
                )
            }

            AnimatedVisibility(visible = showPromptPreview) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CarbonObsidianBg,
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = remember(draft.make, draft.model, draft.yearText, draft.engineDisplacementCcText, draft.bikeType, appLangCode) {
                            onBuildPrompt(draft)
                        },
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            OutlinedTextField(
                value = draft.rawLlmJson,
                onValueChange = { onDraftChanged(draft.copy(rawLlmJson = it)) },
                label = { Text(tr("LLM JSON Output", "Výstupný JSON z LLM")) },
                placeholder = {
                    Text(
                        "{\n  \"technicalSpecs\": { ... },\n  \"maintenanceSchedule\": [ ... ]\n}",
                        color = Color.DarkGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                },
                colors = tutorialFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteClipboard,
                    border = BorderStroke(1.dp, TitaniumBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = TelemetryIceCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Paste from Clipboard", "Vložiť zo schránky"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onApplyLlmJson(draft.rawLlmJson) },
                    enabled = draft.rawLlmJson.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ApexEmeraldGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr("Parse & Apply JSON", "Spracovať a použiť JSON"), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                val isError = statusMessage.contains("fail", ignoreCase = true) ||
                    statusMessage.contains("zlyh", ignoreCase = true) ||
                    statusMessage.contains("error", ignoreCase = true)
                val feedbackColor = if (isError) RedlineCrimson else ApexEmeraldGreen
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = feedbackColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, feedbackColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusMessage,
                        color = feedbackColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (draft.maintenanceTasks.isNotEmpty() || draft.extraSpecsJson.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TftSurfaceElevated,
                    border = BorderStroke(1.dp, TitaniumBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tr(
                                "Applied Data Summary: ${draft.maintenanceTasks.size} maintenance tasks ready",
                                "Súhrn použitých dát: ${draft.maintenanceTasks.size} úloh údržby pripravených"
                            ),
                            color = TelemetryIceCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val specSummary = listOfNotNull(
                            draft.oilCapacityLitersText.takeIf { it.isNotBlank() }?.let { "Oil: $it L" },
                            draft.oilViscosityAndSpec.takeIf { it.isNotBlank() },
                            draft.frontTirePressurePsiText.takeIf { it.isNotBlank() }?.let { "Front: $it PSI" },
                            draft.rearTirePressurePsiText.takeIf { it.isNotBlank() }?.let { "Rear: $it PSI" },
                            draft.chainSlackMm.takeIf { it.isNotBlank() }?.let { "Chain: $it mm" }
                        ).joinToString(" • ")
                        if (specSummary.isNotBlank()) {
                            Text(text = specSummary, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    } else {
        // MANUAL MODE: Blank spec & torque fields + Important Maintenance Intervals
        TutorialSectionCard(title = tr("FLUIDS, TIRES & TUNING SPECS", "KVAPALINY, PNEU A NASTAVENIA")) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.oilCapacityLitersText,
                    onValueChange = { onDraftChanged(draft.copy(oilCapacityLitersText = it)) },
                    label = tr("Oil w/ Filter (L)", "Olej s filtrom (L)"),
                    placeholder = "e.g. 2.7",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.oilViscosityAndSpec,
                    onValueChange = { onDraftChanged(draft.copy(oilViscosityAndSpec = it)) },
                    label = tr("Oil Grade / Spec", "Viskozita oleja"),
                    placeholder = "e.g. 10W-40 JASO MA2",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.oilCapacityNoFilterLitersText,
                    onValueChange = { onDraftChanged(draft.copy(oilCapacityNoFilterLitersText = it)) },
                    label = tr("Oil No Filter (L)", "Olej bez filtra (L)"),
                    placeholder = "e.g. 2.4",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.oilTotalDryCapacityLitersText,
                    onValueChange = { onDraftChanged(draft.copy(oilTotalDryCapacityLitersText = it)) },
                    label = tr("Oil Dry Cap (L)", "Olej suchá náplň (L)"),
                    placeholder = "e.g. 3.2",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.coolantCapacityLitersText,
                    onValueChange = { onDraftChanged(draft.copy(coolantCapacityLitersText = it)) },
                    label = tr("Coolant (L)", "Chladiaca kvap. (L)"),
                    placeholder = "e.g. 1.8",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.coolantType,
                    onValueChange = { onDraftChanged(draft.copy(coolantType = it)) },
                    label = tr("Coolant Type", "Typ chladiacej"),
                    placeholder = "e.g. Ethylene glycol",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.brakeFluidType,
                    onValueChange = { onDraftChanged(draft.copy(brakeFluidType = it)) },
                    label = tr("Brake Fluid", "Brzdová kvapalina"),
                    placeholder = "e.g. DOT 4",
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.brakeFluidVolumeMlText,
                    onValueChange = { onDraftChanged(draft.copy(brakeFluidVolumeMlText = it)) },
                    label = tr("Brake Vol (ml)", "Objem bŕzd (ml)"),
                    placeholder = "e.g. 250",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.forkOilCapacityPerLegMlText,
                    onValueChange = { onDraftChanged(draft.copy(forkOilCapacityPerLegMlText = it)) },
                    label = tr("Fork Oil / Leg (ml)", "Olej vidlice (ml)"),
                    placeholder = "e.g. 460",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.forkOilWeight,
                    onValueChange = { onDraftChanged(draft.copy(forkOilWeight = it)) },
                    label = tr("Fork Oil Weight", "Viskozita vidlice"),
                    placeholder = "e.g. 10W",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.frontTirePressurePsiText,
                    onValueChange = { onDraftChanged(draft.copy(frontTirePressurePsiText = it)) },
                    label = tr("Front Tire (PSI)", "Predná pneu (PSI)"),
                    placeholder = "e.g. 36",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.rearTirePressurePsiText,
                    onValueChange = { onDraftChanged(draft.copy(rearTirePressurePsiText = it)) },
                    label = tr("Rear Tire (PSI)", "Zadná pneu (PSI)"),
                    placeholder = "e.g. 42",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.frontTireSize,
                    onValueChange = { onDraftChanged(draft.copy(frontTireSize = it)) },
                    label = tr("Front Tire Size", "Rozmer prednej pneu"),
                    placeholder = "e.g. 120/70 ZR17",
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.rearTireSize,
                    onValueChange = { onDraftChanged(draft.copy(rearTireSize = it)) },
                    label = tr("Rear Tire Size", "Rozmer zadnej pneu"),
                    placeholder = "e.g. 180/55 ZR17",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.chainSlackMm,
                    onValueChange = { onDraftChanged(draft.copy(chainSlackMm = it)) },
                    label = tr("Chain Slack (mm)", "Prieves reťaze (mm)"),
                    placeholder = "e.g. 25-35",
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.idleSpeedRpm,
                    onValueChange = { onDraftChanged(draft.copy(idleSpeedRpm = it)) },
                    label = tr("Idle Speed (RPM)", "Voľnobeh (RPM)"),
                    placeholder = "e.g. 1250",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.sparkPlugModel,
                    onValueChange = { onDraftChanged(draft.copy(sparkPlugModel = it)) },
                    label = tr("Spark Plug", "Sviečka"),
                    placeholder = "e.g. NGK CR9EIA-9",
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.sparkPlugGapMm,
                    onValueChange = { onDraftChanged(draft.copy(sparkPlugGapMm = it)) },
                    label = tr("Plug Gap (mm)", "Odtrh sviečky (mm)"),
                    placeholder = "e.g. 0.8-0.9",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = draft.valveClearanceIntakeMm,
                    onValueChange = { onDraftChanged(draft.copy(valveClearanceIntakeMm = it)) },
                    label = tr("Intake Valve (mm)", "Sacie ventily (mm)"),
                    placeholder = "e.g. 0.11-0.20",
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = draft.valveClearanceExhaustMm,
                    onValueChange = { onDraftChanged(draft.copy(valveClearanceExhaustMm = it)) },
                    label = tr("Exhaust Valve (mm)", "Výfukové ventily (mm)"),
                    placeholder = "e.g. 0.21-0.30",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Torques section (expandable)
        TutorialSectionCard(title = tr("BOLT TORQUES (NM)", "UŤAHOVACIE MOMENTY (NM)")) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleTorquesSection),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = tr("Enter workshop torque specs (optional)", "Zadajte uťahovacie momenty (voliteľné)"),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Icon(
                    imageVector = if (showTorquesSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = RacingAmberPrimary
                )
            }

            AnimatedVisibility(visible = showTorquesSection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TutorialTextField(
                            value = draft.oilDrainPlugTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(oilDrainPlugTorqueNmText = it)) },
                            label = tr("Oil Drain Plug (Nm)", "Výpustná skrutka (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        TutorialTextField(
                            value = draft.oilFilterTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(oilFilterTorqueNmText = it)) },
                            label = tr("Oil Filter (Nm)", "Olejový filter (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TutorialTextField(
                            value = draft.rearAxleNutTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(rearAxleNutTorqueNmText = it)) },
                            label = tr("Rear Axle Nut (Nm)", "Matica zadnej osi (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        TutorialTextField(
                            value = draft.frontAxleTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(frontAxleTorqueNmText = it)) },
                            label = tr("Front Axle (Nm)", "Predná os (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TutorialTextField(
                            value = draft.frontPinchBoltsTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(frontPinchBoltsTorqueNmText = it)) },
                            label = tr("Front Pinch Bolts (Nm)", "Poistné skrutky (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        TutorialTextField(
                            value = draft.sparkPlugTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(sparkPlugTorqueNmText = it)) },
                            label = tr("Spark Plug (Nm)", "Sviečka (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TutorialTextField(
                            value = draft.frontCaliperBoltsTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(frontCaliperBoltsTorqueNmText = it)) },
                            label = tr("Front Caliper (Nm)", "Predný strmeň (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        TutorialTextField(
                            value = draft.rearCaliperBoltsTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(rearCaliperBoltsTorqueNmText = it)) },
                            label = tr("Rear Caliper (Nm)", "Zadný strmeň (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TutorialTextField(
                            value = draft.waterPumpDrainBoltTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(waterPumpDrainBoltTorqueNmText = it)) },
                            label = tr("Water Pump Drain (Nm)", "Vyp. vod. pumpy (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        TutorialTextField(
                            value = draft.cylinderDrainBoltTorqueNmText,
                            onValueChange = { onDraftChanged(draft.copy(cylinderDrainBoltTorqueNmText = it)) },
                            label = tr("Cylinder Drain (Nm)", "Vyp. valca (Nm)"),
                            placeholder = "Nm",
                            keyboardType = KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TutorialTextField(
                        value = draft.brakeBleedNippleTorqueNmText,
                        onValueChange = { onDraftChanged(draft.copy(brakeBleedNippleTorqueNmText = it)) },
                        label = tr("Brake Bleed Nipple (Nm)", "Odvzdušňovák bŕzd (Nm)"),
                        placeholder = "Nm",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Important Maintenance Intervals Editor
        TutorialSectionCard(title = tr("IMPORTANT MAINTENANCE INTERVALS", "DÔLEŽITÉ SERVISNÉ INTERVALY")) {
            Text(
                text = tr(
                    "Tap a quick chip to add a blank interval, or create a custom task. Enter the exact intervals from your owner's manual.",
                    "Ťuknutím na rýchlu voľbu pridáte prázdny interval alebo vlastný úkon. Zadajte presné intervaly z manuálu."
                ),
                color = Color.LightGray,
                fontSize = 11.sp
            )

            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QUICK_INTERVAL_PRESETS.forEach { preset ->
                    val label = if (appLangCode == "sk") preset.skLabel else preset.enLabel
                    AssistChip(
                        onClick = {
                            val newTask = MaintenanceTaskDto(
                                taskId = "${preset.idPrefix}_${UUID.randomUUID().toString().take(6)}",
                                category = preset.category,
                                taskName = label,
                                actionType = preset.actionType,
                                firstBreakInAtKm = null,
                                intervalKm = null,
                                intervalMonths = null
                            )
                            onDraftChanged(draft.copy(maintenanceTasks = draft.maintenanceTasks + newTask))
                        },
                        label = { Text("+ $label", fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = TftSurfaceElevated,
                            labelColor = TelemetryIceCyan
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = TitaniumBorder
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val customTask = MaintenanceTaskDto(
                        taskId = "custom_${UUID.randomUUID().toString().take(8)}",
                        category = "General",
                        taskName = "",
                        actionType = "Inspect / Replace",
                        firstBreakInAtKm = null,
                        intervalKm = null,
                        intervalMonths = null
                    )
                    onDraftChanged(draft.copy(maintenanceTasks = draft.maintenanceTasks + customTask))
                },
                border = BorderStroke(1.dp, RacingAmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = RacingAmberPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr("+ Add Custom Interval", "+ Pridať vlastný interval"), color = RacingAmberPrimary, fontWeight = FontWeight.Bold)
            }

            draft.maintenanceTasks.forEachIndexed { index, task ->
                EditableIntervalCard(
                    task = task,
                    onTaskChanged = { updatedTask ->
                        val updatedList = draft.maintenanceTasks.toMutableList().apply {
                            set(index, updatedTask)
                        }
                        onDraftChanged(draft.copy(maintenanceTasks = updatedList))
                    },
                    onDelete = {
                        val updatedList = draft.maintenanceTasks.toMutableList().apply {
                            removeAt(index)
                        }
                        onDraftChanged(draft.copy(maintenanceTasks = updatedList))
                    }
                )
            }
        }
    }
}

@Composable
private fun EditableIntervalCard(
    task: MaintenanceTaskDto,
    onTaskChanged: (MaintenanceTaskDto) -> Unit,
    onDelete: () -> Unit
) {
    var breakInText by remember(task.taskId) {
        mutableStateOf(task.firstBreakInAtKm?.let { formatIntervalNumber(it) } ?: "")
    }
    var intervalKmText by remember(task.taskId) {
        mutableStateOf(task.intervalKm?.let { formatIntervalNumber(it) } ?: "")
    }
    var intervalMonthsText by remember(task.taskId) {
        mutableStateOf(task.intervalMonths?.toString() ?: "")
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = TftSurfaceElevated,
        border = BorderStroke(1.dp, TitaniumBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = task.category.ifBlank { tr("Task", "Úkon") },
                    color = RacingAmberPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove task", tint = RedlineCrimson, modifier = Modifier.size(16.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = task.taskName,
                    onValueChange = { onTaskChanged(task.copy(taskName = it)) },
                    label = tr("Task Name", "Názov úkonu"),
                    placeholder = tr("e.g. Engine Oil & Filter", "napr. Výmena oleja"),
                    modifier = Modifier.weight(1.4f)
                )
                TutorialTextField(
                    value = task.category,
                    onValueChange = { onTaskChanged(task.copy(category = it)) },
                    label = tr("Category", "Kategória"),
                    placeholder = "Engine",
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialTextField(
                    value = breakInText,
                    onValueChange = { raw ->
                        breakInText = raw
                        val parsed = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                        onTaskChanged(task.copy(firstBreakInAtKm = parsed))
                    },
                    label = tr("Break-in (km)", "Zábeh (km)"),
                    placeholder = "e.g. 1000",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = intervalKmText,
                    onValueChange = { raw ->
                        intervalKmText = raw
                        val parsed = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
                        onTaskChanged(task.copy(intervalKm = parsed))
                    },
                    label = tr("Every (km)", "Každých (km)"),
                    placeholder = "e.g. 10000",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f)
                )
                TutorialTextField(
                    value = intervalMonthsText,
                    onValueChange = { raw ->
                        intervalMonthsText = raw
                        val parsed = raw.trim().toIntOrNull()?.takeIf { it > 0 }
                        onTaskChanged(task.copy(intervalMonths = parsed))
                    },
                    label = tr("Months", "Mesiace"),
                    placeholder = "e.g. 12",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ConditionSelectCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f) else TftSurfaceDark,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accentColor else TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) accentColor else Color.Gray, modifier = Modifier.size(20.dp))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(subtitle, color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun ModeTabButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) RacingAmberPrimary.copy(alpha = 0.18f) else TftSurfaceDark,
        border = BorderStroke(1.dp, if (selected) RacingAmberPrimary else TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = if (selected) RacingAmberPrimary else Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun TutorialSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = TftSurfaceDark),
        border = BorderStroke(1.dp, TitaniumBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF6B7A99),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            content()
        }
    }
}

@Composable
private fun StepGuideRow(stepNumber: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = RacingAmberPrimary.copy(alpha = 0.2f),
            border = BorderStroke(1.dp, RacingAmberPrimary)
        ) {
            Text(
                text = stepNumber,
                color = RacingAmberPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(text = text, color = Color.LightGray, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun TutorialTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text(placeholder, color = Color.Gray, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = tutorialFieldColors(),
        modifier = modifier
    )
}

@Composable
private fun tutorialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = RacingAmberPrimary,
    unfocusedBorderColor = TitaniumBorder,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = RacingAmberPrimary,
    unfocusedLabelColor = Color.LightGray
)

private fun formatIntervalNumber(value: Double): String {
    val asLong = value.toLong()
    return if (value == asLong.toDouble()) asLong.toString() else value.toString()
}
