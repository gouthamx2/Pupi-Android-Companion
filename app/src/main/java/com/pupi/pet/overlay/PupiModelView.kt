package com.pupi.pet.overlay

import android.content.Context
import android.content.res.AssetManager
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

/** STATUS: WRITTEN, NOT COMPILED, NOT TESTED. */
class PupiModelView(context: Context) : TextureView(context), Choreographer.FrameCallback {

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val filamentView: View
    private val camera: Camera
    private val uiHelper: UiHelper
    private var swapChain: SwapChain? = null

    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null
    private var asset: FilamentAsset? = null
    private var lightEntity = 0
    private var indirectLight: IndirectLight? = null

    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var startNs = 0L
    private var lastFrameNs = 0L
    private var animationIndex = 0

    init {
        Filament.init()
        Gltfio.init()

        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        filamentView = engine.createView()
        camera = engine.createCamera(engine.entityManager.create())

        filamentView.camera = camera
        filamentView.scene = scene
        filamentView.blendMode = View.BlendMode.TRANSLUCENT
        renderer.clearOptions = renderer.clearOptions.apply { clear = true }

        isOpaque = false

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.isOpaque = false
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                filamentView.viewport = Viewport(0, 0, width, height)
                camera.setProjection(
                    35.0, width.toDouble() / height.toDouble(), 0.1, 50.0,
                    Camera.Fov.VERTICAL
                )
            }
        }
        uiHelper.attachTo(this)

        camera.lookAt(0.0, 0.0, 4.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        setupLights()
    }

    private fun setupLights() {
        lightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(110_000.0f)
            .direction(0.0f, -1.0f, -1.0f)
            .castShadows(false)
            .build(engine, lightEntity)
        scene.addEntity(lightEntity)

        indirectLight = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.8f, 0.8f, 0.8f))
            .intensity(30_000.0f)
            .build(engine)
        scene.indirectLight = indirectLight
    }

    /** Returns true if the model was loaded. */
    fun loadModel(assets: AssetManager, path: String): Boolean {
        return try {
            val bytes = assets.open(path).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes)

            val loader = AssetLoader(engine, UbershaderProvider(engine), EntityManager.get())
            val resLoader = ResourceLoader(engine)
            val loaded = loader.createAsset(buffer) ?: return false
            resLoader.loadResources(loaded)
            loaded.releaseSourceData()

            scene.addEntities(loaded.entities)
            fitToView(loaded)

            assetLoader = loader
            resourceLoader = resLoader
            asset = loaded
            true
        } catch (e: Exception) {
            android.util.Log.e("PupiModelView", "Could not load $path", e)
            false
        }
    }

    private fun fitToView(a: FilamentAsset) {
        val box = a.boundingBox
        val c = box.center
        val h = box.halfExtent
        val maxHalf = maxOf(h[0], maxOf(h[1], h[2]))
        val s = if (maxHalf > 0f) 0.85f / maxHalf else 1.0f

        // Rotate ~45 degrees around Y axis so the whole dog (face, body, tail, and legs)
        // is framed in a 3/4 isometric perspective instead of being seen head-on (which
        // occludes the entire rear half of the dog's body).
        val rad = Math.toRadians(45.0)
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()

        val cx = c[0]
        val cy = c[1]
        val cz = c[2]

        val tx = -s * (cos * cx + sin * cz)
        val ty = -s * cy
        val tz = -s * (-sin * cx + cos * cz)

        // Column-major 4x4 matrix for Filament:
        val m = floatArrayOf(
            s * cos,  0f, -s * sin, 0f,
            0f,       s,   0f,      0f,
            s * sin,  0f,  s * cos, 0f,
            tx,       ty,  tz,      1f
        )
        val tm = engine.transformManager
        tm.setTransform(tm.getInstance(a.root), m)
    }

    fun animationNames(): List<String> {
        val animator = asset?.instance?.animator ?: return emptyList()
        return (0 until animator.animationCount).map { animator.getAnimationName(it) }
    }

    fun playAnimation(index: Int) {
        animationIndex = index
        startNs = System.nanoTime()
    }

    fun start() {
        if (running) return
        running = true
        startNs = System.nanoTime()
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        choreographer.postFrameCallback(this)

        if (frameTimeNanos - lastFrameNs < 33_000_000L) return
        lastFrameNs = frameTimeNanos

        asset?.instance?.animator?.let { animator ->
            if (animator.animationCount > 0) {
                val idx = animationIndex.coerceIn(0, animator.animationCount - 1)
                val dur = animator.getAnimationDuration(idx)
                if (dur > 0f) {
                    val t = ((System.nanoTime() - startNs) / 1_000_000_000.0).toFloat() % dur
                    animator.applyAnimation(idx, t)
                }
                animator.updateBoneMatrices()
            }
        }

        val sc = swapChain ?: return
        if (uiHelper.isReadyToRender && renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(filamentView)
            renderer.endFrame()
        }
    }

    fun destroy() {
        stop()
        uiHelper.detach()
        asset?.let { a ->
            scene.removeEntities(a.entities)
            assetLoader?.destroyAsset(a)
        }
        resourceLoader?.destroy()
        assetLoader?.destroy()
        scene.removeEntity(lightEntity)
        indirectLight?.let { engine.destroyIndirectLight(it) }
        engine.destroyRenderer(renderer)
        engine.destroyView(filamentView)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(camera.entity)
        engine.destroy()
    }
}