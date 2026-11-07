
package com.pupi.pet.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import org.json.JSONObject
import kotlin.math.*

class PupiSpriteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class SpriteFrame(
        val source: Rect,
        val duration: Long,
        val index: Int
    )

    private enum class Reaction {
        NONE,
        JUMP,
        SURPRISE,
        HAPPY,
        CUDDLE
    }

    private var spriteSheet: Bitmap? = null

    private val animations =
        mutableMapOf<String, MutableList<SpriteFrame>>()

    private var currentAction = "Idle"
    private var currentDirection = "South"

    private var frameIndex = 0
    private var nextFrameAt = 0L

    private var currentReaction = Reaction.NONE
    private var reactionStartTime = 0L
    private var reactionDuration = 0L
    private var reactionFrameIndex = 0

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    // Surprise expression paints.
    private val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(75, 42, 30)
        style = Paint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val eyeHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(35, 25, 20)
        style = Paint.Style.FILL
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(55, 30, 25)
        style = Paint.Style.FILL
    }

    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(245, 115, 135)
        style = Paint.Style.FILL
    }

    // Happy expression paints.
    private val happyEyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(55, 30, 25)
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
    }

    private val happyMouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(75, 30, 30)
        style = Paint.Style.FILL
    }

    private val happyTonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(245, 115, 135)
        style = Paint.Style.FILL
    }

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        loadSprites()
    }

    private fun loadSprites() {
        try {
            spriteSheet = context.assets.open(
                "puppy-shiba_64px_scroller_outline_lit.png"
            ).use {
                BitmapFactory.decodeStream(it)
            }

            val json = context.assets.open(
                "puppy-shiba_64px_scroller.json"
            ).bufferedReader().use {
                it.readText()
            }

            val frames = JSONObject(json).getJSONArray("frames")

            for (i in 0 until frames.length()) {

                val item = frames.getJSONObject(i)
                val frame = item.getJSONObject("frame")
                val details = item.getJSONObject("pixor")

                val action = details.getString("action")
                val direction = details.getString("side")
                val index = details.getInt("index")

                val spriteFrame = SpriteFrame(
                    source = Rect(
                        frame.getInt("x"),
                        frame.getInt("y"),
                        frame.getInt("x") + frame.getInt("w"),
                        frame.getInt("y") + frame.getInt("h")
                    ),
                    duration = item.optLong("duration", 83L)
                        .coerceAtLeast(16L),
                    index = index
                )

                val key = "${action}_${direction}"

                animations.getOrPut(key) {
                    mutableListOf()
                }.add(spriteFrame)
            }

            animations.values.forEach { animation ->
                animation.sortBy { it.index }
            }

            Log.d(
                "PupiSprite",
                "Loaded animations: ${animations.keys}"
            )

            invalidate()

        } catch (e: Exception) {

            Log.e(
                "PupiSprite",
                "Failed to load puppy sprites",
                e
            )
        }
    }

    fun playAnimation(
        action: String,
        direction: String = "South"
    ) {

        val key = "${action}_${direction}"
        val frames = animations[key]

        if (frames.isNullOrEmpty()) {
            Log.w("PupiSprite", "Animation not found: $key")
            return
        }

        if (
            currentAction == action &&
            currentDirection == direction
        ) {
            return
        }

        currentAction = action
        currentDirection = direction

        frameIndex = 0

        nextFrameAt =
            SystemClock.uptimeMillis() + frames[0].duration

        invalidate()
    }

    // Head tap: directional jump.
    fun playDirectionalJump(direction: String) {
        playAnimation("Idle", direction)
        playGentleJump()
    }

    // Head tap reaction: 700 milliseconds.
    fun playGentleJump() {
        startReaction(Reaction.JUMP, 700L)
    }

    // Body tap: surprise reaction for 800 milliseconds.
    fun playSurpriseReaction() {
        playAnimation("Idle", "South")
        startReaction(Reaction.SURPRISE, 800L)
    }

    // Double tap: happy expression for 3 seconds.
    fun playHappyReaction() {
        playAnimation("Idle", "South")
        startReaction(Reaction.HAPPY, 2000L)
    }

    // Long press: relaxed cuddle for 5 seconds.
    fun playCuddleReaction() {
        playAnimation("Sleep", "South")
        startReaction(Reaction.CUDDLE, 5000L)
    }

    private fun startReaction(
        reaction: Reaction,
        duration: Long
    ) {

        currentReaction = reaction
        reactionStartTime = SystemClock.uptimeMillis()
        reactionDuration = duration

        // Remember the exact frame at the start of the reaction.
        reactionFrameIndex = frameIndex

        invalidate()
    }

    private fun getReactionProgress(): Float {

        if (currentReaction == Reaction.NONE) {
            return 1f
        }

        val elapsed =
            SystemClock.uptimeMillis() - reactionStartTime

        if (elapsed >= reactionDuration) {

            currentReaction = Reaction.NONE

            invalidate()

            return 1f
        }

        return (elapsed.toFloat() / reactionDuration)
            .coerceIn(0f, 1f)
    }

    private fun updateSpriteFrame(
        frames: List<SpriteFrame>,
        now: Long
    ) {

        if (nextFrameAt == 0L) {
            nextFrameAt =
                now + frames[frameIndex].duration
        }

        while (now >= nextFrameAt) {

            frameIndex = (frameIndex + 1) % frames.size

            nextFrameAt += frames[frameIndex].duration
        }
    }

    // Draw the surprised eyes and open mouth.
    private fun drawSurprisedFace(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float
    ) {

        // 1. Eye positions.
        val leftEyeX = left + size * 0.40f
        val rightEyeX = left + size * 0.60f
        val eyeY = top + size * 0.44f

        val eyeRadius = size * 0.038f
        val pupilRadius = size * 0.022f

        // 2. White eyes.
        canvas.drawCircle(leftEyeX, eyeY, eyeRadius, eyeWhitePaint)
        canvas.drawCircle(rightEyeX, eyeY, eyeRadius, eyeWhitePaint)

        // 3. Dark pupils.
        canvas.drawCircle(leftEyeX, eyeY, pupilRadius, pupilPaint)
        canvas.drawCircle(rightEyeX, eyeY, pupilRadius, pupilPaint)

        // 4. Main eye reflections.
        val highlightRadius = size * 0.009f

        canvas.drawCircle(
            leftEyeX - size * 0.008f,
            eyeY - size * 0.010f,
            highlightRadius,
            eyeHighlightPaint
        )

        canvas.drawCircle(
            rightEyeX - size * 0.008f,
            eyeY - size * 0.010f,
            highlightRadius,
            eyeHighlightPaint
        )

        // 5. Small secondary reflections.
        val smallHighlightRadius = size * 0.004f

        canvas.drawCircle(
            leftEyeX + size * 0.009f,
            eyeY + size * 0.009f,
            smallHighlightRadius,
            eyeHighlightPaint
        )

        canvas.drawCircle(
            rightEyeX + size * 0.009f,
            eyeY + size * 0.009f,
            smallHighlightRadius,
            eyeHighlightPaint
        )

        // 6. Raised surprised eyebrows.
        val leftBrow = android.graphics.Path().apply {

            moveTo(left + size * 0.34f, eyeY - size * 0.05f)

            quadTo(
                left + size * 0.40f,
                eyeY - size * 0.13f,
                left + size * 0.46f,
                eyeY - size * 0.05f
            )
        }

        val rightBrow = android.graphics.Path().apply {

            moveTo(left + size * 0.54f, eyeY - size * 0.05f)

            quadTo(
                left + size * 0.60f,
                eyeY - size * 0.13f,
                left + size * 0.66f,
                eyeY - size * 0.05f
            )
        }

        canvas.drawPath(leftBrow, eyebrowPaint)
        canvas.drawPath(rightBrow, eyebrowPaint)

        // 7. Open surprised mouth.
        val mouthLeft = left + size * 0.475f
        val mouthTop = top + size * 0.55f
        val mouthRight = left + size * 0.525f
        val mouthBottom = top + size * 0.61f

        canvas.drawOval(
            mouthLeft,
            mouthTop,
            mouthRight,
            mouthBottom,
            mouthPaint
        )

        // 8. Tongue.
        canvas.drawOval(
            left + size * 0.485f,
            top + size * 0.59f,
            left + size * 0.515f,
            top + size * 0.605f,
            tonguePaint
        )
    }

    private fun drawHappyFace(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float
    ) {

        val leftEyeX = left + size * 0.40f
        val rightEyeX = left + size * 0.60f
        val eyeY = top + size * 0.44f

        // Closed smiling eyes.
        val leftEye = android.graphics.Path().apply {

            moveTo(leftEyeX - size * 0.035f, eyeY)

            quadTo(
                leftEyeX,
                eyeY + size * 0.035f,
                leftEyeX + size * 0.035f,
                eyeY
            )
        }

        val rightEye = android.graphics.Path().apply {

            moveTo(rightEyeX - size * 0.035f, eyeY)

            quadTo(
                rightEyeX,
                eyeY + size * 0.035f,
                rightEyeX + size * 0.035f,
                eyeY
            )
        }

        canvas.drawPath(leftEye, happyEyePaint)
        canvas.drawPath(rightEye, happyEyePaint)

        // Happy open smile.
        canvas.drawOval(
            left + size * 0.46f,
            top + size * 0.53f,
            left + size * 0.54f,
            top + size * 0.60f,
            happyMouthPaint
        )

        // Pink tongue.
        canvas.drawOval(
            left + size * 0.475f,
            top + size * 0.575f,
            left + size * 0.525f,
            top + size * 0.595f,
            happyTonguePaint
        )
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        val bitmap = spriteSheet ?: return

        val key = "${currentAction}_${currentDirection}"
        val frames = animations[key] ?: return

        if (frames.isEmpty()) return

        val now = SystemClock.uptimeMillis()

        // Freeze the sprite during jump, surprise and happy reactions.
        if (
            currentReaction != Reaction.JUMP &&
            currentReaction != Reaction.SURPRISE &&
            currentReaction != Reaction.HAPPY
        ) {
            updateSpriteFrame(frames, now)
        }

        // Use the captured frame while the reaction is active.
        val currentFrame =
            if (
                currentReaction == Reaction.JUMP ||
                currentReaction == Reaction.SURPRISE ||
                currentReaction == Reaction.HAPPY
            ) {

                frames[
                    reactionFrameIndex.coerceIn(
                        0,
                        frames.lastIndex
                    )
                ]

            } else {

                frames[frameIndex.coerceIn(0, frames.lastIndex)]
            }

        val size = min(width, height).toFloat()

        val left = (width - size) / 2f
        val top = (height - size) / 2f

        val centerX = width / 2f
        val centerY = height / 2f

        getReactionProgress()

        val elapsed = now - reactionStartTime

        canvas.save()

        when (currentReaction) {

            Reaction.JUMP -> {

                val hopDuration = 650f

                val hopProgress =
                    (elapsed / hopDuration).coerceIn(0f, 1f)

                val hopHeight =
                    sin(hopProgress * PI).toFloat()

                canvas.translate(
                    0f,
                    -hopHeight * size * 0.10f
                )

                if (elapsed in 500L..650L) {

                    val landingProgress =
                        ((elapsed - 500L) / 150f)
                            .coerceIn(0f, 1f)

                    val squash =
                        sin(landingProgress * PI).toFloat() * 0.06f

                    canvas.scale(
                        1f + squash,
                        1f - squash,
                        centerX,
                        centerY
                    )
                }
            }

            Reaction.SURPRISE -> {

                // Quick pop, then hold still.
                val popProgress =
                    (elapsed / 180f).coerceIn(0f, 1f)

                val popScale =
                    if (elapsed < 180L) {
                        0.92f + popProgress * 0.10f
                    } else {
                        1.02f
                    }

                canvas.scale(
                    popScale,
                    popScale,
                    centerX,
                    centerY
                )
            }

            Reaction.CUDDLE -> {

                val breathing = sin(
                    2.0 * PI * elapsed / 2800.0
                ).toFloat()

                val scale = 1f + breathing * 0.004f

                canvas.scale(
                    scale,
                    scale,
                    centerX,
                    centerY
                )
            }

            Reaction.HAPPY -> {

                val bounce =
                    sin(elapsed / 180f * PI).toFloat() * 0.025f

                canvas.translate(
                    0f,
                    -abs(bounce) * size
                )
            }

            Reaction.NONE -> Unit
        }

        // Draw original puppy sprite.
        canvas.drawBitmap(
            bitmap,
            currentFrame.source,
            RectF(
                left,
                top,
                left + size,
                top + size
            ),
            paint
        )

        // Draw expressions over the puppy sprite.
        when (currentReaction) {

            Reaction.SURPRISE -> {
                drawSurprisedFace(canvas, left, top, size)
            }

            Reaction.HAPPY -> {
                drawHappyFace(canvas, left, top, size)
            }

            else -> Unit
        }

        canvas.restore()

        // Active reactions redraw at approximately 60 FPS.
        val redrawDelay =
            if (currentReaction != Reaction.NONE) {
                16L
            } else {
                (nextFrameAt - SystemClock.uptimeMillis())
                    .coerceAtLeast(16L)
            }

        postInvalidateDelayed(redrawDelay)
    }
}
