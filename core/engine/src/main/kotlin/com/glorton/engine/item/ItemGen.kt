package com.glorton.engine.item

import com.glorton.engine.GameConfig
import com.glorton.engine.world.World
import kotlin.random.Random

/**
 * Port of class ItemGen (ItemGen.as): the random item spawner.
 *
 * AS2 runs this as a static singleton on `setInterval(DoItemGen,
 * 10000 / ItemFrequency)` started from frame_51 `InitArea`
 * (`ItemGen.init(["Mine","Grenade"], Settings.ItemFrequency)`, frequency 3).
 * This port is a per-match instance ticked by the Engine (documented
 * structural deviation: the AS2 class is static/global), inside the FIGHT
 * phase after the pause check - so the AS2 `if (_root.Paused) return` guard
 * is satisfied by the engine's own `paused` early-return.
 *
 * Spawn roll (verbatim AS2):
 *  - `random(ItemsList.length + 50 / ItemFrequency)`; when the roll lands
 *    below `ItemsList.length` the item at that index spawns, otherwise
 *    nothing (the `50 / frequency` tail is the "no item" window).
 *  - Position: `SpawnH{random(4)+1}`, x = bar.x + random(bar.width),
 *    y = bar.y - the item then falls (gravity 0.6) onto the stage.
 */
class ItemGen(
    private val world: World,
    /** AS2 `Settings.ItemFrequency` (frame_42 default: 3; 0 disables spawning). */
    private val frequency: Int,
    /** AS2 `ItemsList` link names; frame_51 passes `["Mine","Grenade"]`. */
    private val itemsList: List<String> = DEFAULT_ITEMS,
) {

    private var accMs = 0f

    /** One 25 ms tick; replaces the AS2 `setInterval` cadence. */
    fun tick() {
        if (frequency <= 0 || world.itemSpawns.isEmpty()) return
        accMs += GameConfig.TICK_MS
        // AS2: setInterval(DoItemGen, 10000 / ItemFrequency)
        val intervalMs = 10000f / frequency
        if (accMs < intervalMs) return
        accMs -= intervalMs
        spawnRoll()
    }

    /** Port of ItemGen.DoItemGen (Item.as decompile: ItemGen.as). */
    private fun spawnRoll() {
        // AS2: random(ItemsList.length + 50 / ItemFrequency) - the argument is
        // coerced to an integer by the VM (2 + 16.67 -> 18 with defaults).
        val bound = (itemsList.size + 50f / frequency).toInt()
        val roll = Random.nextInt(bound)
        if (roll >= itemsList.size) return

        // AS2: random(4) + 1 over SpawnH1..SpawnH4 (here: the stage's bars).
        val bar = world.itemSpawns[Random.nextInt(world.itemSpawns.size)]
        // AS2: x = SpawnH._x + random(SpawnH._width)
        val x = bar.left + Random.nextInt(bar.width.toInt())
        val y = bar.top
        val item = when (itemsList[roll]) {
            "Mine" -> Mine(world)
            "Grenade" -> Grenade(world)
            else -> return
        }
        item.init(x, y)
    }

    companion object {
        /** AS2 frame_51: `ItemGen.init(new Array("Mine","Grenade"), ...)`. */
        val DEFAULT_ITEMS = listOf("Mine", "Grenade")
    }
}
