package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AnatomySystem
import com.example.model.BodyRegion
import com.example.model.BodyViewDirection
import com.example.model.StageAspect
import com.example.model.ViewMode
import com.example.model.VisualLook
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkOnCyan
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceGradientStart
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlSheet(
    state: XRayUiState,
    onToggleExpanded: () -> Unit,
    onSelectRegion: (BodyRegion) -> Unit,
    onSelectSystem: (AnatomySystem) -> Unit,
    onSelectLook: (VisualLook) -> Unit,
    onSelectMode: (ViewMode) -> Unit,
    onBodyVisibilityChange: (Float) -> Unit,
    onLensRadiusChange: (Float) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onToggleTour: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleDebug: () -> Unit,
    onSnapshot: () -> Unit,
    onToggleLabels: () -> Unit,
    onCycleViewDirection: () -> Unit,
    onToggleSegmentation: () -> Unit,
    onStartCalibration: () -> Unit,
    onToggleRecord: () -> Unit,
    onCycleStageAspect: () -> Unit,
    onToggleFlipYaw: () -> Unit,
    onToggleFilament3d: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = SurfaceDark.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Collapse / Expand Header Button — hides/shows ALL controls together
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(SurfaceGradientStart, SurfaceDark),
                        )
                    )
                    .clickable(onClick = onToggleExpanded)
                    .testTag("toggle_sheet_button"),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (state.sheetExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                        contentDescription = null,
                        tint = CyanLight,
                    )
                    Text(
                        text = stringResource(
                            id = if (state.sheetExpanded) R.string.toggle_controls_hide else R.string.toggle_controls_show,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }

            // Entire menu appears/hides together, wrapped in non-horizontal-scroll grids
            AnimatedVisibility(
                visible = state.sheetExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. Search Bar (Full width, zero horizontal scroll)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.search_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary.copy(alpha = 0.7f),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "بحث",
                                    tint = CyanLight,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    onSearchSubmit()
                                    keyboard?.hide()
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated,
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = CyanPrimary.copy(alpha = 0.45f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("search_input"),
                        )

                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .height(50.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onSearchSubmit()
                                    keyboard?.hide()
                                }
                                .testTag("search_submit_button"),
                            shape = RoundedCornerShape(10.dp),
                            color = CyanPrimary,
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "بحث",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = DarkOnCyan,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // 2. All Action Icons in a 4-Column Grid (NO horizontal scroll!)
                    val recLabel = when {
                        state.isRecording -> "إيقاف"
                        state.recordingCountdown -> "إلغاء"
                        else -> "تسجيل"
                    }
                    val recIcon = when {
                        state.isRecording -> Icons.Rounded.Stop
                        state.recordingCountdown -> Icons.Rounded.Close
                        else -> Icons.Rounded.FiberManualRecord
                    }

                    val gridItems = listOf(
                        GridToolItem("جولة", Icons.Rounded.AutoStories, state.tourActive, "tour_button", onClick = onToggleTour),
                        GridToolItem("كاميرا", Icons.Rounded.Cameraswitch, false, "switch_camera_button", onClick = onSwitchCamera),
                        GridToolItem("لقطة", Icons.Rounded.PhotoCamera, false, "snapshot_button", onClick = onSnapshot),
                        GridToolItem(recLabel, recIcon, state.isRecording || state.recordingCountdown, "record_button", if (state.isRecording) AlertRed else null, onToggleRecord),

                        GridToolItem("الأسماء", Icons.Rounded.Label, state.showLabels, "labels_button", onClick = onToggleLabels),
                        GridToolItem("الاتجاه: ${state.viewDirection.labelAr}", Icons.Rounded.Visibility, state.viewDirection != BodyViewDirection.AUTO, "view_direction_button", onClick = onCycleViewDirection),
                        GridToolItem("قص الجسم", Icons.Rounded.ContentCut, state.segEnabled, "segmentation_button", onClick = onToggleSegmentation),
                        GridToolItem("معايرة", Icons.Rounded.Straighten, state.calibration?.hasTorso == true, "calibration_button", onClick = onStartCalibration),

                        GridToolItem("نقاط التتبع", Icons.Rounded.CenterFocusStrong, state.debugMode, "debug_button", onClick = onToggleDebug),
                        GridToolItem("المسرح", Icons.Rounded.AspectRatio, state.stageAspect != StageAspect.FULLSCREEN, "stage_button", onClick = onCycleStageAspect),
                        GridToolItem("عكس دوران", Icons.Rounded.Flip, state.flipYaw, "flip_yaw_button", onClick = onToggleFlipYaw),
                        GridToolItem("نموذج 3D", Icons.Rounded.ViewInAr, state.showFilament3d, "filament_3d_button", onClick = onToggleFilament3d),
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        gridItems.chunked(4).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                rowItems.forEach { item ->
                                    GridActionTile(
                                        item = item,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    // 3. System, Look, ViewMode Selectors (Full-width equal row)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DropdownChipSelector(
                            label = state.system.labelAr,
                            icon = Icons.Rounded.Layers,
                            options = AnatomySystem.entries.map { it.labelAr to it },
                            onSelect = onSelectSystem,
                            testTag = "system_selector",
                            modifier = Modifier.weight(1f),
                        )
                        DropdownChipSelector(
                            label = state.look.labelAr,
                            icon = Icons.Rounded.Palette,
                            options = VisualLook.entries.map { it.labelAr to it },
                            onSelect = onSelectLook,
                            testTag = "look_selector",
                            modifier = Modifier.weight(1f),
                        )
                        DropdownChipSelector(
                            label = state.viewMode.labelAr,
                            icon = Icons.Rounded.Radar,
                            options = ViewMode.entries.map { it.labelAr to it },
                            onSelect = onSelectMode,
                            testTag = "mode_selector",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 4. Sliders (Side-by-side full width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SliderControl(
                            label = "ظهور جسمي",
                            value = state.bodyVisibility,
                            valueRange = 0f..1f,
                            onValueChange = onBodyVisibilityChange,
                            testTag = "body_visibility_slider",
                            modifier = Modifier.weight(1f),
                        )
                        SliderControl(
                            label = "حجم العدسة",
                            value = state.lensRadiusFrac,
                            valueRange = 0.08f..0.40f,
                            onValueChange = onLensRadiusChange,
                            testTag = "lens_radius_slider",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 5. All Body Regions Wrapped in FlowRow (NO horizontal scroll!)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BodyRegion.entries.forEach { region ->
                            CompactRegionChip(
                                label = region.labelAr,
                                icon = when (region) {
                                    BodyRegion.ALL -> Icons.Rounded.AccessibilityNew
                                    BodyRegion.HEAD -> Icons.Rounded.CenterFocusStrong
                                    BodyRegion.CHEST, BodyRegion.ABDOMEN, BodyRegion.BACK, BodyRegion.PELVIS -> Icons.Rounded.Layers
                                    else -> Icons.Rounded.Radar
                                },
                                active = state.selectedRegion == region,
                                testTag = "region_${region.key}",
                                onClick = { onSelectRegion(region) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class GridToolItem(
    val label: String,
    val icon: ImageVector,
    val active: Boolean,
    val testTag: String,
    val accentOverride: Color? = null,
    val onClick: () -> Unit,
)

@Composable
private fun GridActionTile(
    item: GridToolItem,
    modifier: Modifier = Modifier,
) {
    val activeColor = item.accentOverride ?: CyanPrimary
    val bgColor = if (item.active) activeColor else SurfaceElevated
    val fgColor = if (item.active) DarkOnCyan else Color.White
    val borderColor = if (item.active) CyanLight else CyanPrimary.copy(alpha = 0.45f)

    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(54.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = item.onClick)
            .testTag(item.testTag),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = fgColor,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                fontWeight = if (item.active) FontWeight.Bold else FontWeight.Medium,
                color = fgColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactRegionChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val bgColor = if (active) CyanPrimary else SurfaceElevated
    val fgColor = if (active) DarkOnCyan else Color.White
    val borderColor = if (active) CyanLight else CyanPrimary.copy(alpha = 0.4f)

    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fgColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = fgColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun <T> DropdownChipSelector(
    label: String,
    icon: ImageVector,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .height(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = true }
                .testTag(testTag),
            shape = RoundedCornerShape(10.dp),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = CyanLight,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceElevated,
        ) {
            options.forEach { (title, item) ->
                DropdownMenuItem(
                    text = { Text(text = title, color = Color.White) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun SliderControl(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = CyanPrimary,
                activeTrackColor = CyanPrimary,
                inactiveTrackColor = CyanPrimary.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag(testTag),
        )
    }
}
