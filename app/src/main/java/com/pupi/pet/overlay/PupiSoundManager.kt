
package com.pupi.pet.overlay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class PupiSoundManager(context: Context) {

    private val soundPool: SoundPool
    private val sounds = mutableMapOf<String, Int>()

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()

        loadSound(context, "tap_happy")
        loadSound(context, "tap_surprise")
        loadSound(context, "double_happy")
        loadSound(context, "cuddle")

    }

    private fun loadSound(context: Context, name: String) {
        val id = context.resources.getIdentifier(
            name, "raw", context.packageName
        )

        if (id != 0) {
            sounds[name] = soundPool.load(context, id, 1)
        }
    }

    fun play(name: String) {
        sounds[name]?.let {
            soundPool.play(it, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
        sounds.clear()
    }
}
