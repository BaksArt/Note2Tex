package com.baksart.note2tex

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.baksart.note2tex.domain.model.AppLanguage
import com.baksart.note2tex.domain.model.AppTheme
import com.baksart.note2tex.presentation.viewmodel.AuthViewModel
import com.baksart.note2tex.ui.auth.ResetNewPasswordScreen
import com.baksart.note2tex.ui.auth.ResetPasswordScreen
import com.baksart.note2tex.ui.auth.SignInScreen
import com.baksart.note2tex.ui.auth.SignUpScreen
import com.baksart.note2tex.ui.auth.VerifyEmailScreen
import com.baksart.note2tex.ui.main.MainScreen
import com.baksart.note2tex.ui.ocr.OcrScreen
import com.baksart.note2tex.ui.theme.Note2TexTheme
import com.baksart.note2tex.presentation.viewmodel.SettingsViewModel
import com.baksart.note2tex.ui.main.SubscribeScreen

object Routes {

    const val Splash = "splash"
    const val SignIn = "signIn"
    const val SignUp = "signUp"
    const val Reset = "reset"
    const val ResetConfirm = "resetConfirm"
    const val Verify = "verify"
    const val Main = "main"
    const val Recognize = "recognize"
    const val Ocr = "ocr"
    const val Subscribe = "subscribe"
}

@Composable
fun Note2TexApp(
    initialIntent: Intent?,
    vmSettings: SettingsViewModel = viewModel(),   // <-- ViewModel с темой
) {
    val nav = rememberNavController()
    val vm: AuthViewModel = viewModel()

    // читаем выбранную тему из SettingsViewModel
    val theme by vmSettings.theme.collectAsState()
    val useDark = when (theme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val language by vmSettings.language.collectAsState()

    LaunchedEffect(language) {
        val locales = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.RU     -> LocaleListCompat.forLanguageTags("ru")
            AppLanguage.EN     -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    LaunchedEffect(initialIntent) {
        initialIntent?.let { nav.handleDeepLink(it) }
    }

    Note2TexTheme(useDarkTheme = useDark) {
        NavHost(navController = nav, startDestination = Routes.Splash) {

            composable(Routes.Splash) {
                LaunchedEffect(Unit) {
                    vm.checkAuth(
                        onAuthed = {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.Splash) { inclusive = true }
                            }
                        },
                        onUnauthed = {
                            nav.navigate(Routes.SignIn) {
                                popUpTo(Routes.Splash) { inclusive = true }
                            }
                        }
                    )
                }
            }

            composable(Routes.SignIn) {
                SignInScreen(
                    onSignIn = { login, pass ->
                        vm.login(login, pass) {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.SignIn) { inclusive = true }
                            }
                        }
                    },
                    onGoSignUp = { nav.navigate(Routes.SignUp) },
                    onGoReset = { nav.navigate(Routes.Reset) },
                    loadingState = vm.state,
                    onMessageConsumed = vm::consumeMessage
                )
            }

            composable(Routes.SignUp) {
                SignUpScreen(
                    onRegistered = { email, username, pass ->
                        vm.register(email, username, pass) {
                            nav.navigate(Routes.Verify + "?email=${email}") {
                                popUpTo(Routes.SignUp) { inclusive = true }
                            }
                        }
                    },
                    onGoSignIn = { nav.navigate(Routes.SignIn) },
                    loadingState = vm.state,
                    onMessageConsumed = vm::consumeMessage
                )
            }

            composable(Routes.Reset) {
                ResetPasswordScreen(
                    onSend = { email -> vm.forgot(email) },
                    loadingState = vm.state,
                    onMessageConsumed = vm::consumeMessage
                )
            }
            composable(
                route = Routes.Verify + "?accessToken={accessToken}&email={email}",
                arguments = listOf(
                    navArgument("accessToken") { type = NavType.StringType; defaultValue = "" },
                    navArgument("email")       { type = NavType.StringType; defaultValue = "" }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "note2tex://auth/verify?accessToken={accessToken}" }
                )
            ) { entry ->
                val token = entry.arguments?.getString("accessToken").orEmpty()
                val email = entry.arguments?.getString("email").orEmpty()
                VerifyEmailScreen(
                    accessToken = token,
                    email = email,
                    loadingState = vm.state,
                    onAccept = { accessToken ->
                        vm.acceptAccessTokenFromDeepLink(accessToken) {
                            nav.navigate(Routes.Main) {
                                popUpTo(Routes.SignIn) { inclusive = true }
                            }
                        }
                    },
                    onMessageConsumed = vm::consumeMessage
                )
            }


            composable(
                route = Routes.ResetConfirm + "?token={token}",
                arguments = listOf(
                    navArgument("token") { type = NavType.StringType; defaultValue = "" }
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "note2tex://auth/reset-ok?token={token}" }
                )
            ) { entry ->
                val token = entry.arguments?.getString("token").orEmpty()
                ResetNewPasswordScreen(
                    token = token,
                    onSubmit = { newPass ->
                        vm.reset(token, newPass) {
                            nav.navigate(Routes.SignIn) { popUpTo(0) }
                        }
                    },
                    loadingState = vm.state,
                    onMessageConsumed = vm::consumeMessage
                )
            }

            composable(Routes.Main) {
                MainScreen(
                    onCreateProject = { /* опционально */ },
                    onImportReady = { uri ->
                        val encoded = android.net.Uri.encode(uri.toString())
                        nav.navigate("${Routes.Recognize}?uri=$encoded")
                    },
                    onOpenProject = { projectId ->
                        nav.navigate("ocrProject/$projectId")
                    },
                    onChangePassword = {
                        nav.navigate(Routes.Reset)
                    },
                    onLoggedOut = {
                        nav.navigate(Routes.SignIn) {
                            popUpTo(Routes.Main) { inclusive = true }
                        }
                    },
                    onOpenSubscription = { nav.navigate(Routes.Subscribe) },
                    vmSettings = vmSettings
                )
            }

            composable(
                route = Routes.Recognize + "?uri={uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val uriStr = entry.arguments?.getString("uri").orEmpty()
                val uri = android.net.Uri.parse(uriStr)
                com.baksart.note2tex.ui.imports.RecognizeScreen(
                    imageUri = uri,
                    onNext = { readyUri ->
                        val encoded = android.net.Uri.encode(readyUri.toString())
                        nav.navigate("${Routes.Ocr}?uri=$encoded")
                    }
                )
            }

            composable(
                route = Routes.Ocr + "?uri={uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                val uri = android.net.Uri.parse(entry.arguments?.getString("uri").orEmpty())
                com.baksart.note2tex.ui.ocr.OcrScreen(
                    imageUri = uri,
                    onExport = { /* TODO: переход к экспорту /сохранению проекта */ },
                    onBack = { nav.popBackStack() }
                )
            }

            composable(
                route = "ocrProject/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val pid = backStackEntry.arguments?.getString("projectId")!!
                // Открываем экран распознавания по ID проекта.
                // imageUri не передаём (null), только projectId
                OcrScreen(imageUri = null, projectId = pid, onBack = { nav.popBackStack() })
            }

            composable(Routes.Subscribe) {
                SubscribeScreen(onBack = { nav.popBackStack() })
            }
        }

        }
    }