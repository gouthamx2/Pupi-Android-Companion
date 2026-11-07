package com.pupi.pet.core.model

/**
 * Core stats for Pupi. Each attribute is bounded between 0 and 100.
 */
data class PetAttributes(
    val hunger: Float = 80.0f,      // 100 = completely full, 0 = starving
    val happiness: Float = 85.0f,   // 100 = ecstatic, 0 = very depressed
    val energy: Float = 90.0f,      // 100 = wide awake, 0 = exhausted
    val friendship: Float = 50.0f,  // 100 = best friends, 0 = unfamiliar
    val cleanliness: Float = 85.0f, // 100 = spotless, 0 = dirty
    val curiosity: Float = 70.0f    // 100 = hyper-inquisitive, 0 = lethargic
) {
    fun clamp(): PetAttributes = copy(
        hunger = hunger.coerceIn(0f, 100f),
        happiness = happiness.coerceIn(0f, 100f),
        energy = energy.coerceIn(0f, 100f),
        friendship = friendship.coerceIn(0f, 100f),
        cleanliness = cleanliness.coerceIn(0f, 100f),
        curiosity = curiosity.coerceIn(0f, 100f)
    )
}

enum class PupiEmotion {
    HAPPY,
    SAD,
    ANGRY,
    SURPRISED,
    SLEEPY,
    HUNGRY,
    NEUTRAL
}

enum class PupiBehaviorState {
    IDLE_BREATHE,
    WALKING,
    RUNNING,
    SITTING,
    LYING_DOWN,
    SLEEPING,
    WAKING_UP,
    STRETCHING,
    JUMPING,
    TAIL_WAGGING,
    LOOKING_AROUND,
    HEAD_TILTING,
    EATING,
    DRINKING,
    PLAYING,
    BEING_PETTED
}
