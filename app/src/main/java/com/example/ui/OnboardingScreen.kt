package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkOnCyan
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GoldHighlight
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    permissionDenied: Boolean,
    errorMessage: String?,
    onStartCamera: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF0E2942), DeepNavy),
                )
            )
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(104.dp)
                    .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = CyanPrimary, spotColor = CyanPrimary),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.5.dp, CyanPrimary.copy(alpha = 0.65f)),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_xray_logo),
                    contentDescription = stringResource(id = R.string.app_full_title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp)),
                )
            }

            Text(
                text = stringResource(id = R.string.app_full_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = EmeraldOk.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, EmeraldOk.copy(alpha = 0.45f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OfflinePin,
                        contentDescription = null,
                        tint = EmeraldOk,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(id = R.string.offline_ready_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldOk,
                    )
                }
            }

            Text(
                text = stringResource(id = R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onStartCamera,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("start_camera_button"),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = DarkOnCyan,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(id = R.string.start_camera),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (permissionDenied) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = GoldHighlight.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GoldHighlight.copy(alpha = 0.5f)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = GoldHighlight,
                            )
                            Text(
                                text = stringResource(id = R.string.camera_permission_denied),
                                style = MaterialTheme.typography.bodyMedium,
                                color = GoldHighlight,
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenSettings,
                            border = BorderStroke(1.dp, CyanLight),
                            modifier = Modifier.testTag("open_settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = null,
                                tint = CyanLight,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = stringResource(id = R.string.open_settings),
                                color = CyanLight,
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AlertRed,
                    textAlign = TextAlign.Center,
                )
            }

            Text(
                text = stringResource(id = R.string.disclaimer_banner),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary.copy(alpha = 0.72f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
