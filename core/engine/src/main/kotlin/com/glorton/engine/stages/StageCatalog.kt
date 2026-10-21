package com.glorton.engine.stages

import com.glorton.engine.world.World

/**
 * frame_42 `Stages = ["Rooftop","Mogadishu","B52","Space"]`.
 *
 * This array is the single source of truth for the stage select screen: frame_47
 * iterates it to lay out the thumbnails, `SelectStage` stores one of its entries
 * into `Game.Level`, frame_51 switches `Bounds`/`BoundsCam` on that value and
 * `Snd.PlaySound(Game.Level, true)` plays the looped track of the *same name*.
 *
 * The port keeps that naming contract end to end: the id doubles as the music
 * key, so the app's sound registry must keep a track registered per entry here.
 */
val STAGES = listOf("Rooftop", "Mogadishu", "B52", "Space")

/** Builds the [World] for a `Game.Level` id (frame_51's `switch(Game.Level)`). */
fun createStage(stage: String): World = when (stage) {
    "Rooftop" -> RooftopStage.create()
    "Mogadishu" -> MogadishuStage.create()
    "B52" -> B52Stage.create()
    "Space" -> SpaceStage.create()
    // frame_47 can only ever store a Stages entry, so an unknown id means a bad
    // caller rather than original content; fall back to the default stage
    // instead of killing the match (mirrors GameFactory's fighter fallback).
    else -> RooftopStage.create()
}
