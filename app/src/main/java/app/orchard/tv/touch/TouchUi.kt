package app.orchard.tv.touch

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * losve-tab is a tablet launcher: all TV focus/D-pad routing is switched off on a touch device.
 *
 * The decision uses both signals Android gives us:
 *  - the device's configuration / package features (a tablet always has a touchscreen), and
 *  - [View.isInTouchMode], so a tablet that momentarily reports "not in touch mode" (a paired
 *    keyboard or mouse) still keeps the touch UI, while a TV with no touch hardware keeps its
 *    D-pad focus so the original behaviour is not lost on a set-top box.
 *
 * Set once from MainActivity.onCreate before the first composition. `focusEnabled` is read by the
 * few controls that still carry a focusable node; on a tablet it is always false.
 */
object TouchUi {
    /** True on a tablet: taps, long presses and drags; no focus ring, no D-pad routing. */
    @Volatile
    var touchMode = true

    /** The last value of [View.isInTouchMode], kept for Diagnostics. */
    @Volatile
    var inTouchMode = true

    /** Remaining focus nodes are disabled in touch mode. */
    val focusEnabled: Boolean get() = !touchMode

    fun detect(context: Context, decor: View?) {
        val hardware = context.resources.configuration.touchscreen != Configuration.TOUCHSCREEN_NOTOUCH ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        inTouchMode = decor?.isInTouchMode ?: hardware
        touchMode = hardware || inTouchMode
    }
}

/** Fingers need ~9 mm; on the 960x540 design canvas 48 dp is that much. */
val MinFingerTarget: Dp = 48.dp

/** Grows a small control to a finger-sized hit box without changing its own drawing. */
fun Modifier.fingerTarget(min: Dp = MinFingerTarget): Modifier = this.sizeIn(minWidth = min, minHeight = min)

/** How far a finger has to travel after a long press before it counts as a drag, not a menu. */
const val DRAG_SLOP_PX = 18f
