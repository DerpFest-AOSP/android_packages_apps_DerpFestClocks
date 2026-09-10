/*
 * SPDX-FileCopyrightText: FundamentalOS
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.derpfest.clocks.words

import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import androidx.constraintlayout.widget.ConstraintSet.BOTTOM
import androidx.constraintlayout.widget.ConstraintSet.END
import androidx.constraintlayout.widget.ConstraintSet.PARENT_ID
import androidx.constraintlayout.widget.ConstraintSet.START
import androidx.constraintlayout.widget.ConstraintSet.TOP
import androidx.constraintlayout.widget.ConstraintSet.WRAP_CONTENT
import org.derpfest.clocks.AssetLoader
import com.android.systemui.customization.clocks.view.DefaultClockFaceLayout
import com.android.systemui.customization.clocks.R as clocksR
import com.android.systemui.customization.clocks.utils.ContextUtils.getSafeStatusBarHeight
import com.android.systemui.plugins.keyguard.ui.clocks.AodClockBurnInModel
import com.android.systemui.plugins.keyguard.ui.clocks.ClockPreviewConfig
import com.android.systemui.plugins.keyguard.ui.clocks.ClockViewIds
import kotlin.math.roundToInt

/**
 * Pins the large word clock to the start of the lockscreen, a fixed distance below the keyguard's
 * small clock guideline. Scene-container hosts [Clock.Large] through
 * [DefaultClockFaceLayout];
 */
class WordClockFaceLayoutLarge(
    view: WordClockViewLarge,
    private val assets: AssetLoader,
    private val context: Context,
    private val resources: Resources = context.resources,
) : DefaultClockFaceLayout(view) {
    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyConstraints(constraints: ConstraintSet): ConstraintSet {
        val large = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
        val small = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL
        pinTopStart(constraints, resources, fallbackTop())
        // Both faces are positioned from the keyguard's small clock guideline. The keyguard hangs
        // the date row, and below it the notifications, off the small clock container; hang that
        // container off this face instead. When the small face shows, this face is gone and
        // collapses onto the guideline, so the small container lands exactly where the keyguard
        // put it.
        val guideline = assets.getResourcesId(SMALL_CLOCK_GUIDELINE_TOP)
        val guideBegin = constraints.getConstraint(guideline)?.layout?.guideBegin ?: -1
        // The keyguard centres its large-clock date row (date / weather / alarm) under the clock
        // container with a packed chain. The words are start-aligned, so start-align the row with
        // them, and hang the small container off the row rather than off the words: the smartspace
        // cards follow the small container, and would otherwise land on top of the date. Only when
        // the keyguard has put the row under this face (smartspace can be off, and at large font
        // scales the keyguard keeps the date beside the small clock instead).
        val dateRow = resolveIdOrZero(assets, DATE_SMARTSPACE_VIEW_LARGE)
        val dateRowBelowWords =
            constraints.getConstraint(dateRow)?.let {
                it.propertySet.visibility == View.VISIBLE && it.layout.topToBottom == large
            } ?: false
        if (dateRowBelowWords) {
            constraints.setHorizontalBias(dateRow, 0f)
        }
        if (guideline != 0 && guideBegin >= 0) {
            constraints.connect(large, TOP, guideline, BOTTOM, topOffset(resources))
            constraints.connect(small, TOP, if (dateRowBelowWords) dateRow else large, BOTTOM)
        }
        return constraints
    }

    /** Where the small clock guideline sits when the constraint set has none (preview). */
    private fun fallbackTop(): Int = fallbackTop(assets, context)

    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyPreviewConstraints(clockPreviewConfig: ClockPreviewConfig, constraints: ConstraintSet): ConstraintSet {
        pinTopStart(constraints, resources, fallbackTop())
        return constraints
    }

    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyExternalDisplayPresentationConstraints(constraints: ConstraintSet): ConstraintSet {
        pinTopStart(constraints, resources, resources.getDimensionPixelSize(clocksR.dimen.clock_padding_start))
        return constraints
    }

    override fun applyAodBurnIn(aodBurnInModel: AodClockBurnInModel) {
        // The keyguard already moves the large clock container vertically together with the date
        // row, but it leaves the large clock out of the horizontal burn-in shift; follow the date
        // row sideways too. This face is never scaled (useAlternateSmartspaceAODTransition), yet
        // the keyguard can apply its large-clock burn-in scale before it knows that and never
        // resets it, which shrank the words and pushed them off the date column: undo it here.
        view.translationX = aodBurnInModel.translationX
        view.scaleX = 1f
        view.scaleY = 1f
    }

    companion object {
        /** Extra start inset beyond the keyguard's clock padding; 0 keeps the words on the date column. */
        const val START_INSET_DP = 0f
        /** First line of the large face below the keyguard's small clock guideline. */
        const val TOP_OFFSET_DP = 219f
        const val SMALL_CLOCK_GUIDELINE_TOP = "small_clock_guideline_top"
        /** The keyguard's date / weather row shown under the large clock. */
        const val DATE_SMARTSPACE_VIEW_LARGE = "date_smartspace_view_large"
        const val KEYGUARD_CLOCK_TOP_MARGIN = "keyguard_clock_top_margin"

        /** Start inset of the words beyond the keyguard's clock padding. */
        fun startInset(resources: Resources): Int =
            resources.getDimensionPixelSize(clocksR.dimen.clock_padding_start) +
                (START_INSET_DP * resources.displayMetrics.density).roundToInt()

        /** Distance from the keyguard's small clock guideline to the large face's first line. */
        fun topOffset(resources: Resources): Int = (TOP_OFFSET_DP * resources.displayMetrics.density).roundToInt()

        /** A keyguard view id by name, or 0 when the host does not define it (previews). */
        fun resolveIdOrZero(assets: AssetLoader, name: String): Int =
            runCatching { assets.getResourcesId(name) }.getOrDefault(0)

        /** Top of the large face when the constraint set carries no small clock guideline. */
        fun fallbackTop(assets: AssetLoader, context: Context): Int {
            val clockTopMargin =
                assets.getDimensionPixelSize(KEYGUARD_CLOCK_TOP_MARGIN)
                    ?: (18 * context.resources.displayMetrics.density).roundToInt()
            return context.getSafeStatusBarHeight() + clockTopMargin + topOffset(context.resources)
        }

        /**
         * Pins the large clock container to the top start. Shared with [WordClockFaceLayoutSmall]
         * because the default small-face layout also constrains the large container (it assumes a
         * centred single-view clock) and runs after this face's layout in the preview.
         */
        fun pinTopStart(constraints: ConstraintSet, resources: Resources, topMargin: Int) {
            val id = ClockViewIds.LOCKSCREEN_CLOCK_VIEW_LARGE
            constraints.constrainWidth(id, WRAP_CONTENT)
            constraints.constrainHeight(id, WRAP_CONTENT)
            constraints.constrainMaxHeight(id, 0)
            constraints.clear(id, END)
            constraints.clear(id, BOTTOM)
            constraints.connect(id, START, PARENT_ID, START, startInset(resources))
            constraints.connect(id, TOP, PARENT_ID, TOP, topMargin)
        }
    }
}

/**
 * Small face: the default single-view layout, except that the large container is re-pinned after
 * the default preview / external-display constraints, which would otherwise centre it.
 */
class WordClockFaceLayoutSmall(view: WordClockViewSmall, private val assets: AssetLoader, private val context: Context) :
    DefaultClockFaceLayout(view) {
    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyConstraints(constraints: ConstraintSet): ConstraintSet {
        super.applyConstraints(constraints)
        // The large layout hangs the small clock container off the large face so that the date row
        // and the notifications sit below the words. The keyguard applies the target face's layout
        // last, so while this face is the target put the container back on the keyguard's
        // guideline: the large face is kept visible while it fades out, and hanging off it would
        // push the container (and the date row under it) below the incoming notification for the
        // length of the fade, then snap it up.
        val guideline = assets.getResourcesId(WordClockFaceLayoutLarge.SMALL_CLOCK_GUIDELINE_TOP)
        val guideBegin = constraints.getConstraint(guideline)?.layout?.guideBegin ?: -1
        if (guideline != 0 && guideBegin >= 0) {
            constraints.connect(ClockViewIds.LOCKSCREEN_CLOCK_VIEW_SMALL, TOP, guideline, BOTTOM)
        }
        return constraints
    }

    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyPreviewConstraints(clockPreviewConfig: ClockPreviewConfig, constraints: ConstraintSet): ConstraintSet {
        super.applyPreviewConstraints(clockPreviewConfig, constraints)
        WordClockFaceLayoutLarge.pinTopStart(constraints, context.resources, WordClockFaceLayoutLarge.fallbackTop(assets, context))
        return constraints
    }

    @Deprecated("Unsupported with flexiglass. Move to composables.")
    override fun applyExternalDisplayPresentationConstraints(constraints: ConstraintSet): ConstraintSet {
        super.applyExternalDisplayPresentationConstraints(constraints)
        WordClockFaceLayoutLarge.pinTopStart(
            constraints,
            context.resources,
            context.resources.getDimensionPixelSize(clocksR.dimen.clock_padding_start),
        )
        return constraints
    }
}
