package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AnatomyAttributions
import com.example.data.InstalledGlbModel
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkOnCyan
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GoldHighlight
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary

@Composable
fun AttributionsDialog(
    installedModels: List<InstalledGlbModel>,
    onImportGlb: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = DeepNavy,
            border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.6f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Gavel,
                            contentDescription = null,
                            tint = CyanLight,
                        )
                        Text(
                            text = "حقوق ونسب وتراخيص النماذج",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_attributions_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "إغلاق",
                            tint = Color.White,
                        )
                    }
                }

                // Local GLB asset status card (honest probing modeled after open-twin-xr)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SurfaceDark,
                    border = BorderStroke(
                        1.dp,
                        if (installedModels.isEmpty()) GoldHighlight.copy(alpha = 0.55f) else EmeraldOk.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = if (installedModels.isEmpty()) GoldHighlight else EmeraldOk,
                            )
                            Text(
                                text = if (installedModels.isEmpty()) {
                                    "نماذج GLB الطبية: غير مثبّتة محليًا بعد (0 ملفات)"
                                } else {
                                    "نماذج GLB المثبّتة محليًا: ${installedModels.size}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Text(
                            text = if (installedModels.isEmpty()) {
                                "لا يتم تحميل أي نموذج من الإنترنت وقت التشغيل. نزّل ملفات .glb من المصادر المفتوحة أدناه (مثل humanatlas.io أو Z-Anatomy GLB) ثم استوردها مباشرة للعمل دون إنترنت."
                            } else {
                                installedModels.joinToString(" • ") { "${it.displayName} (${it.formattedSize})" }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Button(
                            onClick = onImportGlb,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = DarkOnCyan,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_glb_from_attributions_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = null,
                            )
                            Text(
                                text = "استيراد ملف .glb من الجهاز (دون إنترنت)",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(AnatomyAttributions.entries, key = { it.source }) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                Color(item.licenseBadgeColorHex).copy(alpha = 0.45f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = item.source,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(item.licenseBadgeColorHex).copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, Color(item.licenseBadgeColorHex)),
                                    ) {
                                        Text(
                                            text = item.license,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(item.licenseBadgeColorHex),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                                Text(
                                    text = item.usageNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                                Text(
                                    text = item.citationOrUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanLight,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
