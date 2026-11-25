package com.example.render

import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AnatomySystem
import com.example.model.TrackingFrame
import com.example.model.VisualLook
import com.example.ui.theme.CyanLight
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DarkOnCyan
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GoldHighlight
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextSecondary
import com.google.android.filament.Box as FilamentBox
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

private data class MeshPart(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance,
    val category: AnatomySystem,
    val parentType: Int, // 0=torso, 1=head, 2=world(limb)
    val baseLocalMatrix: FloatArray? = null,
    val isHeart: Boolean = false,
)

private data class LimbSegmentMesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance,
    val category: AnatomySystem,
    val thickness: Float,
    val jointA: (ArticulatedPose3d) -> Vec3,
    val jointB: (ArticulatedPose3d) -> Vec3,
)

private data class JointSphereMesh(
    val entity: Int,
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val material: MaterialInstance,
    val radius: Float,
    val jointPos: (ArticulatedPose3d) -> Vec3,
)

@Composable
fun FilamentAnatomyOverlay(
    system: AnatomySystem,
    look: VisualLook,
    trackingFrame: TrackingFrame,
    isFrontCamera: Boolean,
    onSelectSystem: (AnatomySystem) -> Unit,
    onSelectLook: (VisualLook) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var orbitYaw by remember { mutableFloatStateOf(0f) }
    var orbitPitch by remember { mutableFloatStateOf(4f) }
    var engineError by remember { mutableStateOf<String?>(null) }

    val controller = remember {
        FilamentSceneController(
            onError = { msg -> engineError = msg }
        )
    }

    controller.updateParams(
        system = system,
        look = look,
        trackingFrame = trackingFrame,
        isFrontCamera = isFrontCamera,
        orbitYawDeg = orbitYaw,
        orbitPitchDeg = orbitPitch,
    )

    DisposableEffect(Unit) {
        onDispose {
            controller.destroy()
        }
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DeepNavy.copy(alpha = 0.98f),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (engineError == null) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            controller.attachSurfaceView(sv, ctx)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                orbitYaw += dragAmount.x * 0.45f
                                orbitPitch = (orbitPitch + dragAmount.y * 0.35f).coerceIn(-55f, 55f)
                            }
                        },
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ViewInAr,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = engineError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            // Top Header with Live ML Kit Pose Sync Indicator
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = CyanLight,
                            modifier = Modifier.size(24.dp),
                        )
                        Column {
                            Text(
                                text = "النموذج التشريحي 3D (ML Kit + Filament)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            val syncText = if (trackingFrame.hasPose && trackingFrame.pose3d.size == 33) {
                                "تتبع الحركة الفوري: نشط ✓ (${trackingFrame.fps} إطار/ث) · اسحب للتدوير"
                            } else {
                                "قف أمام الكاميرا لتحريك الهيكل 3D مباشرة · اسحب للتدوير"
                            }
                            Text(
                                text = syncText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (trackingFrame.hasPose) EmeraldOk else GoldHighlight,
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                orbitYaw = 0f
                                orbitPitch = 4f
                            },
                            modifier = Modifier.testTag("reset_3d_orbit_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "إعادة ضبط زاوية الكاميرا",
                                tint = CyanLight,
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("close_filament_3d_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "إغلاق العرض ثلاثي الأبعاد",
                                tint = Color.White,
                            )
                        }
                    }
                }
            }

            // Bottom Quick System & Look Switcher
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                color = SurfaceDark.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.45f)),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AnatomySystem.entries.forEach { sys ->
                            val active = system == sys
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectSystem(sys) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) CyanPrimary else SurfaceElevated,
                                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.45f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = sys.labelAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) DarkOnCyan else Color.White,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VisualLook.entries.forEach { lk ->
                            val active = look == lk
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectLook(lk) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) CyanPrimary else SurfaceElevated,
                                border = BorderStroke(1.dp, CyanPrimary.copy(alpha = 0.45f)),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lk.labelAr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                        color = if (active) DarkOnCyan else Color.White,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class FilamentSceneController(
    private val onError: (String) -> Unit,
) {
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var uiHelper: UiHelper? = null
    private var displayHelper: DisplayHelper? = null
    private var materialProvider: UbershaderProvider? = null
    private var torsoRootEntity: Int = 0
    private var headRootEntity: Int = 0
    private var lightEntity: Int = 0

    private val parts = mutableListOf<MeshPart>()
    private val limbSegments = mutableListOf<LimbSegmentMesh>()
    private val jointSpheres = mutableListOf<JointSphereMesh>()

    @Volatile
    private var targetSystem = AnatomySystem.ALL
    @Volatile
    private var targetLook = VisualLook.XRAY
    @Volatile
    private var targetTrackingFrame = TrackingFrame()
    @Volatile
    private var targetFrontCamera = true
    @Volatile
    private var targetOrbitYawDeg = 0f
    @Volatile
    private var targetOrbitPitchDeg = 4f
    @Volatile
    private var destroyed = false

    private var smoothedPoseState: ArticulatedPose3d = Pose3dKinematics.defaultStandingPose()

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (destroyed) return
            renderFrame(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    fun updateParams(
        system: AnatomySystem,
        look: VisualLook,
        trackingFrame: TrackingFrame,
        isFrontCamera: Boolean,
        orbitYawDeg: Float,
        orbitPitchDeg: Float,
    ) {
        targetSystem = system
        targetLook = look
        targetTrackingFrame = trackingFrame
        targetFrontCamera = isFrontCamera
        targetOrbitYawDeg = orbitYawDeg
        targetOrbitPitchDeg = orbitPitchDeg
    }

    fun attachSurfaceView(surfaceView: SurfaceView, context: android.content.Context) {
        try {
            Filament.init()
            Gltfio.init()

            val eng = Engine.create()
            engine = eng
            renderer = eng.createRenderer().apply {
                clearOptions = Renderer.ClearOptions().apply {
                    clear = true
                    clearColor = floatArrayOf(0.02f, 0.043f, 0.078f, 1.0f)
                }
            }
            scene = eng.createScene()
            view = eng.createView()
            val camEntity = EntityManager.get().create()
            camera = eng.createCamera(camEntity)
            materialProvider = UbershaderProvider(eng)

            view?.apply {
                scene = this@FilamentSceneController.scene
                camera = this@FilamentSceneController.camera
            }

            setupScene(eng)

            val dh = DisplayHelper(context)
            displayHelper = dh

            val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
            uiHelper = helper
            helper.renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { eng.destroySwapChain(it) }
                    swapChain = eng.createSwapChain(surface)
                    surfaceView.display?.let { disp ->
                        renderer?.let { r -> dh.attach(r, disp) }
                    }
                }

                override fun onDetachedFromSurface() {
                    dh.detach()
                    swapChain?.let {
                        eng.destroySwapChain(it)
                        eng.flushAndWait()
                        swapChain = null
                    }
                }

                override fun onResized(width: Int, height: Int) {
                    val v = view ?: return
                    v.viewport = Viewport(0, 0, width, height)
                    val aspect = width.toDouble() / height.coerceAtLeast(1).toDouble()
                    camera?.setProjection(48.0, aspect, 0.1, 20.0, Camera.Fov.VERTICAL)
                }
            }
            helper.attachTo(surfaceView)
            choreographer.postFrameCallback(frameCallback)
        } catch (t: Throwable) {
            onError("تعذّر تهيئة محرك Filament 3D على هذا الجهاز: ${t.localizedMessage ?: "OpenGL ES 3.0 مطلوب"}")
        }
    }

    private fun setupScene(eng: Engine) {
        val sc = scene ?: return
        val em = EntityManager.get()

        lightEntity = em.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.95f, 0.98f, 1.0f)
            .intensity(98_000f)
            .direction(-0.35f, -0.55f, -0.75f)
            .build(eng, lightEntity)
        sc.addEntity(lightEntity)

        torsoRootEntity = em.create()
        eng.transformManager.create(torsoRootEntity)

        headRootEntity = em.create()
        eng.transformManager.create(headRootEntity)

        val boneColor = floatArrayOf(0.87f, 0.95f, 1.0f, 1.0f)
        val jointColor = floatArrayOf(0.55f, 0.88f, 1.0f, 1.0f)
        val organHeartColor = floatArrayOf(0.88f, 0.14f, 0.22f, 1.0f)
        val organLungColor = floatArrayOf(0.92f, 0.56f, 0.62f, 1.0f)
        val organLiverColor = floatArrayOf(0.56f, 0.23f, 0.12f, 1.0f)
        val organStomachColor = floatArrayOf(0.88f, 0.58f, 0.42f, 1.0f)
        val muscleColor = floatArrayOf(0.75f, 0.16f, 0.16f, 1.0f)

        // Head Root Children (Skull & Mandible in head-local coordinates)
        addPart(eng, sc, parentType = 1, 0f, 0.03f, 0f, 0.13f, 0.17f, 0.15f, boneColor, 0.35f, AnatomySystem.BONES)
        addPart(eng, sc, parentType = 1, 0f, -0.11f, 0.04f, 0.09f, 0.06f, 0.09f, boneColor, 0.35f, AnatomySystem.BONES)

        // Torso Root Children (local Y: 0.0 = hips -> 0.85 = shoulders)
        // Spine (13 vertebrae)
        for (i in 0 until 13) {
            val y = 0.05f + i * 0.064f
            val z = -0.12f + sin(i * 0.35f) * 0.02f
            addPart(eng, sc, parentType = 0, 0f, y, z, 0.04f, 0.028f, 0.04f, boneColor, 0.3f, AnatomySystem.BONES)
        }
        // Sternum & Clavicles
        addPart(eng, sc, parentType = 0, 0f, 0.64f, 0.14f, 0.03f, 0.17f, 0.02f, boneColor, 0.3f, AnatomySystem.BONES)
        addPart(eng, sc, parentType = 0, -0.14f, 0.79f, 0.07f, 0.14f, 0.02f, 0.022f, boneColor, 0.3f, AnatomySystem.BONES)
        addPart(eng, sc, parentType = 0, 0.14f, 0.79f, 0.07f, 0.14f, 0.02f, 0.022f, boneColor, 0.3f, AnatomySystem.BONES)

        // Ribcage (8 pairs)
        for (i in 0 until 8) {
            val y = 0.75f - i * 0.045f
            val rx = 0.19f + sin((i + 1) * 0.36f) * 0.05f
            val rz = 0.13f + sin((i + 1) * 0.36f) * 0.025f
            for (side in floatArrayOf(-1f, 1f)) {
                addPart(eng, sc, parentType = 0, side * rx * 0.56f, y, 0.02f, rx * 0.44f, 0.014f, rz, boneColor, 0.35f, AnatomySystem.BONES)
            }
        }
        // Pelvis
        addPart(eng, sc, parentType = 0, -0.13f, 0.02f, 0f, 0.10f, 0.10f, 0.08f, boneColor, 0.3f, AnatomySystem.BONES)
        addPart(eng, sc, parentType = 0, 0.13f, 0.02f, 0f, 0.10f, 0.10f, 0.08f, boneColor, 0.3f, AnatomySystem.BONES)

        // Organs inside Torso
        addPart(eng, sc, parentType = 0, -0.05f, 0.61f, 0.04f, 0.068f, 0.085f, 0.065f, organHeartColor, 0.05f, AnatomySystem.ORGANS, isHeart = true)
        addPart(eng, sc, parentType = 0, 0.12f, 0.65f, -0.02f, 0.085f, 0.15f, 0.09f, organLungColor, 0.05f, AnatomySystem.ORGANS)
        addPart(eng, sc, parentType = 0, -0.13f, 0.65f, -0.02f, 0.080f, 0.15f, 0.09f, organLungColor, 0.05f, AnatomySystem.ORGANS)
        addPart(eng, sc, parentType = 0, 0.10f, 0.38f, 0.03f, 0.11f, 0.065f, 0.075f, organLiverColor, 0.1f, AnatomySystem.ORGANS)
        addPart(eng, sc, parentType = 0, -0.10f, 0.36f, 0.04f, 0.08f, 0.065f, 0.065f, organStomachColor, 0.05f, AnatomySystem.ORGANS)

        // Torso Muscles (Pectorals & Abs)
        addPart(eng, sc, parentType = 0, 0.11f, 0.70f, 0.12f, 0.10f, 0.07f, 0.035f, muscleColor, 0.05f, AnatomySystem.MUSCLES)
        addPart(eng, sc, parentType = 0, -0.11f, 0.70f, 0.12f, 0.10f, 0.07f, 0.035f, muscleColor, 0.05f, AnatomySystem.MUSCLES)

        // Articulated 3D Limbs (driven directly by ML Kit Pose3D joints!)
        // Arms: Humerus + Forearms + Hands + Biceps
        addLimbSegment(eng, sc, boneColor, 0.055f, AnatomySystem.BONES, { it.shR }, { it.elR })
        addLimbSegment(eng, sc, boneColor, 0.055f, AnatomySystem.BONES, { it.shL }, { it.elL })
        addLimbSegment(eng, sc, boneColor, 0.045f, AnatomySystem.BONES, { it.elR }, { it.wrR })
        addLimbSegment(eng, sc, boneColor, 0.045f, AnatomySystem.BONES, { it.elL }, { it.wrL })
        addLimbSegment(eng, sc, boneColor, 0.045f, AnatomySystem.BONES, { it.wrR }, { it.hdR })
        addLimbSegment(eng, sc, boneColor, 0.045f, AnatomySystem.BONES, { it.wrL }, { it.hdL })

        addLimbSegment(eng, sc, muscleColor, 0.085f, AnatomySystem.MUSCLES, { it.shR }, { it.elR })
        addLimbSegment(eng, sc, muscleColor, 0.085f, AnatomySystem.MUSCLES, { it.shL }, { it.elL })

        // Legs: Femurs + Shins + Feet + Thigh muscles
        addLimbSegment(eng, sc, boneColor, 0.072f, AnatomySystem.BONES, { it.hpR }, { it.knR })
        addLimbSegment(eng, sc, boneColor, 0.072f, AnatomySystem.BONES, { it.hpL }, { it.knL })
        addLimbSegment(eng, sc, boneColor, 0.056f, AnatomySystem.BONES, { it.knR }, { it.akR })
        addLimbSegment(eng, sc, boneColor, 0.056f, AnatomySystem.BONES, { it.knL }, { it.akL })
        addLimbSegment(eng, sc, boneColor, 0.048f, AnatomySystem.BONES, { it.akR }, { it.ftR })
        addLimbSegment(eng, sc, boneColor, 0.048f, AnatomySystem.BONES, { it.akL }, { it.ftL })

        addLimbSegment(eng, sc, muscleColor, 0.115f, AnatomySystem.MUSCLES, { it.hpR }, { it.knR })
        addLimbSegment(eng, sc, muscleColor, 0.115f, AnatomySystem.MUSCLES, { it.hpL }, { it.knL })

        // Joint Spheres (Shoulders, Elbows, Wrists, Hips, Knees, Ankles)
        listOf<(ArticulatedPose3d) -> Vec3>(
            { it.shR }, { it.shL }, { it.elR }, { it.elL }, { it.wrR }, { it.wrL },
            { it.hpR }, { it.hpL }, { it.knR }, { it.knL }, { it.akR }, { it.akL },
        ).forEach { getter ->
            addJointSphere(eng, sc, jointColor, 0.048f, getter)
        }
    }

    private fun createMat(color: FloatArray, metallic: Float): MaterialInstance? {
        val provider = materialProvider ?: return null
        val key = MaterialProvider.MaterialKey().apply {
            unlit = false
            hasVertexColors = false
            alphaMode = 0
        }
        val mat = provider.createMaterialInstance(key, IntArray(8), "anatomy_pbr", null) ?: return null
        mat.setParameter("baseColorFactor", color[0], color[1], color[2], color[3])
        mat.setParameter("metallicFactor", metallic)
        mat.setParameter("roughnessFactor", 0.42f)
        return mat
    }

    private fun addPart(
        eng: Engine,
        sc: Scene,
        parentType: Int,
        cx: Float,
        cy: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        color: FloatArray,
        metallic: Float,
        category: AnatomySystem,
        isHeart: Boolean = false,
    ) {
        val mat = createMat(color, metallic) ?: return
        val (vb, ib, box) = buildEllipsoidBuffers(eng, cx, cy, cz, rx, ry, rz, latBands = 12, lonBands = 16)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(box)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, ib.indexCount)
            .material(0, mat)
            .castShadows(false)
            .receiveShadows(false)
            .build(eng, entity)

        val tm = eng.transformManager
        tm.create(entity)
        val parent = if (parentType == 1) headRootEntity else torsoRootEntity
        tm.setParent(tm.getInstance(entity), tm.getInstance(parent))
        sc.addEntity(entity)
        parts.add(MeshPart(entity, vb, ib, mat, category, parentType, isHeart = isHeart))
    }

    private fun addLimbSegment(
        eng: Engine,
        sc: Scene,
        color: FloatArray,
        thickness: Float,
        category: AnatomySystem,
        jointA: (ArticulatedPose3d) -> Vec3,
        jointB: (ArticulatedPose3d) -> Vec3,
    ) {
        val mat = createMat(color, 0.3f) ?: return
        // Unit bone along Y [-0.5..+0.5], unit radius 0.5 in X/Z
        val (vb, ib, box) = buildEllipsoidBuffers(eng, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, latBands = 10, lonBands = 14)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(box)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, ib.indexCount)
            .material(0, mat)
            .castShadows(false)
            .receiveShadows(false)
            .build(eng, entity)
        eng.transformManager.create(entity)
        sc.addEntity(entity)
        limbSegments.add(LimbSegmentMesh(entity, vb, ib, mat, category, thickness, jointA, jointB))
    }

    private fun addJointSphere(
        eng: Engine,
        sc: Scene,
        color: FloatArray,
        radius: Float,
        jointPos: (ArticulatedPose3d) -> Vec3,
    ) {
        val mat = createMat(color, 0.4f) ?: return
        val (vb, ib, box) = buildEllipsoidBuffers(eng, 0f, 0f, 0f, 1f, 1f, 1f, latBands = 10, lonBands = 12)
        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(box)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, ib.indexCount)
            .material(0, mat)
            .castShadows(false)
            .receiveShadows(false)
            .build(eng, entity)
        eng.transformManager.create(entity)
        sc.addEntity(entity)
        jointSpheres.add(JointSphereMesh(entity, vb, ib, mat, radius, jointPos))
    }

    private fun renderFrame(frameTimeNanos: Long) {
        val eng = engine ?: return
        val ren = renderer ?: return
        val v = view ?: return
        val sc = swapChain ?: return
        val cam = camera ?: return
        if (uiHelper?.isReadyToRender != true) return

        // Camera orbit around full-body midpoint
        val yawRad = targetOrbitYawDeg * PI.toFloat() / 180f
        val pitchRad = targetOrbitPitchDeg * PI.toFloat() / 180f
        val dist = 3.15f
        val targetY = 0.02f
        val camX = dist * cos(pitchRad) * sin(yawRad)
        val camY = targetY + dist * sin(pitchRad) * 0.6f
        val camZ = dist * cos(pitchRad) * cos(yawRad)
        cam.lookAt(
            camX.toDouble(), camY.toDouble(), camZ.toDouble(),
            0.0, targetY.toDouble(), 0.0,
            0.0, 1.0, 0.0,
        )

        // Build & smooth real-time 3D articulated pose from ML Kit Pose Detection
        val frame = targetTrackingFrame
        val targetArticulated = Pose3dKinematics.buildArticulatedPose(
            pose3d = frame.pose3d,
            isFrontCamera = targetFrontCamera,
            faceEulerYDeg = frame.faceEulerY,
        )
        smoothedPoseState = lerpArticulated(smoothedPoseState, targetArticulated, 0.35f)
        val pose = smoothedPoseState

        val tm = eng.transformManager
        tm.getInstance(torsoRootEntity).takeIf { it != 0 }?.let { inst ->
            tm.setTransform(inst, pose.torsoMatrix)
        }
        tm.getInstance(headRootEntity).takeIf { it != 0 }?.let { inst ->
            tm.setTransform(inst, pose.headMatrix)
        }

        val tSec = frameTimeNanos / 1_000_000_000.0f
        val pulse = 1f + 0.06f * sin(tSec * 5.2f)
        val isXray = targetLook == VisualLook.XRAY

        for (p in parts) {
            val show = targetSystem == AnatomySystem.ALL || targetSystem == p.category
            val inst = tm.getInstance(p.entity)
            if (inst != 0) {
                val m = FloatArray(16)
                Matrix.setIdentityM(m, 0)
                if (!show) {
                    Matrix.scaleM(m, 0, 0f, 0f, 0f)
                } else if (p.isHeart) {
                    Matrix.translateM(m, 0, -0.05f, 0.61f, 0.04f)
                    Matrix.scaleM(m, 0, pulse, pulse, pulse)
                    Matrix.translateM(m, 0, 0.05f, -0.61f, -0.04f)
                }
                tm.setTransform(inst, m)
            }
            if (p.category == AnatomySystem.BONES) {
                if (isXray) p.material.setParameter("baseColorFactor", 0.55f, 0.88f, 1.0f, 1.0f)
                else p.material.setParameter("baseColorFactor", 0.94f, 0.90f, 0.80f, 1.0f)
            }
        }

        for (seg in limbSegments) {
            val show = targetSystem == AnatomySystem.ALL || targetSystem == seg.category
            val inst = tm.getInstance(seg.entity)
            if (inst != 0) {
                if (!show) {
                    val zero = FloatArray(16)
                    Matrix.setIdentityM(zero, 0)
                    Matrix.scaleM(zero, 0, 0f, 0f, 0f)
                    tm.setTransform(inst, zero)
                } else {
                    val m = Pose3dKinematics.segmentMatrix(seg.jointA(pose), seg.jointB(pose), seg.thickness)
                    tm.setTransform(inst, m)
                }
            }
            if (seg.category == AnatomySystem.BONES) {
                if (isXray) seg.material.setParameter("baseColorFactor", 0.55f, 0.88f, 1.0f, 1.0f)
                else seg.material.setParameter("baseColorFactor", 0.94f, 0.90f, 0.80f, 1.0f)
            }
        }

        val showJoints = targetSystem == AnatomySystem.ALL || targetSystem == AnatomySystem.BONES
        for (js in jointSpheres) {
            val inst = tm.getInstance(js.entity)
            if (inst != 0) {
                if (!showJoints) {
                    val zero = FloatArray(16)
                    Matrix.setIdentityM(zero, 0)
                    Matrix.scaleM(zero, 0, 0f, 0f, 0f)
                    tm.setTransform(inst, zero)
                } else {
                    tm.setTransform(inst, Pose3dKinematics.jointMatrix(js.jointPos(pose), js.radius))
                }
            }
        }

        if (ren.beginFrame(sc, frameTimeNanos)) {
            ren.render(v)
            ren.endFrame()
        }
    }

    private fun lerpArticulated(a: ArticulatedPose3d, b: ArticulatedPose3d, k: Float): ArticulatedPose3d {
        fun lv(v1: Vec3, v2: Vec3) = Vec3(v1.x + (v2.x - v1.x) * k, v1.y + (v2.y - v1.y) * k, v1.z + (v2.z - v1.z) * k)
        fun lm(m1: FloatArray, m2: FloatArray) = FloatArray(16) { i -> m1[i] + (m2[i] - m1[i]) * k }
        return ArticulatedPose3d(
            isLiveTracked = b.isLiveTracked,
            torsoMatrix = lm(a.torsoMatrix, b.torsoMatrix),
            headMatrix = lm(a.headMatrix, b.headMatrix),
            shR = lv(a.shR, b.shR),
            shL = lv(a.shL, b.shL),
            elR = lv(a.elR, b.elR),
            elL = lv(a.elL, b.elL),
            wrR = lv(a.wrR, b.wrR),
            wrL = lv(a.wrL, b.wrL),
            hdR = lv(a.hdR, b.hdR),
            hdL = lv(a.hdL, b.hdL),
            hpR = lv(a.hpR, b.hpR),
            hpL = lv(a.hpL, b.hpL),
            knR = lv(a.knR, b.knR),
            knL = lv(a.knL, b.knL),
            akR = lv(a.akR, b.akR),
            akL = lv(a.akL, b.akL),
            ftR = lv(a.ftR, b.ftR),
            ftL = lv(a.ftL, b.ftL),
            hasArms = b.hasArms,
            hasLegs = b.hasLegs,
        )
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        choreographer.removeFrameCallback(frameCallback)
        val eng = engine ?: return
        runCatching {
            uiHelper?.detach()
            parts.forEach { part ->
                scene?.removeEntity(part.entity)
                eng.destroyEntity(part.entity)
                eng.destroyVertexBuffer(part.vertexBuffer)
                eng.destroyIndexBuffer(part.indexBuffer)
                eng.destroyMaterialInstance(part.material)
            }
            limbSegments.forEach { seg ->
                scene?.removeEntity(seg.entity)
                eng.destroyEntity(seg.entity)
                eng.destroyVertexBuffer(seg.vertexBuffer)
                eng.destroyIndexBuffer(seg.indexBuffer)
                eng.destroyMaterialInstance(seg.material)
            }
            jointSpheres.forEach { js ->
                scene?.removeEntity(js.entity)
                eng.destroyEntity(js.entity)
                eng.destroyVertexBuffer(js.vertexBuffer)
                eng.destroyIndexBuffer(js.indexBuffer)
                eng.destroyMaterialInstance(js.material)
            }
            parts.clear()
            limbSegments.clear()
            jointSpheres.clear()
            if (torsoRootEntity != 0) eng.destroyEntity(torsoRootEntity)
            if (headRootEntity != 0) eng.destroyEntity(headRootEntity)
            if (lightEntity != 0) eng.destroyEntity(lightEntity)
            materialProvider?.destroyMaterials()
            renderer?.let { eng.destroyRenderer(it) }
            view?.let { eng.destroyView(it) }
            scene?.let { eng.destroyScene(it) }
            camera?.let { eng.destroyCameraComponent(it.entity) }
            swapChain?.let { eng.destroySwapChain(it) }
            eng.destroy()
        }
        engine = null
    }

    private fun buildEllipsoidBuffers(
        eng: Engine,
        cx: Float,
        cy: Float,
        cz: Float,
        rx: Float,
        ry: Float,
        rz: Float,
        latBands: Int,
        lonBands: Int,
    ): Triple<VertexBuffer, IndexBuffer, FilamentBox> {
        val vertexCount = (latBands + 1) * (lonBands + 1)
        val posBuf = ByteBuffer.allocateDirect(vertexCount * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val tanBuf = ByteBuffer.allocateDirect(vertexCount * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val uvBuf = ByteBuffer.allocateDirect(vertexCount * 2 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        for (lat in 0..latBands) {
            val theta = lat * PI.toFloat() / latBands
            val sinTheta = sin(theta)
            val cosTheta = cos(theta)
            for (lon in 0..lonBands) {
                val phi = lon * 2f * PI.toFloat() / lonBands
                val nx0 = cos(phi) * sinTheta
                val ny0 = cosTheta
                val nz0 = sin(phi) * sinTheta

                posBuf.put(cx + rx * nx0)
                posBuf.put(cy + ry * ny0)
                posBuf.put(cz + rz * nz0)

                val nLen = sqrt((nx0 / rx) * (nx0 / rx) + (ny0 / ry) * (ny0 / ry) + (nz0 / rz) * (nz0 / rz)).coerceAtLeast(1e-5f)
                val nx = (nx0 / rx) / nLen
                val ny = (ny0 / ry) / nLen
                val nz = (nz0 / rz) / nLen

                val q = normalToTangentQuat(nx, ny, nz)
                tanBuf.put(q[0])
                tanBuf.put(q[1])
                tanBuf.put(q[2])
                tanBuf.put(q[3])

                uvBuf.put(lon.toFloat() / lonBands)
                uvBuf.put(lat.toFloat() / latBands)
            }
        }
        posBuf.flip()
        tanBuf.flip()
        uvBuf.flip()

        val indexCount = latBands * lonBands * 6
        val idxBuf = ByteBuffer.allocateDirect(indexCount * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        for (lat in 0 until latBands) {
            for (lon in 0 until lonBands) {
                val first = (lat * (lonBands + 1) + lon).toShort()
                val second = (first + lonBands + 1).toShort()
                idxBuf.put(first)
                idxBuf.put(second)
                idxBuf.put((first + 1).toShort())
                idxBuf.put(second)
                idxBuf.put((second + 1).toShort())
                idxBuf.put((first + 1).toShort())
            }
        }
        idxBuf.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(3)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 12)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
            .attribute(VertexBuffer.VertexAttribute.UV0, 2, VertexBuffer.AttributeType.FLOAT2, 0, 8)
            .build(eng)
        vb.setBufferAt(eng, 0, posBuf)
        vb.setBufferAt(eng, 1, tanBuf)
        vb.setBufferAt(eng, 2, uvBuf)

        val ib = IndexBuffer.Builder()
            .indexCount(indexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(eng)
        ib.setBuffer(eng, idxBuf)

        val box = FilamentBox(cx, cy, cz, rx, ry, rz)
        return Triple(vb, ib, box)
    }

    private fun normalToTangentQuat(nx: Float, ny: Float, nz: Float): FloatArray {
        val upX = if (kotlin.math.abs(ny) < 0.99f) 0f else 1f
        val upY = if (kotlin.math.abs(ny) < 0.99f) 1f else 0f
        val upZ = 0f

        var tx = upY * nz - upZ * ny
        var ty = upZ * nx - upX * nz
        var tz = upX * ny - upY * nx
        val tLen = hypot(tx, hypot(ty, tz)).coerceAtLeast(1e-5f)
        tx /= tLen; ty /= tLen; tz /= tLen

        val bx = ny * tz - nz * ty
        val by = nz * tx - nx * tz
        val bz = nx * ty - ny * tx

        val trace = tx + by + nz
        val qx: Float
        val qy: Float
        val qz: Float
        val qw: Float
        if (trace > 0f) {
            val s = sqrt(trace + 1f) * 2f
            qw = 0.25f * s
            qx = (bz - ty) / s
            qy = (nx - tz) / s
            qz = (tx - bx) / s
        } else if (tx > by && tx > nz) {
            val s = sqrt(1f + tx - by - nz) * 2f
            qw = (bz - ty) / s
            qx = 0.25f * s
            qy = (ty + bx) / s
            qz = (tz + nx) / s
        } else if (by > nz) {
            val s = sqrt(1f + by - tx - nz) * 2f
            qw = (nx - tz) / s
            qx = (ty + bx) / s
            qy = 0.25f * s
            qz = (ny + bz) / s
        } else {
            val s = sqrt(1f + nz - tx - by) * 2f
            qw = (tx - bx) / s
            qx = (tz + nx) / s
            qy = (ny + bz) / s
            qz = 0.25f * s
        }
        val qLen = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-5f)
        return floatArrayOf(qx / qLen, qy / qLen, qz / qLen, qw / qLen)
    }
}
