@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.smsaggregator.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.smsaggregator.data.Transaction
import com.example.smsaggregator.logic.InsightCard
import com.example.smsaggregator.logic.InsightsEngine
import com.example.smsaggregator.logic.SmsParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat

// --- PREMIUM UI UTILITIES ---

@Composable
fun CountUpText(
    targetValue: Double,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    color: Color = Color.White,
    prefix: String = "₹"
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "CountUp"
    )

    Text(
        text = "$prefix${InsightsEngine.formatAmount(animatedValue.toDouble()).replace("₹", "").trim()}",
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        modifier = modifier
    )
}

@Composable
fun AnimatedEntrance(
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(50f) } // 50 pixels down

    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        alpha.animateTo(1f, animationSpec = tween(400))
        offsetY.animateTo(0f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            this.translationY = offsetY.value
        }
    ) {
        content()
    }
}

@Composable
fun ScaleOnClick(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ScaleOnClick"
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        content()
    }
}

// --- DESIGN TOKENS ---

// Refined Deep Navy Premium palette
private val DarkBg = Color(0xFF08090B)
private val SurfaceColor = Color(0xFF111418)
private val CardBg = Color(0xFF161A1F)
private val CardBg2 = Color(0xFF1E2329)
private val AccentBlue = Color(0xFF2E86DE)
private val AccentPurple = Color(0xFF8E44AD)
private val AccentTeal = Color(0xFF00D2D3)
private val AccentGreen = Color(0xFF1DD1A1)
private val AccentRed = Color(0xFFEE5253)
private val TextWhite = Color(0xFFF9FAFB)
private val TextGray = Color(0xFF909BA9)
private val InsightGradientStart = Color(0xFF121418)
private val InsightGradientEnd = Color(0xFF1E252D)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : FragmentActivity() {

    private val viewModel: TransactionViewModel by viewModels { TransactionViewModelFactory(application) }
    private var isUnlocked by mutableStateOf(false)
    private var lastActiveTime by mutableLongStateOf(0L)
    private val lockGracePeriod = 30_000L // 30 seconds

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
        val useBiometrics = prefs.getBoolean("biometric_lock", false)
        
        if (useBiometrics) {
            val now = System.currentTimeMillis()
            if (!isUnlocked || (lastActiveTime > 0 && (now - lastActiveTime) > lockGracePeriod)) {
                isUnlocked = false
                showBiometricPrompt()
            }
        } else {
            isUnlocked = true
        }
    }

    override fun onPause() {
        super.onPause()
        lastActiveTime = System.currentTimeMillis()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                viewModel.refreshSms()
            }
        }

    private val requestNotificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    private val signInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            viewModel.handleSignInResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        com.example.smsaggregator.util.NotificationHelper.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getSharedPreferences("sms_agg_prefs", Context.MODE_PRIVATE)
        val useBiometrics = prefs.getBoolean("biometric_lock", false)
        
        if (!useBiometrics) {
            isUnlocked = true
        }

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            com.example.smsaggregator.ui.theme.SmsAggregatorTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isUnlocked) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🔒", fontSize = 48.sp)
                                Spacer(Modifier.height(16.dp))
                                Text("App Locked", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { showBiometricPrompt() }) {
                                    Text("Unlock with Biometrics")
                                }
                            }
                        }
                    } else {
                        val authState by viewModel.authState.collectAsState()
                        var skippedLogin by remember { mutableStateOf(false) }
                        var signInError by remember { mutableStateOf<String?>(null) }

                        val safeSignIn = {
                            val intent = viewModel.getSignInIntent()
                            if (intent != null) {
                                signInLauncher.launch(intent)
                            } else {
                                signInError = "Firebase not configured. Please add google-services.json"
                            }
                        }

                        when {
                            authState == AuthState.LOADING -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentBlue)
                                }
                            }
                            authState == AuthState.SIGNED_IN || skippedLogin -> {
                                AppContent(viewModel, onSignIn = safeSignIn)
                            }
                            else -> {
                                LoginScreen(
                                    onGoogleSignIn = safeSignIn,
                                    onSkipLogin = {
                                        skippedLogin = true
                                        checkSmsPermission()
                                    },
                                    errorMessage = signInError
                                )
                            }
                        }
                    }
                }
            }
        }

        // If already signed in, scan SMS immediately
        if (viewModel.authState.value == AuthState.SIGNED_IN) {
            checkSmsPermission()
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or fails, we stay locked.
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Login")
            .setSubtitle("Unlock SMS Aggregator")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkSmsPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.refreshSms()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        }
    }
}

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Categories : Screen("categories")
    object Settings : Screen("settings")
    
    // Sub-screens
    data class AiReview(val report: AiReport) : Screen("ai_review")
    data class CategoryBreakdown(val title: String, val transactions: List<Transaction>) : Screen("breakdown")
    data class TransactionList(val title: String, val transactions: List<Transaction>, val parent: Screen) : Screen("list")
    data class TransactionDetail(val transaction: Transaction, val parent: Screen) : Screen("detail")
}

@Composable
fun LoginScreen(onGoogleSignIn: () -> Unit, onSkipLogin: () -> Unit, errorMessage: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F0F1A), Color(0xFF1A1A2E), Color(0xFF16213E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("💰", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("SMS Aggregator", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text("Your Privacy-First Financial Assistant", fontSize = 14.sp, color = TextGray, modifier = Modifier.padding(top = 4.dp, bottom = 48.dp))

            // Google Sign-In Button
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onGoogleSignIn() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("G", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4285F4))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F1F1F))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("☁️ Why sign in?", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Backup transactions to the cloud", fontSize = 13.sp, color = TextGray)
                    Text("• Restore data even if SMS are deleted", fontSize = 13.sp, color = TextGray)
                    Text("• Sync across device resets", fontSize = 13.sp, color = TextGray)
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723))
                ) {
                    Text("⚠️ $errorMessage", fontSize = 13.sp, color = Color(0xFFFFAB91), modifier = Modifier.padding(12.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            TextButton(onClick = onSkipLogin) {
                Text("Use Locally Without Sign-In →", color = TextGray, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun AppContent(viewModel: TransactionViewModel, onSignIn: () -> Unit = {}) {
    // Single entry point — M3AppContent handles all navigation now
    M3AppContent(viewModel = viewModel, onSignIn = onSignIn)
}
