package com.pupi.pet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pupi.pet.overlay.PupiFloatingService

/**
 * Minimal Host Activity:
 * Strict Product Decision:
 * There is NO dedicated pet-care app screen. This activity serves strictly as an
 * onboarding gate for Android system permissions (Overlay & Battery), and launching
 * the floating Pupi onto the screen.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PupiOnboardingHostScreen(
                        hasOverlayPermission = Settings.canDrawOverlays(this),
                        onRequestPermission = { requestOverlayPermission() },
                        onLaunchPupi = { launchPupiService() },
                        onStopPupi = { stopPupiService() }
                    )
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPupiService() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, PupiFloatingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // Finish activity so Pupi lives exclusively floating on screen
            finish()
        } else {
            Toast.makeText(this, "Please grant 'Display over other apps' first.", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
        }
    }

    private fun stopPupiService() {
        val intent = Intent(this, PupiFloatingService::class.java)
        stopService(intent)
        Toast.makeText(this, "Pupi has gone to rest.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PupiOnboardingHostScreen(
    hasOverlayPermission: Boolean,
    onRequestPermission: () -> Unit,
    onLaunchPupi: () -> Unit,
    onStopPupi: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PUPI",
            style = MaterialTheme.typography.headlineLarge
        )
        Text(
            text = "Your Little AI Pet Companion",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Welcome to PUPI!",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Pupi lives directly on your screen as a floating 3D companion while you use your favorite apps.\n\nTo allow Pupi to appear, Android requires the 'Display over other apps' permission.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (!hasOverlayPermission) {
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Screen Overlay Permission")
            }
        } else {
            Button(
                onClick = onLaunchPupi,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Bring Pupi to Your Screen")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStopPupi,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Pupi to Sleep (Exit)")
            }
        }
    }
}
