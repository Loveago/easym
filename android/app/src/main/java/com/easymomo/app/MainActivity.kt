package com.easymomo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.ui.auth.AuthScreen
import com.easymomo.app.ui.auth.AuthViewModel
import com.easymomo.app.ui.auth.OnboardingScreen
import com.easymomo.app.ui.auth.SplashScreen
import com.easymomo.app.ui.customussd.CustomUssdHubScreen
import com.easymomo.app.ui.customussd.CustomUssdViewModel
import com.easymomo.app.ui.history.HistoryReportsViewModel
import com.easymomo.app.ui.history.HistoryScreen
import com.easymomo.app.ui.history.ReportsScreen
import com.easymomo.app.ui.settings.SimAndDiagnosticsScreen
import com.easymomo.app.ui.subscription.SubscriptionScreen
import com.easymomo.app.ui.terminal.CustomUssdScreen
import com.easymomo.app.ui.terminal.CustomerLedCashOutScreen
import com.easymomo.app.ui.terminal.CustomersScreen
import com.easymomo.app.ui.terminal.DashboardScreen
import com.easymomo.app.ui.terminal.DataBundlesScreen
import com.easymomo.app.ui.terminal.HelpSetupScreen
import com.easymomo.app.ui.terminal.PayMerchantScreen
import com.easymomo.app.ui.terminal.TerminalViewModel
import com.easymomo.app.ui.terminal.TransactionResultScreen
import com.easymomo.app.ui.terminal.TransactionReviewScreen
import com.easymomo.app.ui.terminal.UssdDevConfigScreen
import com.easymomo.app.ui.terminal.UssdProcessingScreen
import com.easymomo.app.ui.theme.EasyMoMoTheme

class MainActivity : ComponentActivity() {

    private var authViewModelRef: AuthViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as EasyMoMoApplication

        setContent {
            EasyMoMoTheme {
                val authVm: AuthViewModel = viewModel(
                    factory = AuthViewModel.Factory(app.authRepository)
                )
                val terminalVm: TerminalViewModel = viewModel(
                    factory = TerminalViewModel.Factory(
                        app.transactionRepository,
                        app.authRepository,
                        app.ussdLauncher,
                        app.simManager,
                        app.permissionManager,
                        app.customerSyncManager
                    )
                )
                val historyVm: HistoryReportsViewModel = viewModel(
                    factory = HistoryReportsViewModel.Factory(app.transactionRepository)
                )
                val customUssdVm: CustomUssdViewModel = viewModel(
                    factory = CustomUssdViewModel.Factory(
                        app.customUssdRepository,
                        app.simManager,
                        app.ussdLauncher,
                        app.permissionManager
                    )
                )
                authViewModelRef = authVm

                EasyMoMoNavGraph(
                    authViewModel = authVm,
                    terminalViewModel = terminalVm,
                    historyReportsViewModel = historyVm,
                    customUssdViewModel = customUssdVm
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (data != null && data.scheme == "easymomo" && data.host == "payment") {
            val ref = data.getQueryParameter("reference") ?: data.getQueryParameter("trxref")
            authViewModelRef?.verifyPendingOrRefresh(ref)
        }
    }
}

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val AUTH = "auth"
    const val DASHBOARD = "dashboard"
    const val SUBSCRIPTION = "subscription"
    const val REVIEW = "review"
    const val PROCESSING = "processing"
    const val CUSTOMER_LED = "customer_led"
    const val RESULT = "result"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val DATA_BUNDLES = "data_bundles/{network}"
    const val CUSTOM_USSD = "custom_ussd"
    const val MERCHANT = "merchant"
    const val CUSTOMERS = "customers"
    const val HELP = "help"
    const val USSD_DEV = "ussd_dev"
    const val SIM_DIAGNOSTICS = "sim_diagnostics"
}

@Composable
private fun EasyMoMoNavGraph(
    authViewModel: AuthViewModel,
    terminalViewModel: TerminalViewModel,
    historyReportsViewModel: HistoryReportsViewModel,
    customUssdViewModel: CustomUssdViewModel
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                authViewModel = authViewModel,
                onNavigateToOnboarding = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToDashboard = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onContinue = {
                    authViewModel.completeOnboarding()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.AUTH) {
            AuthScreen(
                authViewModel = authViewModel,
                onAuthenticated = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.DASHBOARD) {
            DashboardScreen(
                terminalViewModel = terminalViewModel,
                onOpenHelp = { navController.navigate(Routes.HELP) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenCustomers = { navController.navigate(Routes.CUSTOMERS) },
                onOpenMerchant = { navController.navigate(Routes.MERCHANT) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                onOpenUssdDevMode = { navController.navigate(Routes.USSD_DEV) },
                onOpenSimAndDiagnostics = { navController.navigate(Routes.SIM_DIAGNOSTICS) },
                onOpenDataBundles = { net -> navController.navigate("data_bundles/${net.code}") },
                onOpenCustomUssd = { navController.navigate(Routes.CUSTOM_USSD) },
                onNavigateToReview = { _, _ -> navController.navigate(Routes.REVIEW) },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate(Routes.AUTH) {
                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.SUBSCRIPTION) {
            SubscriptionScreen(
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REVIEW) {
            TransactionReviewScreen(
                terminalViewModel = terminalViewModel,
                onConfirmAgentUssd = { localId ->
                    terminalViewModel.executeAgentUssd(localId)
                    navController.navigate(Routes.PROCESSING) {
                        popUpTo(Routes.REVIEW) { inclusive = true }
                    }
                },
                onConfirmCustomerLed = { _ ->
                    navController.navigate(Routes.CUSTOMER_LED) {
                        popUpTo(Routes.REVIEW) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.PROCESSING) {
            UssdProcessingScreen(
                terminalViewModel = terminalViewModel,
                onOpenUssdConfig = { navController.navigate(Routes.USSD_DEV) },
                onNavigateToResult = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.PROCESSING) { inclusive = true }
                    }
                },
                onCancelBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CUSTOMER_LED) {
            CustomerLedCashOutScreen(
                terminalViewModel = terminalViewModel,
                onNavigateToResult = {
                    navController.navigate(Routes.RESULT) {
                        popUpTo(Routes.CUSTOMER_LED) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.RESULT) {
            TransactionResultScreen(
                terminalViewModel = terminalViewModel,
                onReturnToDashboard = {
                    navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                },
                onViewHistory = {
                    navController.navigate(Routes.HISTORY) {
                        popUpTo(Routes.DASHBOARD) { inclusive = false }
                    }
                },
                onStartAnother = {
                    navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                viewModel = historyReportsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                viewModel = historyReportsViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DATA_BUNDLES,
            arguments = listOf(navArgument("network") { type = NavType.StringType })
        ) { backStack ->
            val netCode = backStack.arguments?.getString("network")
            DataBundlesScreen(
                initialNetwork = NetworkType.fromCode(netCode),
                terminalViewModel = terminalViewModel,
                onSelectBundleAndReview = { _, _ -> navController.navigate(Routes.REVIEW) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CUSTOM_USSD) {
            CustomUssdHubScreen(
                viewModel = customUssdViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.MERCHANT) {
            PayMerchantScreen(
                terminalViewModel = terminalViewModel,
                onNavigateToReview = { _, _ -> navController.navigate(Routes.REVIEW) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CUSTOMERS) {
            CustomersScreen(
                terminalViewModel = terminalViewModel,
                onPickCustomer = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HELP) {
            HelpSetupScreen(
                terminalViewModel = terminalViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.USSD_DEV) {
            UssdDevConfigScreen(
                terminalViewModel = terminalViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SIM_DIAGNOSTICS) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val app = ctx.applicationContext as EasyMoMoApplication
            val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
            ) {
                terminalViewModel.refreshDeviceStatus()
            }
            SimAndDiagnosticsScreen(
                terminalViewModel = terminalViewModel,
                onRequestPermissions = {
                    val perms = app.permissionManager.requiredUssdPermissions()
                    app.permissionManager.markPermissionRequested(perms)
                    permLauncher.launch(perms)
                },
                onOpenAccessibilitySettings = {
                    ctx.startActivity(app.permissionManager.createAccessibilitySettingsIntent())
                },
                onOpenAppSettings = {
                    ctx.startActivity(app.permissionManager.createAppSettingsIntent())
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
