package app.orchard.tv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.media3.common.MimeTypes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/** How much of the baked frosted glass a panel draws. */
enum class GlassLevel(val key: String) {
    High("high"), Low("low"), Off("off");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: Low
    }
}

/**
 * Tablet performance switches. A Mali-G57 is happy with one baked glass layer but not with the
 * two-layer frost crossfade plus a 4K/Dolby Vision film behind it, so the defaults are
 * conservative: 1080p SDR, 30 fps, single-pass glass, reduced animation.
 */
object PerfSettings {
    private const val PREFS = "settings"
    private const val K_VIDEO = "tab_video_cap"
    private const val K_AUTO_BITRATE = "tab_auto_bitrate"
    private const val K_GLASS = "tab_glass_level"
    private const val K_LOW_ANIMATION = "tab_low_animation"

    /** Force 1080p / 30 fps / SDR and cap the stream's bitrate. */
    var videoCap by mutableStateOf(true); private set

    /** Step the bitrate cap down when the film stalls or a decoder errors. */
    var autoBitrate by mutableStateOf(true); private set

    /** High = the full two-layer frosted glass; Low = one flat layer; Off = solid colour. */
    var glassLevel by mutableStateOf(GlassLevel.Low); private set

    /** Cuts continuous animations and collapses transitions to short fades. */
    var lowAnimation by mutableStateOf(true); private set

    /** Current ceiling for the video track, lowered automatically when the network struggles. */
    var bitrateCap by mutableStateOf(8_000_000); private set

    private lateinit var app: Context

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        videoCap = p.getBoolean(K_VIDEO, true)
        autoBitrate = p.getBoolean(K_AUTO_BITRATE, true)
        glassLevel = GlassLevel.of(p.getString(K_GLASS, null))
        lowAnimation = p.getBoolean(K_LOW_ANIMATION, true)
    }

    private fun save(block: android.content.SharedPreferences.Editor.() -> Unit) =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { this.block() }

    fun chooseVideoCap(on: Boolean) {
        videoCap = on
        save { putBoolean(K_VIDEO, on) }
    }

    fun chooseAutoBitrate(on: Boolean) {
        autoBitrate = on
        if (on) bitrateCap = 8_000_000
        save { putBoolean(K_AUTO_BITRATE, on) }
    }

    fun chooseGlass(level: GlassLevel) {
        glassLevel = level
        save { putString(K_GLASS, level.key) }
    }

    fun chooseLowAnimation(on: Boolean) {
        lowAnimation = on
        save { putBoolean(K_LOW_ANIMATION, on) }
    }

    /**
     * Applies the tablet video limits to a player. Called for the aerial wallpaper, the aerial
     * screensaver and the owner's local video wallpapers alike. H.264 is SDR, so preferring it
     * keeps HEVC / Dolby Vision (HDR) streams out; the 1920x1080 / 30 fps / bitrate ceilings are
     * the belt-and-braces in case a source exposes several tracks.
     */
    fun applyVideoCaps(player: ExoPlayer) {
        if (!videoCap) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(1920, 1080)
            .setMaxVideoFrameRate(30)
            .setMaxVideoBitrate(bitrateCap)
            .setPreferredVideoMimeType(MimeTypes.VIDEO_H264)
            .build()
        // Do not ask the panel to switch refresh rates: a fixed 30/60 Hz panel is cheaper and
        // avoids the black frame a mode switch puts between the wallpaper and the screensaver.
        player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
    }

    /** A stall or decoder error: 8 -> 5 -> 3 Mbps. The cap is picked up on the next player. */
    fun onBuffering() {
        if (!autoBitrate) return
        bitrateCap = when {
            bitrateCap > 5_000_000 -> 5_000_000
            bitrateCap > 3_000_000 -> 3_000_000
            else -> 3_000_000
        }
    }
}
