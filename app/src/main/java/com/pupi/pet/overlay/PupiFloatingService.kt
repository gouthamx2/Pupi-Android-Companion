
package com.pupi.pet.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.pupi.pet.core.behavior.PupiBehaviorEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

class PupiFloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val engine = PupiBehaviorEngine()

    private val serviceScope =
        CoroutineScope(Dispatchers.Main + Job())

    private var tickerJob: Job? = null
    private var movementJob: Job? = null
    private var jumpJob: Job? = null
    private var pendingTapJob: Job? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isDragging = false
    private var isSecondTapInProgress = false

    private var happyUntil = 0L
    private var cuddleUntil = 0L
    private var sadUntil = 0L

    private var touchDownTime = 0L
    private var touchDownY = 0f

    private var pendingTapZone: String? = null

    private var pupiView: PupiSpriteView? = null
    private lateinit var soundManager: PupiSoundManager

    override fun onCreate() {
        super.onCreate()

        startForegroundServiceNotification()
        soundManager = PupiSoundManager(this)
        setupOverlayView()
        startEngineHeartbeat()
        startAutonomousMovement()

    }

    private fun startForegroundServiceNotification() {
        val channelId = "pupi_companion_channel"

        val channel = NotificationChannel(
            channelId,
            "PUPI Floating Companion",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps PUPI active on your screen"
        }

        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(channel)

        val notification: Notification =
            NotificationCompat.Builder(this, channelId)
                .setContentTitle("PUPI is here!")
                .setContentText("PUPI is keeping you company.")
                .setSmallIcon(android.R.drawable.btn_star)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        startForeground(1001, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlayView() {

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        val density = resources.displayMetrics.density
        val sizePx = (50 * density).toInt()

        layoutParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val container = object : FrameLayout(this) {
            override fun performClick(): Boolean {
                super.performClick()
                return true
            }
        }

        val pupi = PupiSpriteView(this)

        container.addView(
            pupi,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        pupiView = pupi
        pupi.playAnimation("Idle", "South")

        overlayView = container

        container.setOnTouchListener { view, event ->

            val handled = handleTouch(event)

            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }

            handled
        }

        windowManager.addView(container, layoutParams)
    }

    private fun handleTouch(event: MotionEvent): Boolean {

        when (event.action) {

            MotionEvent.ACTION_DOWN -> {

                touchDownTime = SystemClock.uptimeMillis()
                sadUntil = 0L
                pupiView?.clearReaction()
                touchDownY = event.y

                initialX = layoutParams.x
                initialY = layoutParams.y

                initialTouchX = event.rawX
                initialTouchY = event.rawY

                isDragging = false

                // Check whether this is the second tap.
                isSecondTapInProgress =
                    pendingTapJob?.isActive == true

                if (isSecondTapInProgress) {
                    pendingTapJob?.cancel()
                    pendingTapJob = null
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (abs(dx) > 10 || abs(dy) > 10) {

                    if (!isDragging) {

                        jumpJob?.cancel()
                        engine.onDragStart()

                        // If the second gesture becomes a drag,
                        // restore the first tap as a normal single tap.
                        if (isSecondTapInProgress) {

                            val previousZone = pendingTapZone

                            pendingTapZone = null
                            isSecondTapInProgress = false

                            if (previousZone != null) {
                                scheduleSingleTap(previousZone)
                            }
                        }
                    }

                    isDragging = true

                    layoutParams.x = initialX + dx
                    layoutParams.y = initialY + dy

                    clampToBounds()

                    overlayView?.let {
                        windowManager.updateViewLayout(it, layoutParams)
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {

                if (!isDragging) {

                    engine.onPet()

                    val now = SystemClock.uptimeMillis()
                    val touchDuration = now - touchDownTime

                    val touchZone =
                        if (touchDownY < layoutParams.height * 0.4f) {
                            "Head"
                        } else {
                            "Body"
                        }

                    if (touchDuration >= 600L) {

                        // Long press: cuddle.
                        pendingTapJob?.cancel()
                        pendingTapJob = null
                        pendingTapZone = null
                        isSecondTapInProgress = false

                        jumpJob?.cancel()

                        cuddleUntil = now + 5000L
                        happyUntil = 0L

                        pupiView?.playCuddleReaction()
                        soundManager.play("cuddle")

                        Toast.makeText(
                            this,
                            "Pupi loves your cuddles!",
                            Toast.LENGTH_SHORT
                        ).show()

                    } else {

                        if (isSecondTapInProgress) {

                            // Double tap: Happy.
                            pendingTapZone = null
                            isSecondTapInProgress = false

                            performHappyReaction()

                        } else {

                            // Wait before deciding this is a single tap.
                            scheduleSingleTap(touchZone)
                        }
                    }

                } else if (isSecondTapInProgress) {

                    // A second gesture ended as a drag.
                    val previousZone = pendingTapZone

                    pendingTapZone = null
                    isSecondTapInProgress = false

                    if (previousZone != null) {
                        scheduleSingleTap(previousZone)
                    }
                }

                isDragging = false

                return true
            }

            MotionEvent.ACTION_CANCEL -> {

                // Do not lose the first tap if the second gesture
                // is cancelled by the system.
                if (isSecondTapInProgress) {

                    val previousZone = pendingTapZone

                    pendingTapZone = null
                    isSecondTapInProgress = false

                    if (previousZone != null) {
                        scheduleSingleTap(previousZone)
                    }
                }

                isDragging = false

                return true
            }
        }

        return false
    }

    // Delays a single tap so a second tap can be detected.
    private fun scheduleSingleTap(touchZone: String) {

        pendingTapJob?.cancel()

        pendingTapZone = touchZone

        pendingTapJob = serviceScope.launch {

            delay(350L)

            pendingTapZone = null
            pendingTapJob = null

            performSingleTap(touchZone)
        }
    }

    // Handles the normal head tap or body tap.
    private fun performSingleTap(touchZone: String) {

        val now = SystemClock.uptimeMillis()

        jumpJob?.cancel()

        cuddleUntil = 0L

        if (touchZone == "Head") {

            // Keep autonomous movement paused for the jump.
            happyUntil = now + 700L

            val direction = chooseJumpDirection()
            startDirectionalJump(direction)
            soundManager.play("tap_happy")

        } else {

            // Keep autonomous movement paused for the surprise.
            happyUntil = now + 800L

            pupiView?.playSurpriseReaction()
            soundManager.play("tap_surprise")
        }

        val message =
            if (touchZone == "Head") {
                "Pupi loves head pats!"
            } else {
                "Pupi loves belly rubs!"
            }

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()
    }

    // Handles a double tap anywhere on Pupi.
    private fun performHappyReaction() {

        val now = SystemClock.uptimeMillis()

        jumpJob?.cancel()

        cuddleUntil = 0L
        happyUntil = now + 2000L

        pupiView?.playHappyReaction()
        soundManager.play("double_happy")

        Toast.makeText(
            this,
            "Pupi is happy!",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Select a direction where Pupi has enough screen space.
    private fun chooseJumpDirection(): String {

        clampToBounds()

        val density = resources.displayMetrics.density
        val jumpDistance = (64f * density).toInt()

        val metrics = resources.displayMetrics

        val maxX =
            (metrics.widthPixels - layoutParams.width)
                .coerceAtLeast(0)

        val minY = 50

        val maxY =
            (metrics.heightPixels - layoutParams.height - 100)
                .coerceAtLeast(minY)

        val roomLeft = layoutParams.x
        val roomRight = maxX - layoutParams.x
        val roomUp = layoutParams.y - minY
        val roomDown = maxY - layoutParams.y

        val possibleDirections = mutableListOf<String>()

        if (roomLeft >= jumpDistance / 2) {
            possibleDirections.add("West")
        }

        if (roomRight >= jumpDistance / 2) {
            possibleDirections.add("East")
        }

        if (roomUp >= jumpDistance / 2) {
            possibleDirections.add("North")
        }

        if (roomDown >= jumpDistance / 2) {
            possibleDirections.add("South")
        }

        if (possibleDirections.isNotEmpty()) {
            return possibleDirections.random()
        }

        return when (
            maxOf(roomLeft, roomRight, roomUp, roomDown)
        ) {
            roomLeft -> "West"
            roomRight -> "East"
            roomUp -> "North"
            else -> "South"
        }
    }

    // Move Pupi smoothly in the selected direction.
    private fun startDirectionalJump(direction: String) {

        jumpJob?.cancel()

        pupiView?.playDirectionalJump(direction)

        val density = resources.displayMetrics.density
        val jumpDistance = (64f * density).toInt()

        val metrics = resources.displayMetrics

        val maxX =
            (metrics.widthPixels - layoutParams.width)
                .coerceAtLeast(0)

        val minY = 50

        val maxY =
            (metrics.heightPixels - layoutParams.height - 100)
                .coerceAtLeast(minY)

        val startX = layoutParams.x
        val startY = layoutParams.y

        val targetX = when (direction) {
            "East" -> (startX + jumpDistance).coerceIn(0, maxX)
            "West" -> (startX - jumpDistance).coerceIn(0, maxX)
            else -> startX
        }

        val targetY = when (direction) {
            "South" -> (startY + jumpDistance).coerceIn(minY, maxY)
            "North" -> (startY - jumpDistance).coerceIn(minY, maxY)
            else -> startY
        }

        jumpJob = serviceScope.launch {

            val startTime = SystemClock.uptimeMillis()
            val duration = 650L

            while (isActive) {

                val elapsed =
                    SystemClock.uptimeMillis() - startTime

                val progress =
                    (elapsed.toFloat() / duration)
                        .coerceIn(0f, 1f)

                // Smooth acceleration and deceleration.
                val eased =
                    progress * progress * (3f - 2f * progress)

                layoutParams.x =
                    (startX + (targetX - startX) * eased).toInt()

                layoutParams.y =
                    (startY + (targetY - startY) * eased).toInt()

                overlayView?.let {
                    windowManager.updateViewLayout(it, layoutParams)
                }

                if (progress >= 1f) break

                delay(16L)
            }

            layoutParams.x = targetX
            layoutParams.y = targetY

            overlayView?.let {
                windowManager.updateViewLayout(it, layoutParams)
            }
        }
    }

    private fun clampToBounds() {

        val metrics = resources.displayMetrics

        val maxX =
            (metrics.widthPixels - layoutParams.width)
                .coerceAtLeast(0)

        val minY = 50

        val maxY =
            (metrics.heightPixels - layoutParams.height - 100)
                .coerceAtLeast(minY)

        layoutParams.x =
            layoutParams.x.coerceIn(0, maxX)

        layoutParams.y =
            layoutParams.y.coerceIn(minY, maxY)
    }

    private fun startEngineHeartbeat() {
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(15_000L)
                engine.tick(0.25f)

                if (engine.isInactiveFor30Seconds() && sadUntil == 0L) {
                    sadUntil = Long.MAX_VALUE
                    pupiView?.playSadReaction()
                } else if (!engine.isInactiveFor30Seconds()) {
                    sadUntil = 0L
                }
            }
        }
    }
    private fun startAutonomousMovement() {

        movementJob = serviceScope.launch {

            val density = resources.displayMetrics.density

            val speed = 55f * density
            val frameDelay = 32L

            var targetX = layoutParams.x.toFloat()
            var targetY = layoutParams.y.toFloat()

            var walking = false
            var pauseUntil = 0L

            var lastDirection = "South"

            while (isActive) {

                // Pause movement while the user drags Pupi.
                if (isDragging) {

                    targetX = layoutParams.x.toFloat()
                    targetY = layoutParams.y.toFloat()

                    walking = false

                    delay(50L)
                    continue
                }

                val now = SystemClock.uptimeMillis()

                // Pause during cuddle.
                if (now < cuddleUntil) {

                    walking = false

                    delay(50L)
                    continue
                }

                // Pause during tap reactions.
                if (now < happyUntil) {

                    walking = false

                    delay(50L)
                    continue
                }
                if (now < sadUntil) {
                    delay(50L)
                    continue
                }

                // Rest briefly after reaching a destination.
                if (now < pauseUntil) {

                    pupiView?.playAnimation(
                        "Idle",
                        lastDirection
                    )

                    delay(50L)
                    continue
                }

                // Choose a new random destination.
                if (!walking) {

                    val metrics = resources.displayMetrics

                    val maxX =
                        (metrics.widthPixels - layoutParams.width)
                            .coerceAtLeast(0)

                    val minY = 50

                    val maxY =
                        (metrics.heightPixels -
                                layoutParams.height - 100)
                            .coerceAtLeast(minY)

                    targetX =
                        Random.nextInt(maxX + 1).toFloat()

                    targetY =
                        Random.nextInt(minY, maxY + 1).toFloat()

                    walking = true
                }

                val dx = targetX - layoutParams.x
                val dy = targetY - layoutParams.y

                val distance = sqrt(dx * dx + dy * dy)

                // Stop and play idle at the destination.
                if (distance < 8f) {

                    walking = false

                    pauseUntil =
                        now + Random.nextLong(700L, 1300L)

                    pupiView?.playAnimation(
                        "Idle",
                        lastDirection
                    )

                    delay(frameDelay)
                    continue
                }

                // Select the closest available sprite direction.
                lastDirection = when {
                    abs(dx) > abs(dy) && dx > 0 -> "East"
                    abs(dx) > abs(dy) && dx < 0 -> "West"
                    dy > 0 -> "South"
                    else -> "North"
                }

                pupiView?.playAnimation(
                    "Walk",
                    lastDirection
                )

                // Move along both axes, including diagonally.
                val step = minOf(
                    speed * frameDelay / 1000f,
                    distance
                )

                val moveX = dx / distance * step
                val moveY = dy / distance * step

                layoutParams.x =
                    (layoutParams.x + moveX).toInt()

                layoutParams.y =
                    (layoutParams.y + moveY).toInt()

                clampToBounds()

                overlayView?.let {
                    windowManager.updateViewLayout(it, layoutParams)
                }

                delay(frameDelay)
            }
        }
    }

    override fun onDestroy() {

        pendingTapJob?.cancel()
        jumpJob?.cancel()
        movementJob?.cancel()
        tickerJob?.cancel()

        serviceScope.cancel()

        overlayView?.let {
            windowManager.removeView(it)
        }

        overlayView = null
        pupiView = null
        soundManager.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
