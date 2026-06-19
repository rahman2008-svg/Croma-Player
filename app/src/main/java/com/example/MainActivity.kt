package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.VideoItem
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.MainScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.ColorOSGreen40
import com.example.ui.theme.ColorOSGreen80
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VideoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppSelectorContainer()
            }
        }
    }
}

sealed class Screen {
    object Main : Screen()
    data class Player(val video: VideoItem) : Screen()
    data class Editor(val video: VideoItem) : Screen()
}

@Composable
fun AppSelectorContainer() {
    val context = LocalContext.current
    val viewModel: VideoViewModel = viewModel()
    
    // Dynamic permissions checking depending on API levels
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.CAMERA)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            Toast.makeText(context, "Storage and Camera access granted", Toast.LENGTH_SHORT).show()
            viewModel.refreshVideoCatalog()
        } else {
            Toast.makeText(context, "Demonstration mock files initialized", Toast.LENGTH_LONG).show()
        }
    }

    // Triggers catalog sync upon load
    LaunchedEffect(hasPermissions) {
        viewModel.refreshVideoCatalog()
    }

    // State machine for screen flows
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
    ) {
        if (!hasPermissions) {
            // Elegant Illustrative Permission Onboarding card
            PermissionOnboardingCard {
                permissionLauncher.launch(requiredPermissions)
            }
        } else {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    is Screen.Main -> {
                        MainScreen(
                            viewModel = viewModel,
                            onPlayVideo = { video -> currentScreen = Screen.Player(video) },
                            onEditVideo = { video -> currentScreen = Screen.Editor(video) }
                        )
                    }
                    is Screen.Player -> {
                        PlayerScreen(
                            video = screen.video,
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.Main },
                            onEdit = { video -> currentScreen = Screen.Editor(video) }
                        )
                    }
                    is Screen.Editor -> {
                        EditorScreen(
                            video = screen.video,
                            viewModel = viewModel,
                            onBack = { currentScreen = Screen.Main },
                            onExportComplete = { currentScreen = Screen.Main }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionOnboardingCard(onGrantRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ColorOSGreen80.copy(alpha = 0.12f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Permission Required",
                        tint = ColorOSGreen80,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Unlock Library Access",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Croma Player catalogs your local device movies, organizes folders, and records real camera sequences completely offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { onGrantRequest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("grant_permission_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorOSGreen40),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Grant Media Permissions",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
