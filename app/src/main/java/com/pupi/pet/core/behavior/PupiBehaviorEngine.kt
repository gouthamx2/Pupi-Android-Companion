package com.pupi.pet.core.behavior

import com.pupi.pet.core.model.PetAttributes
import com.pupi.pet.core.model.PupiBehaviorState
import com.pupi.pet.core.model.PupiEmotion
import kotlin.random.Random

/**
 * Deterministic, rule-based Behaviour and Emotion Engine for Pupi.
 * Handles attribute decay over time, natural state transitions, and responsive reactions.
 */
class PupiBehaviorEngine(
    private var attributes: PetAttributes = PetAttributes(),
    private val random: Random = Random.Default
) {
    private var currentState: PupiBehaviorState = PupiBehaviorState.IDLE_BREATHE
    private var currentEmotion: PupiEmotion = PupiEmotion.NEUTRAL
    private var lastInteractionTimestamp: Long = System.currentTimeMillis()

    fun getAttributes(): PetAttributes = attributes
    fun getCurrentState(): PupiBehaviorState = currentState
    fun getCurrentEmotion(): PupiEmotion = currentEmotion
    fun isInactiveFor30Seconds(): Boolean {
        return System.currentTimeMillis() - lastInteractionTimestamp >= 30_000L
    }

    /**
     * Regular heartbeat tick (called periodically, e.g. every 10-30 seconds).
     * Simulates gentle life decay and evaluates next idle animation.
     */
    fun tick(elapsedMinutes: Float): PupiBehaviorState {
        // Natural gradual decay
        val hungerLoss = elapsedMinutes * 0.5f
        val energyLoss = if (currentState == PupiBehaviorState.SLEEPING) -elapsedMinutes * 2.0f else elapsedMinutes * 0.4f
        val happinessLoss = elapsedMinutes * 0.3f
        val cleanlinessLoss = elapsedMinutes * 0.2f

        attributes = attributes.copy(
            hunger = attributes.hunger - hungerLoss,
            energy = attributes.energy - energyLoss,
            happiness = attributes.happiness - happinessLoss,
            cleanliness = attributes.cleanliness - cleanlinessLoss
        ).clamp()

        updateEmotion()
        selectNextIdleState()
        return currentState
    }

    private fun updateEmotion() {
        currentEmotion = when {
            attributes.energy < 20f -> PupiEmotion.SLEEPY
            attributes.hunger < 25f -> PupiEmotion.HUNGRY
            attributes.happiness > 75f && attributes.hunger > 50f -> PupiEmotion.HAPPY
            attributes.happiness < 30f -> PupiEmotion.SAD
            attributes.cleanliness < 20f -> PupiEmotion.ANGRY // grumpy when dirty
            else -> PupiEmotion.NEUTRAL
        }
    }

    private fun selectNextIdleState() {
        if (currentState == PupiBehaviorState.SLEEPING) {
            if (attributes.energy > 90f) {
                currentState = PupiBehaviorState.WAKING_UP
            }
            return
        }

        if (attributes.energy < 15f) {
            currentState = PupiBehaviorState.SLEEPING
            return
        }

        // Varied idle behaviours
        val roll = random.nextInt(100)
        currentState = when {
            roll < 25 -> PupiBehaviorState.IDLE_BREATHE
            roll < 45 -> PupiBehaviorState.LOOKING_AROUND
            roll < 60 -> PupiBehaviorState.HEAD_TILTING
            roll < 75 -> PupiBehaviorState.TAIL_WAGGING
            roll < 88 -> PupiBehaviorState.SITTING
            roll < 95 -> PupiBehaviorState.STRETCHING
            else -> PupiBehaviorState.WALKING
        }
    }

    // --- Interactive User Triggered Actions ---

    fun onPet(): PupiBehaviorState {
        lastInteractionTimestamp = System.currentTimeMillis()
        attributes = attributes.copy(
            happiness = attributes.happiness + 8f,
            friendship = attributes.friendship + 1.5f
        ).clamp()
        currentEmotion = PupiEmotion.HAPPY
        currentState = PupiBehaviorState.BEING_PETTED
        return currentState
    }

    fun onFeed(foodValue: Float = 25f): PupiBehaviorState {
        lastInteractionTimestamp = System.currentTimeMillis()
        attributes = attributes.copy(
            hunger = attributes.hunger + foodValue,
            happiness = attributes.happiness + 5f
        ).clamp()
        currentEmotion = PupiEmotion.HAPPY
        currentState = PupiBehaviorState.EATING
        return currentState
    }

    fun onPlay(intensity: Float = 1.0f): PupiBehaviorState {
        lastInteractionTimestamp = System.currentTimeMillis()
        attributes = attributes.copy(
            happiness = attributes.happiness + (15f * intensity),
            energy = attributes.energy - (10f * intensity),
            friendship = attributes.friendship + 2f
        ).clamp()
        currentEmotion = PupiEmotion.HAPPY
        currentState = PupiBehaviorState.PLAYING
        return currentState
    }

    fun onClean(): PupiBehaviorState {
        lastInteractionTimestamp = System.currentTimeMillis()
        attributes = attributes.copy(
            cleanliness = 100f,
            happiness = attributes.happiness + 6f
        ).clamp()
        currentEmotion = PupiEmotion.HAPPY
        currentState = PupiBehaviorState.STRETCHING
        return currentState
    }

    fun onSleep(): PupiBehaviorState {
        currentState = PupiBehaviorState.SLEEPING
        currentEmotion = PupiEmotion.SLEEPY
        return currentState
    }

    fun onDragStart(): PupiBehaviorState {
        lastInteractionTimestamp = System.currentTimeMillis()
        currentState = PupiBehaviorState.JUMPING
        return currentState
    }
}
