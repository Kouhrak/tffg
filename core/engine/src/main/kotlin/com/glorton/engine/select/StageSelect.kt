package com.glorton.engine.select

import com.glorton.engine.stages.STAGES

/**
 * frame_47 (the frame labelled "StageSelect") thumbnail layout.
 *
 * AS2 source of truth (`scripts/frame_47/DoAction.as`):
 *
 * ```
 * var xP = (i <= 2 ? i : i - 3) * 550 / 3 + 30;
 * var yP = i > 2 ? 210 : 90;
 * ```
 *
 * So it is a 3-wide grid at `y = 90` plus whatever spills onto a second row at
 * `y = 210`, left aligned at `x = 30` and spaced `550 / 3` apart - i.e. the
 * 600x400 Flash stage divided into three columns.
 *
 * Two frame_47 behaviours are deliberately **not** ported:
 *  - The `Game.Rand.Level` branch that skips straight to a random stage. Nothing
 *    in the decompiled scripts ever sets `Game.Rand.Level` (the mode buttons only
 *    write `Game.Rand.CP`), so that branch is unreachable in the original and
 *    the port always shows the thumbnails.
 *  - `StageThum.getNextHighestDepth()` on the thumbnail attach: `StageThum` is
 *    an identifier that appears nowhere else in the decompiled scripts, so it is
 *    most likely a stray timeline instance name. It only feeds a depth counter,
 *    which the port does not model.
 */

/** One thumbnail slot, in Flash stage units (600x400 view, `GameConfig.VIEW_*`). */
data class StageThumbPosition(
    /** AS2 `xP`. */
    val x: Float,
    /** AS2 `yP`. */
    val y: Float,
)

/**
 * AS2 `xP`/`yP` for [count] thumbnails. Arithmetic is float like ActionScript,
 * so `1 * 550 / 3` is `183.33` rather than Kotlin's integer `183`.
 */
fun stageThumbPositions(count: Int = STAGES.size): List<StageThumbPosition> =
    (0 until count).map { i ->
        val column = if (i <= 2) i else i - 3
        StageThumbPosition(
            x = column * 550f / 3f + 30f,
            y = if (i > 2) 210f else 90f,
        )
    }
