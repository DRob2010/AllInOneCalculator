package com.example.calculator

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.ui.theme.CalculatorTheme
import androidx.core.view.WindowCompat
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cbrt
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.math.tanh
import kotlinx.coroutines.launch

private const val PINNED_KEY = "pinned_scientific_functions"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val preferences = remember {
                getSharedPreferences("calculator_preferences", MODE_PRIVATE)
            }
            var darkMode by remember {
                mutableStateOf(preferences.getBoolean("dark_mode", false))
            }
            var accentColor by remember {
                mutableStateOf(
                    Color(preferences.getInt("accent_color", Color(0xFF5B5FEF).toArgb()))
                )
            }
            CalculatorTheme(darkTheme = darkMode, dynamicColor = false) {
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as Activity).window
                    val barColor = if (darkMode) Color(0xFF121212) else Color(0xFFF6F7FB)
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = !darkMode
                    insetsController.isAppearanceLightNavigationBars = !darkMode
                }
                CalculatorScreen(
                    darkMode = darkMode,
                    onDarkModeChanged = {
                        darkMode = it
                        preferences.edit().putBoolean("dark_mode", it).apply()
                    },
                    accentColor = accentColor,
                    onAccentColorChanged = {
                        accentColor = it
                        preferences.edit().putInt("accent_color", it.toArgb()).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun CalculatorScreen(
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit
) {
    var selectedMode by remember { mutableStateOf(CalculatorMode.CALCULATOR) }
    var display by remember { mutableStateOf("0") }
    var useRadians by remember { mutableStateOf(false) }
    var startsNewNumber by remember { mutableStateOf(false) }
    var equation by remember { mutableStateOf("") }
    var expression by remember { mutableStateOf("") }
    var justCalculated by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("calculator_preferences", Activity.MODE_PRIVATE)
    }
    var pinnedScientific by remember {
        mutableStateOf<List<String>>(
            preferences.getString(PINNED_KEY, "")
                .orEmpty()
                .split(HISTORY_SEPARATOR)
                .filter { it.isNotEmpty() }
        )
    }
    val recentCalculations = remember {
        mutableStateListOf<String>().apply {
            val savedHistory = preferences.getString(HISTORY_KEY, "").orEmpty()
            if (savedHistory.isNotEmpty()) addAll(savedHistory.split(HISTORY_SEPARATOR))
        }
    }
    val recentScrollState = rememberScrollState(initial = Int.MAX_VALUE)
    var keepRecentAtBottom by remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    fun calculate() {
        if (expression.isBlank()) return
        try {
            keepRecentAtBottom = recentScrollState.value >= recentScrollState.maxValue - 4
            val balancedExpression = balanceParentheses(expression)
            val result = evaluateExpression(balancedExpression, useRadians)
            equation = "$balancedExpression ="
            display = result.formatNumber()
            recentCalculations.add(0, "$balancedExpression = ${result.formatNumber()}")
            if (recentCalculations.size > 50) recentCalculations.removeAt(recentCalculations.lastIndex)
            preferences.edit()
                .putString(HISTORY_KEY, recentCalculations.joinToString(HISTORY_SEPARATOR))
                .apply()
            expression = display
            justCalculated = true
            startsNewNumber = true
        } catch (_: IllegalArgumentException) {
            equation = expression
            display = "Error"
            expression = ""
            justCalculated = true
        }
    }

    LaunchedEffect(recentCalculations.size) {
        if (keepRecentAtBottom) recentScrollState.scrollTo(recentScrollState.maxValue)
    }

    fun press(value: String) {
        when {
            value == "C" -> {
                display = "0"
                startsNewNumber = false
                equation = ""
                expression = ""
                justCalculated = false
            }
            value == "⌫" -> {
                if (!justCalculated) {
                    expression = removeLastInputToken(expression)
                    display = expression.ifEmpty { "0" }
                    equation = expression
                }
            }
            value == "." -> {
                if (justCalculated) {
                    expression = ""
                    equation = ""
                    justCalculated = false
                }
                val currentNumber = expression.substringAfterLast("+")
                    .substringAfterLast("−")
                    .substringAfterLast("×")
                    .substringAfterLast("÷")
                    .substringAfterLast("(")
                if (!currentNumber.contains(".")) {
                    expression += if (expression.isEmpty() || expression.last() in "+−×÷(") "0." else "."
                    display = expression
                    equation = expression
                }
            }
            value == "(" || value == ")" -> {
                if (justCalculated) {
                    expression = ""
                    equation = ""
                    justCalculated = false
                }
                if (value == "(" || expression.isNotEmpty()) {
                    expression += value
                    display = expression
                    equation = expression
                }
            }
            value in listOf("+", "−", "×", "÷") -> {
                if (justCalculated) {
                    expression = display
                    justCalculated = false
                }
                if (expression.isEmpty() && value == "−") {
                    expression = value
                } else if (expression.isNotEmpty()) {
                    if (expression.last() in "+−×÷") {
                        expression = expression.dropLast(1) + value
                    } else if (expression.last() != '(' || value == "−") {
                        expression += value
                    }
                }
                display = expression
                equation = expression
            }
            value == "=" -> calculate()
            else -> {
                if (justCalculated || display == "Error") {
                    expression = ""
                    equation = ""
                    justCalculated = false
                }
                if (expression.filter { it.isDigit() }.length < MAX_DIGITS) {
                    expression += value
                    display = expression
                    equation = expression
                }
            }
        }
    }

    fun scientific(value: String) {
        if (justCalculated) {
            expression = ""
            equation = ""
            justCalculated = false
        }
        if (value == "^") {
            if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
                expression += "^"
                display = expression
                equation = expression
            }
            return
        }
        if (value == "XrootY") {
            expression += "root"
            display = expression
            equation = expression
            return
        }
        if (expression.isNotEmpty() && (expression.last().isDigit() || expression.last() == ')')) {
            expression += "×"
        }
        val token = when (value) {
            "π" -> "pi"
            "√" -> "sqrt"
            "∛" -> "cbrt"
            "x²" -> "square"
            "x³" -> "cube"
            "eˣ" -> "exp"
            "XrootY" -> "root"
            "1/x" -> "inv"
            "%" -> "percent"
            "n!" -> "factorial"
            else -> value
        }
        expression += if (value == "π" || value == "e") token else "$token("
        display = expression
        equation = expression
        startsNewNumber = false
    }

    val rows = listOf(
        listOf("C", "⌫", "÷", "×"),
        listOf("7", "8", "9", "−"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("0", ".", "(", ")")
    )
    val scientificFunctions = listOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "ln", "log", "exp", "1/x", "abs",
        "floor", "ceil", "%", "n!", "√", "∛", "x²", "x³", "eˣ", "^", "XrootY", "π", "e"
    )
    val displayFontSize = when {
        display.length <= 8 -> 48.sp
        display.length <= 11 -> 40.sp
        display.length <= 14 -> 32.sp
        else -> 26.sp
    }
    val screenBackground = if (darkMode) Color(0xFF121212) else Color(0xFFF6F7FB)
    val screenText = if (darkMode) Color.White else Color(0xFF202124)

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet {
                SettingsDrawerContent(
                    onClose = { drawerScope.launch { drawerState.close() } },
                    onClearHistory = {
                        recentCalculations.clear()
                        preferences.edit().remove(HISTORY_KEY).apply()
                    },
                    darkMode = darkMode,
                    onDarkModeChanged = onDarkModeChanged,
                    accentColor = accentColor,
                    onAccentColorChanged = onAccentColorChanged
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(screenBackground)
                .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 14.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                    Text(
                        text = "⚙",
                        fontSize = 27.sp,
                        color = if (darkMode) Color(0xFFEAEAEA) else Color(0xFF202124)
                    )
                    }
                    Button(
                        onClick = { useRadians = !useRadians },
                        modifier = Modifier.height(36.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (darkMode) accentColor.copy(alpha = 0.35f) else accentColor.copy(alpha = 0.15f),
                            contentColor = if (darkMode) Color.White else accentColor
                        )
                    ) {
                        Text(if (useRadians) "RAD" else "DEG", fontSize = 12.sp)
                    }
                }
            }

        if (selectedMode == CalculatorMode.CALCULATOR) {
            if (recentCalculations.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(recentScrollState)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    recentCalculations.asReversed().forEach { calculation ->
                        Text(
                            text = calculation,
                            color = if (darkMode) Color(0xFFBDBDBD) else Color(0xFF777777),
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = equation,
                        color = if (darkMode) Color(0xFFBDBDBD) else Color(0xFF777777),
                        fontSize = 18.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    Text(
                        text = display,
                        color = screenText,
                        fontSize = displayFontSize,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (scientificFunctions.filter { it in pinnedScientific } +
                    scientificFunctions.filterNot { it in pinnedScientific }).forEach { value ->
                    ScientificButton(
                        label = value,
                        darkMode = darkMode,
                        accentColor = accentColor,
                        isPinned = value in pinnedScientific,
                        onLongClick = {
                            pinnedScientific = if (value in pinnedScientific) {
                                pinnedScientific.filterNot { it == value }
                            } else {
                                listOf(value) + pinnedScientific
                            }
                            preferences.edit()
                                .putString(PINNED_KEY, pinnedScientific.joinToString(HISTORY_SEPARATOR))
                                .apply()
                        },
                        onClick = { scientific(value) }
                    )
                }
            }

            rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { value ->
                        val isOperator = value in listOf("+", "−", "×", "÷", "=")
                        CalculatorButton(
                            label = value,
                            buttonWeight = if (rowIndex == rows.lastIndex) 2f else 1f,
                            isOperator = isOperator,
                            darkMode = darkMode,
                            accentColor = accentColor,
                            onClick = { press(value) }
                        )
                    }
                }
            }
        } else if (selectedMode == CalculatorMode.GRAPHING) {
            GraphingPanel(
                darkMode = darkMode,
                accentColor = accentColor,
                useRadians = useRadians,
                pinnedScientific = pinnedScientific,
                onPinnedScientificChanged = { value ->
                    pinnedScientific = if (value in pinnedScientific) {
                        pinnedScientific.filterNot { it == value }
                    } else {
                        listOf(value) + pinnedScientific
                    }
                    preferences.edit()
                        .putString(PINNED_KEY, pinnedScientific.joinToString(HISTORY_SEPARATOR))
                        .apply()
                }
            )
        } else {
            ConverterPanel(darkMode = darkMode, accentColor = accentColor)
        }

            CalculatorBottomBar(selectedMode, darkMode, accentColor) { selectedMode = it }
        }
    }
}

private enum class CalculatorMode {
    CALCULATOR, GRAPHING, MEASUREMENTS
}

private data class ConverterCategory(
    val name: String,
    val units: List<String>
)

private val converterCategories = listOf(
    ConverterCategory("Length", listOf("Meters", "Kilometers", "Centimeters", "Feet", "Inches", "Miles")),
    ConverterCategory("Mass", listOf("Grams", "Kilograms", "Pounds", "Ounces")),
    ConverterCategory("Time", listOf("Seconds", "Minutes", "Hours", "Days")),
    ConverterCategory("Temperature", listOf("Celsius", "Fahrenheit", "Kelvin")),
    ConverterCategory("Area", listOf("Square meters", "Square kilometers", "Square feet", "Square miles")),
    ConverterCategory("Volume", listOf("Liters", "Milliliters", "Gallons", "Cubic meters"))
)

@Composable
private fun ColumnScope.ConverterPanel(
    darkMode: Boolean,
    accentColor: Color
) {
    val converterContext = LocalContext.current
    val converterPreferences = remember(converterContext) {
        converterContext.getSharedPreferences("calculator_preferences", Activity.MODE_PRIVATE)
    }
    val savedCategoryIndex = converterPreferences
        .getInt(CONVERTER_CATEGORY_KEY, 0)
        .coerceIn(converterCategories.indices)
    var categoryIndex by remember { mutableStateOf(savedCategoryIndex) }
    val initialCategory = converterCategories[categoryIndex]
    var fromIndex by remember {
        mutableStateOf(
            converterPreferences.getInt(CONVERTER_FROM_KEY, 0)
                .coerceIn(initialCategory.units.indices)
        )
    }
    var toIndex by remember {
        mutableStateOf(
            converterPreferences.getInt(CONVERTER_TO_KEY, 1)
                .coerceIn(initialCategory.units.indices)
        )
    }
    var input by remember {
        mutableStateOf(converterPreferences.getString(CONVERTER_INPUT_KEY, "0") ?: "0")
    }
    val category = converterCategories[categoryIndex]
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var openUnitMenu by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(categoryIndex, fromIndex, toIndex, input) {
        converterPreferences.edit()
            .putInt(CONVERTER_CATEGORY_KEY, categoryIndex)
            .putInt(CONVERTER_FROM_KEY, fromIndex)
            .putInt(CONVERTER_TO_KEY, toIndex)
            .putString(CONVERTER_INPUT_KEY, input)
            .apply()
    }
    val result = convertMeasurement(
        input.toDoubleOrNull() ?: 0.0,
        category.name,
        category.units[fromIndex],
        category.units[toIndex]
    )
    val resultText = result.formatNumber()

    fun resetCategory(newIndex: Int) {
        categoryIndex = newIndex
        fromIndex = 0
        toIndex = if (converterCategories[newIndex].units.size > 1) 1 else 0
        input = "0"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Bottom
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.zIndex(10f)) {
                Button(
                    onClick = {
                        openUnitMenu = null
                        categoryMenuExpanded = !categoryMenuExpanded
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text(category.name)
                }
                if (categoryMenuExpanded) {
                    Column(
                        modifier = Modifier
                            .offset(y = 48.dp)
                            .zIndex(100f)
                            .background(if (darkMode) Color(0xFF2A2A2A) else Color.White)
                            .padding(4.dp)
                    ) {
                        converterCategories.forEachIndexed { index, item ->
                            Text(
                                text = item.name,
                                color = if (darkMode) Color.White else Color(0xFF202124),
                                modifier = Modifier
                                    .clickable {
                                        resetCategory(index)
                                        categoryMenuExpanded = false
                                        openUnitMenu = null
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = resultText,
                    modifier = Modifier.weight(1f),
                    color = accentColor,
                    fontSize = converterFontSize(resultText, 42),
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ConverterUnitMenu(
                    label = category.units[toIndex],
                    units = category.units,
                    menuKey = "to",
                    openMenuKey = openUnitMenu,
                    onToggle = { key ->
                        categoryMenuExpanded = false
                        openUnitMenu = if (openUnitMenu == key) null else key
                    },
                    onSelected = { toIndex = it },
                    darkMode = darkMode,
                    accentColor = accentColor,
                    modifier = Modifier.width(140.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = input,
                    modifier = Modifier.weight(1f),
                    color = if (darkMode) Color.White else Color(0xFF202124),
                    fontSize = converterFontSize(input, 32),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ConverterUnitMenu(
                    label = category.units[fromIndex],
                    units = category.units,
                    menuKey = "from",
                    openMenuKey = openUnitMenu,
                    onToggle = { key ->
                        categoryMenuExpanded = false
                        openUnitMenu = if (openUnitMenu == key) null else key
                    },
                    onSelected = { fromIndex = it },
                    darkMode = darkMode,
                    accentColor = accentColor,
                    modifier = Modifier.width(140.dp)
                )
            }
        }

        val converterRows = listOf(
            listOf("C", "⌫", "÷", "×"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("0", ".", "(", ")")
        )
        converterRows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { value ->
                    val buttonWeight = if (rowIndex == converterRows.lastIndex) 2f else 1f
                    val isConverterButton = value in listOf("C", "⌫", "−", ".") || value.all { it.isDigit() }
                    if (isConverterButton) {
                        CalculatorButton(
                            label = value,
                            buttonWeight = buttonWeight,
                            isOperator = value == "−",
                            darkMode = darkMode,
                            accentColor = accentColor,
                            onClick = {
                                input = when (value) {
                                    "C" -> "0"
                                    "⌫" -> input.dropLast(1).ifEmpty { "0" }
                                    "−" -> if (input == "0") "−" else input + "−"
                                    "." -> if (input.contains(".")) input else "$input."
                                    else -> if (value.all { it.isDigit() }) {
                                        if (input == "0") value else input + value
                                    } else {
                                        input
                                    }
                                }
                            }
                        )
                    } else {
                        Spacer(
                            modifier = Modifier
                                .weight(buttonWeight)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ConverterUnitMenu(
    label: String,
    units: List<String>,
    menuKey: String,
    openMenuKey: String?,
    onToggle: (String) -> Unit,
    onSelected: (Int) -> Unit,
    darkMode: Boolean,
    accentColor: Color,
    modifier: Modifier
) {
    val menuExpanded = openMenuKey == menuKey
    Box(modifier = modifier.zIndex(10f)) {
        Button(
            onClick = { onToggle(menuKey) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (darkMode) accentColor.copy(alpha = 0.35f) else accentColor.copy(alpha = 0.15f),
                contentColor = if (darkMode) Color.White else accentColor
            )
        ) {
            Text(label, maxLines = 1, fontSize = 12.sp)
        }
        if (menuExpanded) {
            Column(
                modifier = Modifier
                    .offset(y = 48.dp)
                    .zIndex(100f)
                    .background(if (darkMode) Color(0xFF2A2A2A) else Color.White)
                    .padding(4.dp)
            ) {
                units.forEachIndexed { index, unit ->
                    Text(
                        text = unit,
                        color = if (darkMode) Color.White else Color(0xFF202124),
                        modifier = Modifier
                            .clickable {
                                onSelected(index)
                                onToggle(menuKey)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private fun converterFontSize(value: String, startingSize: Int): androidx.compose.ui.unit.TextUnit {
    return when {
        value.length <= 8 -> startingSize.sp
        value.length <= 12 -> (startingSize - 6).sp
        value.length <= 16 -> (startingSize - 12).sp
        value.length <= 22 -> (startingSize - 16).sp
        else -> 18.sp
    }
}

private fun convertMeasurement(value: Double, category: String, from: String, to: String): Double {
    if (from == to) return value
    if (category == "Temperature") {
        val celsius = when (from) {
            "Fahrenheit" -> (value - 32.0) * 5.0 / 9.0
            "Kelvin" -> value - 273.15
            else -> value
        }
        return when (to) {
            "Fahrenheit" -> celsius * 9.0 / 5.0 + 32.0
            "Kelvin" -> celsius + 273.15
            else -> celsius
        }
    }
    val factors = when (category) {
        "Length" -> mapOf("Meters" to 1.0, "Kilometers" to 1000.0, "Centimeters" to 0.01, "Feet" to 0.3048, "Inches" to 0.0254, "Miles" to 1609.344)
        "Mass" -> mapOf("Grams" to 0.001, "Kilograms" to 1.0, "Pounds" to 0.45359237, "Ounces" to 0.0283495)
        "Time" -> mapOf("Seconds" to 1.0, "Minutes" to 60.0, "Hours" to 3600.0, "Days" to 86400.0)
        "Area" -> mapOf("Square meters" to 1.0, "Square kilometers" to 1_000_000.0, "Square feet" to 0.092903, "Square miles" to 2_589_988.11)
        "Volume" -> mapOf("Liters" to 1.0, "Milliliters" to 0.001, "Gallons" to 3.785411784, "Cubic meters" to 1000.0)
        else -> emptyMap()
    }
    return value * (factors[from] ?: 1.0) / (factors[to] ?: 1.0)
}

@Composable
private fun ColumnScope.GraphingPanel(
    darkMode: Boolean,
    accentColor: Color,
    useRadians: Boolean,
    pinnedScientific: List<String>,
    onPinnedScientificChanged: (String) -> Unit
) {
    val graphContext = LocalContext.current
    val graphPreferences = remember(graphContext) {
        graphContext.getSharedPreferences("calculator_preferences", Activity.MODE_PRIVATE)
    }
    var graphEquations by remember {
        mutableStateOf(
            graphPreferences.getString(GRAPH_EQUATIONS_KEY, "y = x")
                .orEmpty()
                .split(HISTORY_SEPARATOR)
                .filter { it.isNotEmpty() }
                .ifEmpty { listOf("y = x") }
        )
    }
    var activeEquationIndex by remember { mutableStateOf(0) }
    var graphCursorPosition by remember { mutableStateOf("y = x".length) }
    var graphZoom by remember { mutableStateOf(1f) }
    var graphPan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(graphEquations) {
        graphPreferences.edit()
            .putString(GRAPH_EQUATIONS_KEY, graphEquations.joinToString(HISTORY_SEPARATOR))
            .apply()
    }
    val graphEquation = graphEquations[activeEquationIndex.coerceIn(0, graphEquations.lastIndex)]
    fun updateGraphEquation(index: Int, value: String) {
        graphEquations = graphEquations.toMutableList().also { it[index] = value }
    }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    fun insertGraphText(value: String) {
        val cursor = graphCursorPosition.coerceIn(0, graphEquation.length)
        updateGraphEquation(
            activeEquationIndex,
            graphEquation.substring(0, cursor) + value + graphEquation.substring(cursor)
        )
        graphCursorPosition = cursor + value.length
    }
    fun deleteGraphText() {
        val cursor = graphCursorPosition.coerceIn(0, graphEquation.length)
        if (cursor > 0) {
            updateGraphEquation(
                activeEquationIndex,
                graphEquation.removeRange(cursor - 1, cursor)
            )
            graphCursorPosition = cursor - 1
        }
    }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        graphZoom = (graphZoom * zoomChange).coerceIn(0.05f, 8f)
        graphPan += panChange
    }

    val graphRows = listOf(
        listOf("C", "⌫", "÷", "×"),
        listOf("7", "8", "9", "−"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf("0", ".", "(", ")")
    )
    val graphScientificFunctions = listOf(
        "sin", "cos", "tan", "asin", "acos", "atan",
        "sinh", "cosh", "tanh", "ln", "log", "exp", "1/x", "abs",
        "floor", "ceil", "%", "n!", "√", "∛", "x²", "x³", "eˣ", "^", "XrootY", "π", "e"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 80.dp, max = 220.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (darkMode) Color(0xFF1E1E1E) else Color.White)
                    .clipToBounds()
                    .transformable(transformState)
            ) {
            val xHalfRange = 10.0 / graphZoom
            val yHalfRange = 10.0 / graphZoom
            val xScale = size.width / (xHalfRange * 2.0).toFloat()
            val yScale = size.height / (yHalfRange * 2.0).toFloat()
            val xCenter = -graphPan.x / xScale
            val yCenter = graphPan.y / yScale
            val xMin = xCenter - xHalfRange
            val xMax = xCenter + xHalfRange
            val yMin = yCenter - yHalfRange
            val yMax = yCenter + yHalfRange
            val gridColor = if (darkMode) Color(0xFF444444) else Color(0xFFE0E0E0)
            val axisColor = if (darkMode) Color(0xFFAAAAAA) else Color(0xFF777777)
            val xTickStep = graphTickStep(xMax - xMin)
            val yTickStep = graphTickStep(yMax - yMin)

            var xTick = floor(xMin / xTickStep) * xTickStep
            while (xTick <= xMax) {
                val x = ((xTick - xMin) * xScale).toFloat()
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                xTick += xTickStep
            }
            var yTick = floor(yMin / yTickStep) * yTickStep
            while (yTick <= yMax) {
                val y = (size.height - (yTick - yMin) * yScale).toFloat()
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                yTick += yTickStep
            }

            val xAxis = ((0 - xMin) * xScale).toFloat()
            val yAxis = (size.height - (0 - yMin) * yScale).toFloat()
            drawLine(axisColor, Offset(xAxis, 0f), Offset(xAxis, size.height), 2f)
            drawLine(axisColor, Offset(0f, yAxis), Offset(size.width, yAxis), 2f)

            val labelPaint = android.graphics.Paint().apply {
                color = axisColor.toArgb()
                textSize = 22f
                isAntiAlias = true
            }
            drawIntoCanvas { canvas ->
                var labelX = floor(xMin / xTickStep) * xTickStep
                while (labelX <= xMax) {
                    val x = ((labelX - xMin) * xScale).toFloat()
                    canvas.nativeCanvas.drawText(
                        formatGraphTick(labelX),
                        x + 4f,
                        size.height - 6f,
                        labelPaint
                    )
                    labelX += xTickStep
                }
                var labelY = floor(yMin / yTickStep) * yTickStep
                while (labelY <= yMax) {
                    val y = (size.height - (labelY - yMin) * yScale).toFloat()
                    canvas.nativeCanvas.drawText(
                        formatGraphTick(labelY),
                        4f,
                        y - 4f,
                        labelPaint
                    )
                    labelY += yTickStep
                }
            }

            graphEquations.forEachIndexed { index, equationToDraw ->
                val graphPath = Path()
                val lineColor = if (index == activeEquationIndex) {
                    accentColor
                } else {
                    accentColor.copy(alpha = 0.45f)
                }
                var hasPoint = false
                var hasVisiblePoint = false
                var lastPoint = Offset.Zero
                for (pixel in 0..size.width.toInt()) {
                    val x = xMin + pixel.toDouble() / xScale
                    val y = evaluateGraphEquation(equationToDraw, x, useRadians)
                    if (y != null && y.isFinite() && y in yMin..yMax) {
                        val screenY = size.height - ((y - yMin) * yScale).toFloat()
                        if (!hasPoint) graphPath.moveTo(pixel.toFloat(), screenY) else graphPath.lineTo(pixel.toFloat(), screenY)
                        hasPoint = true
                        hasVisiblePoint = true
                        lastPoint = Offset(pixel.toFloat(), screenY)
                    } else {
                        hasPoint = false
                    }
                }
                drawPath(graphPath, lineColor, style = Stroke(width = 4f))
                val labelX = if (hasVisiblePoint) {
                    (lastPoint.x + 6f).coerceIn(4f, size.width - 48f)
                } else {
                    size.width - 48f
                }
                val labelY = if (hasVisiblePoint) {
                    (lastPoint.y - 6f).coerceIn(20f, size.height - 4f)
                } else {
                    (24f + index * 24f).coerceAtMost(size.height - 4f)
                }
                val lineLabelPaint = android.graphics.Paint().apply {
                    color = lineColor.toArgb()
                    textSize = 24f
                    isAntiAlias = true
                }
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        graphFunctionLabel(index),
                        labelX,
                        labelY,
                        lineLabelPaint
                    )
                }
            }
            }

            Button(
                onClick = { graphPan = Offset.Zero },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .height(34.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (darkMode) Color(0xFF333333) else Color.White,
                    contentColor = if (darkMode) Color.White else Color(0xFF333333)
                )
            ) {
                Text("0,0", fontSize = 12.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Line equations",
                    fontSize = 12.sp,
                    color = if (darkMode) Color(0xFFBDBDBD) else Color(0xFF666666)
                )
                Button(
                    onClick = {
                        graphEquations = graphEquations + ""
                        activeEquationIndex = graphEquations.lastIndex
                        graphCursorPosition = 0
                    },
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("+ line", fontSize = 12.sp)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                graphEquations.forEachIndexed { index, equationValue ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    BasicTextField(
                        value = TextFieldValue(
                            text = equationValue,
                            selection = TextRange(
                                if (index == activeEquationIndex) {
                                    graphCursorPosition.coerceIn(0, equationValue.length)
                                } else {
                                    equationValue.length
                                }
                            )
                        ),
                        onValueChange = {
                            activeEquationIndex = index
                            updateGraphEquation(index, it.text)
                            graphCursorPosition = it.selection.start.coerceIn(0, it.text.length)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (index == activeEquationIndex) {
                                    accentColor.copy(alpha = if (darkMode) 0.28f else 0.12f)
                                } else if (darkMode) {
                                    Color(0xFF2A2A2A)
                                } else {
                                    Color(0xFFF5F5F5)
                                }
                            )
                            .onFocusChanged {
                                if (it.isFocused) {
                                    activeEquationIndex = index
                                    softwareKeyboardController?.hide()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        singleLine = true,
                        readOnly = true,
                        cursorBrush = SolidColor(accentColor),
                        textStyle = TextStyle(
                            color = if (darkMode) Color.White else Color(0xFF202124),
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = graphFunctionLabel(index),
                        modifier = Modifier.width(52.dp),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            if (graphEquations.size > 1) {
                                graphEquations = graphEquations.toMutableList().also { it.removeAt(index) }
                                activeEquationIndex = activeEquationIndex.coerceIn(0, graphEquations.lastIndex)
                                graphCursorPosition = graphEquations[activeEquationIndex].length
                            } else {
                                updateGraphEquation(index, "")
                                activeEquationIndex = index
                                graphCursorPosition = 0
                            }
                        },
                        modifier = Modifier.width(44.dp).height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                    ) {
                        Text("×", color = Color.White, fontSize = 18.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = 3.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("x", "y", "a", "b", "m", "n").forEach { variable ->
                ScientificButton(
                    label = variable,
                    darkMode = darkMode,
                    accentColor = accentColor,
                    isPinned = false,
                    onLongClick = {},
                    onClick = { insertGraphText(variable) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (graphScientificFunctions.filter { it in pinnedScientific } +
                graphScientificFunctions.filterNot { it in pinnedScientific }).forEach { value ->
                ScientificButton(
                    label = value,
                    darkMode = darkMode,
                    accentColor = accentColor,
                    isPinned = value in pinnedScientific,
                    onLongClick = { onPinnedScientificChanged(value) },
                    onClick = {
                        val token = when (value) {
                            "π" -> "pi"
                            "x²" -> "square"
                            "x³" -> "cube"
                            "eˣ" -> "exp"
                            "√" -> "sqrt"
                            "∛" -> "cbrt"
                            "XrootY" -> "root"
                            "n!" -> "factorial"
                            "1/x" -> "inv"
                            "%" -> "percent"
                            else -> value
                        }
                        insertGraphText(if (value in listOf("^", "π", "e", "XrootY")) token else "$token(")
                    }
                )
            }
        }

        graphRows.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { value ->
                    CalculatorButton(
                        label = value,
                        buttonWeight = if (rowIndex == graphRows.lastIndex) 2f else 1f,
                        isOperator = value in listOf("+", "−", "×", "÷", "="),
                        darkMode = darkMode,
                        accentColor = accentColor,
                        onClick = {
                            when (value) {
                                "C" -> {
                                    updateGraphEquation(activeEquationIndex, "")
                                    graphCursorPosition = 0
                                }
                                "⌫" -> {
                                    deleteGraphText()
                                }
                                "=" -> {
                                    insertGraphText(" = ")
                                }
                                else -> {
                                    insertGraphText(value)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
}

private fun evaluateGraphEquation(equation: String, x: Double, useRadians: Boolean): Double? {
    return try {
        val rightSide = equation
            .substringAfter('=', equation)
            .trim()
            .replace("x²", "($x)^2", ignoreCase = true)
            .replace("x³", "($x)^3", ignoreCase = true)
            .replace(Regex("(?i)(?<![A-Za-z])x(?![A-Za-z])")) { "($x)" }
        evaluateExpression(balanceParentheses(rightSide), useRadians)
    } catch (_: Exception) {
        null
    }
}

private fun graphFunctionLabel(index: Int): String {
    return if (index < 21) {
        "${('f'.code + index).toChar()}(x)"
    } else {
        "f${index + 1}(x)"
    }
}

private fun graphTickStep(range: Double): Double {
    val rawStep = range / 10.0
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val normalizedStep = rawStep / magnitude
    val multiplier = when {
        normalizedStep <= 1.0 -> 1.0
        normalizedStep <= 2.0 -> 2.0
        normalizedStep <= 5.0 -> 5.0
        else -> 10.0
    }
    return multiplier * magnitude
}

private fun formatGraphTick(value: Double): String {
    val rounded = if (abs(value) < 1e-9) 0.0 else value
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        "%.2f".format(rounded)
    }
}

@Composable
private fun CalculatorBottomBar(
    selectedMode: CalculatorMode,
    darkMode: Boolean,
    accentColor: Color,
    onModeSelected: (CalculatorMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModeButton(
            label = "Calculator",
            selected = selectedMode == CalculatorMode.CALCULATOR,
            darkMode = darkMode,
            accentColor = accentColor,
            onClick = { onModeSelected(CalculatorMode.CALCULATOR) }
        )
        ModeButton(
            label = "Graphing",
            selected = selectedMode == CalculatorMode.GRAPHING,
            darkMode = darkMode,
            accentColor = accentColor,
            onClick = { onModeSelected(CalculatorMode.GRAPHING) }
        )
        ModeButton(
            label = "Convert",
            selected = selectedMode == CalculatorMode.MEASUREMENTS,
            darkMode = darkMode,
            accentColor = accentColor,
            onClick = { onModeSelected(CalculatorMode.MEASUREMENTS) }
        )
    }
}

@Composable
private fun SettingsDrawerContent(
    onClose: () -> Unit,
    onClearHistory: () -> Unit,
    darkMode: Boolean,
    onDarkModeChanged: (Boolean) -> Unit,
    accentColor: Color,
    onAccentColorChanged: (Color) -> Unit
) {
    val drawerBackground = if (darkMode) Color(0xFF1E1E1E) else Color.White
    val drawerText = if (darkMode) Color(0xFFEAEAEA) else Color(0xFF202124)

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(drawerBackground)
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = drawerText
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Dark mode", color = drawerText)
            Switch(
                checked = darkMode,
                onCheckedChange = onDarkModeChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = accentColor,
                    uncheckedTrackColor = accentColor.copy(alpha = 0.25f)
                )
            )
        }
        Text(
            text = "Button color",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            fontWeight = FontWeight.Bold,
            color = drawerText
        )
        val colorOptions = listOf(
            "Blue" to Color(0xFF5B5FEF),
            "Red" to Color(0xFFD32F2F),
            "Green" to Color(0xFF43A047),
            "Orange" to Color(0xFFF57C00),
            "Purple" to Color(0xFF8E24AA),
            "Teal" to Color(0xFF00838F)
        )
        var colorMenuExpanded by remember { mutableStateOf(false) }
        val selectedColorName = colorOptions.firstOrNull { it.second == accentColor }?.first ?: "Blue"
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Button(
                onClick = { colorMenuExpanded = !colorMenuExpanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("$selectedColorName button")
            }
            if (colorMenuExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (darkMode) Color(0xFF2A2A2A) else Color(0xFFF0F0F0),
                            RoundedCornerShape(10.dp)
                        )
                ) {
                    colorOptions.forEach { (name, color) ->
                        Text(
                            text = name,
                            color = if (darkMode) Color.White else Color(0xFF202124),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAccentColorChanged(color)
                                    colorMenuExpanded = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        DropdownMenuItem(
                text = { Text("Clear history", color = drawerText) },
                    onClick = {
                        onClearHistory()
                    }
                )
    }
}

@Composable
private fun RowScope.ScientificButton(
    label: String,
    darkMode: Boolean,
    accentColor: Color,
    isPinned: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(48.dp)
            .background(
                if (isPinned) {
                    accentColor.copy(alpha = if (darkMode) 0.55f else 0.25f)
                } else if (darkMode) {
                    accentColor.copy(alpha = 0.35f)
                } else {
                    accentColor.copy(alpha = 0.15f)
                },
                RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (darkMode) Color.White else accentColor,
            fontSize = 13.sp,
            maxLines = 1
        )
        if (isPinned) {
            Text(
                text = "★",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp),
                color = if (darkMode) Color.White else accentColor,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RowScope.ModeButton(
    label: String,
    selected: Boolean,
    darkMode: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) accentColor else if (darkMode) Color(0xFF2A2A2A) else Color.White,
            contentColor = if (selected) Color.White else if (darkMode) Color(0xFFEAEAEA) else Color(0xFF202124)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun RowScope.CalculatorButton(
    label: String,
    buttonWeight: Float,
    isOperator: Boolean,
    darkMode: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .weight(buttonWeight)
            .fillMaxHeight()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isOperator) accentColor else if (darkMode) Color(0xFF2A2A2A) else Color.White,
            contentColor = if (isOperator) Color.White else if (darkMode) Color(0xFFEAEAEA) else Color(0xFF202124)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
    ) {
        Text(text = label, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
}

private fun Double.formatNumber(): String {
    return if (this % 1.0 == 0.0) toLong().toString() else toString()
}

private const val MAX_DIGITS = 15
private const val HISTORY_KEY = "calculation_history"
private const val HISTORY_SEPARATOR = "\u001F"
private const val GRAPH_EQUATIONS_KEY = "graph_equations"
private const val CONVERTER_CATEGORY_KEY = "converter_category"
private const val CONVERTER_FROM_KEY = "converter_from_unit"
private const val CONVERTER_TO_KEY = "converter_to_unit"
private const val CONVERTER_INPUT_KEY = "converter_input"

private fun removeLastInputToken(expression: String): String {
    val functionTokens = listOf(
        "factorial(", "percent(", "square(", "cube(", "#root#(", "sqrt(", "cbrt(",
        "sinh(", "cosh(", "tanh(", "asin(", "acos(", "atan(", "root",
        "sin(", "cos(", "tan(", "floor(", "ceil(", "exp(", "log(",
        "ln(", "abs(", "inv(", "pi"
    )
    val token = functionTokens.firstOrNull { expression.endsWith(it) }
    return if (token != null) expression.dropLast(token.length) else expression.dropLast(1)
}

private fun balanceParentheses(expression: String): String {
    val openingCount = expression.count { it == '(' }
    val closingCount = expression.count { it == ')' }
    return when {
        openingCount > closingCount -> expression + ")".repeat(openingCount - closingCount)
        closingCount > openingCount -> "(".repeat(closingCount - openingCount) + expression
        else -> expression
    }
}

private fun evaluateExpression(expression: String, useRadians: Boolean): Double {
    val customRootPattern = Regex("(\\d+(?:\\.\\d+)?)root(\\d+(?:\\.\\d+)?)")
    val normalizedRootExpression = customRootPattern.replace(expression) {
        "root(${it.groupValues[1]},${it.groupValues[2]})"
    }
    val parser = ExpressionParser(
        normalizedRootExpression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-'),
        useRadians
    )
    val result = parser.parseExpression()
    if (!parser.isAtEnd()) throw IllegalArgumentException("Unexpected input")
    if (!result.isFinite()) throw IllegalArgumentException("Invalid result")
    return result
}

private class ExpressionParser(
    private val source: String,
    private val useRadians: Boolean
) {
    private var position = 0

    fun parseExpression(): Double {
        var result = parseTerm()
        while (true) {
            result = when {
                match('+') -> result + parseTerm()
                match('-') -> result - parseTerm()
                else -> return result
            }
        }
    }

    private fun parseTerm(): Double {
        var result = parsePower()
        while (true) {
            result = when {
                match('*') -> result * parsePower()
                match('/') -> {
                    val divisor = parsePower()
                    if (divisor == 0.0) throw IllegalArgumentException("Division by zero")
                    result / divisor
                }
                nextIs('(') -> result * parsePower()
                nextStartsFactor() -> result * parsePower()
                else -> return result
            }
        }
    }

    private fun parsePower(): Double {
        val base = parseFactor()
        return if (match('^')) base.pow(parsePower()) else base
    }

    private fun parseFactor(): Double {
        skipSpaces()
        if (match('+')) return parseFactor()
        if (match('-')) return -parseFactor()
        if (match('(')) {
            val result = parseExpression()
            if (!match(')')) throw IllegalArgumentException("Missing parenthesis")
            return result
        }

        if (position < source.length && source[position].isLetter()) {
            val start = position
            while (position < source.length && source[position].isLetter()) position++
            val name = source.substring(start, position)
            if (name == "pi") return Math.PI
            if (name == "e") return Math.E
            if (name == "root") {
                if (!match('(')) throw IllegalArgumentException("Missing parenthesis")
                val degree = parseExpression()
                if (!match(',')) throw IllegalArgumentException("Root needs two values")
                val radicand = parseExpression()
                if (!match(')') || degree == 0.0) throw IllegalArgumentException("Invalid root")
                return radicand.pow(1.0 / degree)
            }
            if (name !in setOf("sin", "cos", "tan", "asin", "acos", "atan", "sinh", "cosh", "tanh", "ln", "log", "exp", "root", "sqrt", "cbrt", "square", "cube", "inv", "abs", "floor", "ceil", "percent", "factorial")) {
                throw IllegalArgumentException("Unknown function")
            }
            if (!match('(')) throw IllegalArgumentException("Missing parenthesis")
            val argument = parseExpression()
            if (!match(')')) throw IllegalArgumentException("Missing parenthesis")
            return applyFunction(name, argument, useRadians)
        }

        val start = position
        while (position < source.length && (source[position].isDigit() || source[position] == '.')) {
            position++
        }
        if (start == position) throw IllegalArgumentException("Expected number")
        return source.substring(start, position).toDoubleOrNull()
            ?: throw IllegalArgumentException("Invalid number")
    }

    fun isAtEnd(): Boolean {
        skipSpaces()
        return position == source.length
    }

    private fun match(expected: Char): Boolean {
        skipSpaces()
        if (position < source.length && source[position] == expected) {
            position++
            return true
        }
        return false
    }

    private fun nextIs(expected: Char): Boolean {
        skipSpaces()
        return position < source.length && source[position] == expected
    }

    private fun nextStartsFactor(): Boolean {
        skipSpaces()
        return position < source.length && (
            source[position].isLetter() ||
                source[position].isDigit() ||
                source[position] == '.'
            )
    }

    private fun skipSpaces() {
        while (position < source.length && source[position].isWhitespace()) position++
    }
}

private fun applyFunction(name: String, number: Double, useRadians: Boolean): Double {
    return when (name) {
        "sin" -> sin(if (useRadians) number else Math.toRadians(number))
        "cos" -> cos(if (useRadians) number else Math.toRadians(number))
        "tan" -> tan(if (useRadians) number else Math.toRadians(number))
        "asin" -> if (number in -1.0..1.0) {
            val result = asin(number)
            if (useRadians) result else Math.toDegrees(result)
        } else Double.NaN
        "acos" -> if (number in -1.0..1.0) {
            val result = acos(number)
            if (useRadians) result else Math.toDegrees(result)
        } else Double.NaN
        "atan" -> {
            val result = atan(number)
            if (useRadians) result else Math.toDegrees(result)
        }
        "sinh" -> sinh(number)
        "cosh" -> cosh(number)
        "tanh" -> tanh(number)
        "ln" -> if (number > 0) ln(number) else Double.NaN
        "log" -> if (number > 0) log10(number) else Double.NaN
        "exp" -> exp(number)
        "sqrt" -> if (number >= 0) sqrt(number) else Double.NaN
        "root" -> if (number >= 0) sqrt(number) else Double.NaN
        "cbrt" -> cbrt(number)
        "square" -> number.pow(2)
        "cube" -> number.pow(3)
        "inv" -> if (number != 0.0) 1.0 / number else Double.NaN
        "abs" -> abs(number)
        "floor" -> floor(number)
        "ceil" -> ceil(number)
        "percent" -> number / 100.0
        "factorial" -> factorial(number)
        else -> Double.NaN
    }
}

private fun factorial(number: Double): Double {
    if (number < 0 || number % 1.0 != 0.0 || number > 170) return Double.NaN
    var result = 1.0
    for (value in 2..number.toInt()) result *= value
    return result
}
