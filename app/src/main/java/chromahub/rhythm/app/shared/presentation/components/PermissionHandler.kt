/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import chromahub.rhythm.app.shared.presentation.components.icons.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import chromahub.rhythm.app.shared.data.model.AppSettings
import chromahub.rhythm.app.shared.presentation.components.common.InitializationLoader
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.OnboardingStep
import chromahub.rhythm.app.features.local.presentation.screens.onboarding.PermissionScreenState
import chromahub.rhythm.app.features.local.presentation.screens.OnboardingScreen
import chromahub.rhythm.app.shared.presentation.viewmodel.ThemeViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import chromahub.rhythm.app.features.local.presentation.viewmodel.MusicViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.AppUpdaterViewModel
import chromahub.rhythm.app.shared.presentation.viewmodel.rememberAppUpdaterViewModel
import chromahub.rhythm.app.features.streaming.presentation.viewmodel.StreamingMusicViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(
    onPermissionsGranted: @Composable () -> Unit,
    themeViewModel: ThemeViewModel,
    appSettings: AppSettings,
    isLoading: Boolean,
    isInitializingApp: Boolean,
    onSetIsLoading: (Boolean) -> Unit,
    onSetIsInitializingApp: (Boolean) -> Unit,
    musicViewModel: MusicViewModel = viewModel(),
    updaterViewModel: AppUpdaterViewModel = rememberAppUpdaterViewModel(),
    streamingViewModel: StreamingMusicViewModel = viewModel(),
    showMediaScanLoader: Boolean,
    onShowMediaScanLoaderChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val onboardingCompleted by appSettings.onboardingCompleted.collectAsState()
    val initialMediaScanCompleted by appSettings.initialMediaScanCompleted.collectAsState()
    val appMode by appSettings.appMode.collectAsState()
    var permissionScreenState by remember { mutableStateOf<PermissionScreenState>(PermissionScreenState.Loading) }
    var permissionRequestLaunched by remember { mutableStateOf(false) }
    var continueFullTour by remember { mutableStateOf(false) }
    var tourWasReset by remember { mutableStateOf(false) }

    val storagePermissions = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            listOf(Manifest.permission.READ_MEDIA_AUDIO)
        }
        else -> {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    val notificationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }
    
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }
    
    val essentialPermissions = storagePermissions + bluetoothPermissions + notificationPermissions
    
    LaunchedEffect(Unit) {
        if (onboardingCompleted && appSettings.appMode.value != "STREAMING") {
            val hasStoragePermissions = storagePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasStoragePermissions) {
                tourWasReset = true
                appSettings.setOnboardingCompleted(false)
                appSettings.setInitialMediaScanCompleted(false)
                onSetIsLoading(false)
            }
        }
    }

    LaunchedEffect(appMode) {
        if (appMode != "STREAMING" && onboardingCompleted) {
            val hasStoragePermissions = storagePermissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (!hasStoragePermissions) {
                tourWasReset = true
                appSettings.setOnboardingCompleted(false)
                appSettings.setInitialMediaScanCompleted(false)
                onSetIsLoading(false)
            }
        }
    }

    var currentOnboardingStep by remember(onboardingCompleted) {
        mutableStateOf(
            if (onboardingCompleted) OnboardingStep.COMPLETE else OnboardingStep.WELCOME
        )
    }

    fun completeOnboardingNow() {
        tourWasReset = false
        appSettings.setOnboardingCompleted(true)
        currentOnboardingStep = OnboardingStep.COMPLETE
        if (!initialMediaScanCompleted && !musicViewModel.isLibraryRefreshing.value && appSettings.appMode.value != "STREAMING") {
            onShowMediaScanLoaderChange(true)
            musicViewModel.refreshLibrary(showMediaScanLoader = false)
        }
    }
    
    val permissionsState = rememberMultiplePermissionsState(essentialPermissions)
    val lifecycleOwner = LocalLifecycleOwner.current

    suspend fun evaluatePermissionsAndSetStep() {
        val hasStoragePermissions = appSettings.appMode.value == "STREAMING" || storagePermissions.all { permission ->
            permissionsState.permissions.find { it.permission == permission }?.status?.isGranted == true
        }

        if (hasStoragePermissions) {
            permissionScreenState = PermissionScreenState.PermissionsGranted
            if (onboardingCompleted) {
                currentOnboardingStep = OnboardingStep.COMPLETE
                onSetIsInitializingApp(true)
                try {
                    musicViewModel.connectToMediaService()
                } catch (e: Exception) {
                    android.util.Log.w("PermissionHandler", "Failed to connect to media service", e)
                }
                onSetIsInitializingApp(false)
            }
            onSetIsLoading(false)
        } else {
            val deniedStoragePermissions = storagePermissions.filter { permission ->
                permissionsState.permissions.find { it.permission == permission }?.status?.isGranted != true
            }

            val shouldShowRationaleForAny = deniedStoragePermissions.any { permission ->
                permissionsState.permissions.find { it.permission == permission }?.status?.shouldShowRationale == true
            }
            
            val allDeniedPermanently = deniedStoragePermissions.isNotEmpty() && deniedStoragePermissions.all { permission ->
                val permissionState = permissionsState.permissions.find { it.permission == permission }
                permissionState?.status?.shouldShowRationale == false && permissionRequestLaunched
            }

            if (!permissionRequestLaunched && !onboardingCompleted) {
                permissionScreenState = PermissionScreenState.PermissionsRequired
            } else if (shouldShowRationaleForAny) {
                permissionScreenState = PermissionScreenState.ShowRationale
            } else if (allDeniedPermanently) {
                permissionScreenState = PermissionScreenState.RedirectToSettings
            } else {
                permissionScreenState = PermissionScreenState.PermissionsRequired
            }
            currentOnboardingStep = OnboardingStep.PERMISSIONS
            onSetIsLoading(false)
        }
    }

    LaunchedEffect(currentOnboardingStep) {
        if (currentOnboardingStep == OnboardingStep.PERMISSIONS) {
            onSetIsLoading(false)
            evaluatePermissionsAndSetStep()
        }
    }

    LaunchedEffect(permissionsState.allPermissionsGranted, permissionsState.shouldShowRationale) {
        if (currentOnboardingStep == OnboardingStep.PERMISSIONS || (onboardingCompleted && permissionScreenState != PermissionScreenState.PermissionsGranted)) {
            evaluatePermissionsAndSetStep()
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                super.onResume(owner)
                if (currentOnboardingStep == OnboardingStep.PERMISSIONS || (onboardingCompleted && permissionScreenState != PermissionScreenState.PermissionsGranted)) {
                    scope.launch {
                        delay(300)
                        evaluatePermissionsAndSetStep()
                        permissionRequestLaunched = false
                    }
                }
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = currentOnboardingStep == OnboardingStep.COMPLETE,
            enter = fadeIn(animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)) +
                   slideInVertically(
                       initialOffsetY = { it / 3 },
                       animationSpec = tween(1000, easing = androidx.compose.animation.core.EaseOutCubic)
                   )
        ) {
            onPermissionsGranted()
        }

        AnimatedVisibility(
            visible = isLoading && currentOnboardingStep != OnboardingStep.COMPLETE && currentOnboardingStep != OnboardingStep.PERMISSIONS,
            exit = fadeOut(animationSpec = tween(800, easing = androidx.compose.animation.core.EaseInCubic))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                InitializationLoader(
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = (!isLoading && !isInitializingApp && currentOnboardingStep != OnboardingStep.COMPLETE)
                    || (tourWasReset && currentOnboardingStep == OnboardingStep.WELCOME && !isInitializingApp),
            enter = fadeIn(animationSpec = tween(800, easing = androidx.compose.animation.core.EaseOutCubic)) +
                   scaleIn(initialScale = 0.95f, animationSpec = tween(800, easing = androidx.compose.animation.core.EaseOutCubic))
        ) {
            OnboardingScreen(
                currentStep = currentOnboardingStep,
                musicViewModel = musicViewModel,
                updaterViewModel = updaterViewModel,
                streamingViewModel = streamingViewModel,
                onNextStep = {
                    when (currentOnboardingStep) {
                        OnboardingStep.WELCOME -> {
                            appSettings.setAppMode("LOCAL")
                            currentOnboardingStep = OnboardingStep.PERMISSIONS
                        }
                        OnboardingStep.APP_MODE_CHOICE -> {
                            currentOnboardingStep = OnboardingStep.PERMISSIONS
                        }
                        OnboardingStep.STREAMING_SERVICE_CHOICE -> currentOnboardingStep = OnboardingStep.STREAMING_SETUP
                        OnboardingStep.STREAMING_SETUP -> currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        OnboardingStep.PERMISSIONS -> {
                            when (permissionScreenState) {
                                PermissionScreenState.PermissionsGranted -> {
                                    currentOnboardingStep = OnboardingStep.MEDIA_SCAN
                                }
                                PermissionScreenState.RedirectToSettings -> {
                                }
                                else -> {
                                    onSetIsLoading(true)
                                    scope.launch {
                                        try {
                                            permissionsState.launchMultiplePermissionRequest()
                                            permissionRequestLaunched = true
                                        } catch (e: Exception) {
                                            onSetIsLoading(false)
                                            permissionScreenState = PermissionScreenState.ShowRationale
                                        }
                                    }
                                }
                            }
                        }
                        OnboardingStep.MEDIA_SCAN -> {
                            musicViewModel.refreshLibrary(showMediaScanLoader = false)
                            onShowMediaScanLoaderChange(true)
                            currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        }
                        OnboardingStep.FULL_TOUR_PROMPT -> {
                            currentOnboardingStep = if (continueFullTour) {
                                OnboardingStep.RHYTHM_GUARD
                            } else {
                                OnboardingStep.SETUP_FINISHED
                            }
                        }
                        OnboardingStep.RHYTHM_GUARD -> currentOnboardingStep = OnboardingStep.AUDIO_PLAYBACK
                        OnboardingStep.AUDIO_PLAYBACK -> currentOnboardingStep = OnboardingStep.THEMING
                        OnboardingStep.THEMING -> currentOnboardingStep = OnboardingStep.PLAYER_THEME_CHOICE
                        OnboardingStep.PLAYER_THEME_CHOICE -> currentOnboardingStep = OnboardingStep.GESTURES
                        OnboardingStep.GESTURES -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.WIDGETS
                            } else {
                                OnboardingStep.LIBRARY_SETUP
                            }
                        }
                        OnboardingStep.LIBRARY_SETUP -> currentOnboardingStep = OnboardingStep.WIDGETS
                        OnboardingStep.WIDGETS -> currentOnboardingStep = OnboardingStep.INTEGRATIONS
                        OnboardingStep.INTEGRATIONS -> currentOnboardingStep = OnboardingStep.UPDATER
                        OnboardingStep.UPDATER -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.RHYTHM_STATS
                            } else {
                                OnboardingStep.BACKUP_RESTORE
                            }
                        }
                        OnboardingStep.NOTIFICATIONS -> currentOnboardingStep = OnboardingStep.RHYTHM_STATS
                        OnboardingStep.BACKUP_RESTORE -> currentOnboardingStep = OnboardingStep.RHYTHM_STATS
                        OnboardingStep.RHYTHM_STATS -> currentOnboardingStep = OnboardingStep.SETUP_FINISHED
                        OnboardingStep.SETUP_FINISHED -> {
                            completeOnboardingNow()
                        }
                        OnboardingStep.COMPLETE -> { }
                    }
                },
                onPrevStep = {
                    when (currentOnboardingStep) {
                        OnboardingStep.APP_MODE_CHOICE -> currentOnboardingStep = OnboardingStep.WELCOME
                        OnboardingStep.PERMISSIONS -> currentOnboardingStep = OnboardingStep.WELCOME
                        OnboardingStep.STREAMING_SERVICE_CHOICE -> currentOnboardingStep = OnboardingStep.WELCOME
                        OnboardingStep.STREAMING_SETUP -> currentOnboardingStep = OnboardingStep.STREAMING_SERVICE_CHOICE
                        OnboardingStep.MEDIA_SCAN -> {
                            currentOnboardingStep = OnboardingStep.PERMISSIONS
                            scope.launch {
                                onSetIsLoading(false)
                                evaluatePermissionsAndSetStep()
                            }
                        }
                        OnboardingStep.FULL_TOUR_PROMPT -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.STREAMING_SETUP
                            } else {
                                OnboardingStep.MEDIA_SCAN
                            }
                        }
                        OnboardingStep.RHYTHM_GUARD -> currentOnboardingStep = OnboardingStep.FULL_TOUR_PROMPT
                        OnboardingStep.AUDIO_PLAYBACK -> currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
                        OnboardingStep.THEMING -> currentOnboardingStep = OnboardingStep.AUDIO_PLAYBACK
                        OnboardingStep.PLAYER_THEME_CHOICE -> currentOnboardingStep = OnboardingStep.THEMING
                        OnboardingStep.GESTURES -> currentOnboardingStep = OnboardingStep.PLAYER_THEME_CHOICE
                        OnboardingStep.LIBRARY_SETUP -> currentOnboardingStep = OnboardingStep.GESTURES
                        OnboardingStep.WIDGETS -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.GESTURES
                            } else {
                                OnboardingStep.LIBRARY_SETUP
                            }
                        }
                        OnboardingStep.INTEGRATIONS -> currentOnboardingStep = OnboardingStep.WIDGETS
                        OnboardingStep.UPDATER -> currentOnboardingStep = OnboardingStep.INTEGRATIONS
                        OnboardingStep.BACKUP_RESTORE -> currentOnboardingStep = OnboardingStep.UPDATER
                        OnboardingStep.NOTIFICATIONS -> currentOnboardingStep = OnboardingStep.UPDATER
                        OnboardingStep.RHYTHM_STATS -> {
                            currentOnboardingStep = if (appSettings.appMode.value == "STREAMING") {
                                OnboardingStep.UPDATER
                            } else {
                                OnboardingStep.BACKUP_RESTORE
                            }
                        }
                        OnboardingStep.SETUP_FINISHED -> {
                            currentOnboardingStep = if (continueFullTour) {
                                OnboardingStep.RHYTHM_STATS
                            } else {
                                OnboardingStep.FULL_TOUR_PROMPT
                            }
                        }
                        else -> { }
                    }
                },
                onContinueFullTour = {
                    continueFullTour = true
                    currentOnboardingStep = OnboardingStep.RHYTHM_GUARD
                },
                onSkipFullTour = {
                    continueFullTour = false
                    completeOnboardingNow()
                },
                onRequestAgain = {
                    onSetIsLoading(true)
                },
                permissionScreenState = permissionScreenState,
                isParentLoading = isLoading,
                themeViewModel = themeViewModel,
                appSettings = appSettings,
                onFinish = {
                    completeOnboardingNow()
                }
            )
        }
    }
}
