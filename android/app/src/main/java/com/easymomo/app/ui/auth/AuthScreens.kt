package com.easymomo.app.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import com.easymomo.app.BuildConfig
import com.easymomo.app.R
import com.easymomo.app.ui.theme.EasyMoMoCard
import com.easymomo.app.ui.theme.EmeraldPrimary
import com.easymomo.app.ui.theme.EmeraldPrimaryContainer
import com.easymomo.app.ui.theme.StatusError
import com.easymomo.app.ui.theme.StatusErrorBg
import com.easymomo.app.ui.theme.TerminalActionButton
import com.easymomo.app.ui.theme.TerminalBackground
import com.easymomo.app.ui.theme.TextPrimary
import com.easymomo.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(650)
        when {
            authViewModel.isLoggedIn() -> onNavigateToDashboard()
            !authViewModel.isOnboardingCompleted() -> onNavigateToOnboarding()
            else -> onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldPrimary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.easymomo_app_logo),
                contentDescription = "EasyMoMo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "EasyMoMo",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ghana Multi-Network Agent Terminal",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    onContinue: () -> Unit
) {
    Scaffold(containerColor = TerminalBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Welcome to EasyMoMo",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Built for Ghanaian mobile money agents managing MTN MoMo, AT Money, and Telecel Cash.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))

                OnboardingFeatureCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "Multi-Network Agent Terminal",
                    body = "Auto-detect networks from phone prefixes, run verified USSD workflows, and guide customer-led AT & Telecel cash-outs."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OnboardingFeatureCard(
                    icon = Icons.Outlined.Shield,
                    title = "Offline-First Records & Reports",
                    body = "Every transaction is saved locally first with daily, weekly, and monthly summaries plus CSV/JSON export."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OnboardingFeatureCard(
                    icon = Icons.Outlined.Lock,
                    title = "PIN-Protected USSD Automation",
                    body = "EasyMoMo navigates verified USSD menus and always pauses so you enter your MoMo PIN privately."
                )
            }

            Column {
                Spacer(modifier = Modifier.height(24.dp))
                TerminalActionButton(
                    label = "Continue to Agent Sign In",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun OnboardingFeatureCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(EmeraldPrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = EmeraldPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val activityContext = remember(context) { context.findActivity() ?: context }
    val scope = rememberCoroutineScope()
    val uiState by authViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        authViewModel.loadGoogleAuthConfig()
    }

    val launchGoogleSignIn: () -> Unit = {
        val clientId = uiState.googleClientId.trim()
        if (clientId.isBlank()) {
            authViewModel.setError("Google Sign-In is not configured by the administrator yet.")
        } else if (!isGoogleSigningIn && !uiState.isLoading) {
            isGoogleSigningIn = true
            authViewModel.clearError()
            scope.launch {
                try {
                    val credentialManager = CredentialManager.create(activityContext)
                    val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
                    val primaryRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(signInOption)
                        .build()
                    val result = try {
                        credentialManager.getCredential(
                            context = activityContext,
                            request = primaryRequest
                        )
                    } catch (_: NoCredentialException) {
                        val fallbackOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(clientId)
                            .setAutoSelectEnabled(false)
                            .build()
                        val fallbackRequest = GetCredentialRequest.Builder()
                            .addCredentialOption(fallbackOption)
                            .build()
                        credentialManager.getCredential(
                            context = activityContext,
                            request = fallbackRequest
                        )
                    }
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                        authViewModel.signInWithGoogle(googleCred.idToken, onAuthenticated)
                    } else {
                        authViewModel.setError("Unexpected Google credential response (${credential.type}).")
                    }
                } catch (e: GetCredentialCancellationException) {
                    Log.w("EasyMoMoAuth", "Google Sign-In cancelled: ${e.message}", e)
                    val detail = e.message?.takeIf { it.isNotBlank() }
                    authViewModel.setError(
                        if (detail != null) {
                            "Google Sign-In cancelled ($detail). If you selected an account and nothing happened, publish your Google Cloud OAuth Consent Screen (or add your Gmail as a Test User) and verify the APK SHA-1."
                        } else {
                            "Google Sign-In did not complete. If you selected an account and the popup closed, publish your Google Cloud OAuth Consent Screen (or add your Gmail as a Test User) and verify the APK SHA-1."
                        }
                    )
                } catch (e: NoCredentialException) {
                    Log.w("EasyMoMoAuth", "Google NoCredentialException: ${e.message}", e)
                    authViewModel.setError(
                        "Google Sign-In unavailable on this build. Check that a Google account is signed in on the phone and this app's SHA-1 is added in Google Cloud Console."
                    )
                } catch (e: Exception) {
                    Log.e("EasyMoMoAuth", "Google Sign-In error", e)
                    val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
                    authViewModel.setError("Google Sign-In failed: $detail")
                } finally {
                    isGoogleSigningIn = false
                }
            }
        }
    }

    // Login state
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }

    // Register state
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirm by remember { mutableStateOf("") }
    var showRegPassword by remember { mutableStateOf(false) }

    Scaffold(containerColor = TerminalBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "EasyMoMo",
                style = MaterialTheme.typography.headlineMedium,
                color = EmeraldPrimary
            )
            Text(
                text = "Agent Terminal Access",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; authViewModel.clearError() },
                    text = { Text("Sign In") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; authViewModel.clearError() },
                    text = { Text("Create Account") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusErrorBg)
                        .padding(12.dp)
                ) {
                    Text(text = err, color = StatusError, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (selectedTab == 0) {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = loginIdentifier,
                        onValueChange = { loginIdentifier = it },
                        label = { Text("Email or Phone Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                                Icon(
                                    imageVector = if (showLoginPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showForgotDialog = true }) {
                            Text("Forgot password?")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TerminalActionButton(
                        label = if (uiState.isLoading) "Signing In..." else "Sign In",
                        enabled = !uiState.isLoading,
                        onClick = {
                            authViewModel.login(loginIdentifier, loginPassword, onAuthenticated)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.googleSignInEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text("OR", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = launchGoogleSignIn,
                            enabled = !uiState.isLoading && !isGoogleSigningIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isGoogleSigningIn || uiState.isLoading) "Signing in with Google..." else "Continue with Google",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                if (authViewModel.loginAsDebugGuest()) {
                                    onAuthenticated()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue as Guest (Debug)")
                        }
                    }
                }
            } else {
                EasyMoMoCard(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number (e.g. 0244123456)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = { regPassword = it },
                        label = { Text("Password (min 8 chars)") },
                        singleLine = true,
                        visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showRegPassword = !showRegPassword }) {
                                Icon(
                                    imageVector = if (showRegPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = regConfirm,
                        onValueChange = { regConfirm = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = if (showRegPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TerminalActionButton(
                        label = if (uiState.isLoading) "Creating Account..." else "Create Account",
                        enabled = !uiState.isLoading,
                        onClick = {
                            authViewModel.register(
                                fullName = fullName,
                                phone = phone,
                                email = email,
                                password = regPassword,
                                confirmPassword = regConfirm,
                                onSuccess = onAuthenticated
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.googleSignInEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f))
                            Text("OR", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            HorizontalDivider(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = launchGoogleSignIn,
                            enabled = !uiState.isLoading && !isGoogleSigningIn,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isGoogleSigningIn || uiState.isLoading) "Signing in with Google..." else "Sign up with Google",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Password Recovery") },
            text = { Text("Contact EasyMoMo support or your administrator to reset your agent credentials.") },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("OK") }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
