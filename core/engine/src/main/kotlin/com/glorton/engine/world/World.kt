package com.glorton.engine.world

import com.glorton.engine.PointF
import com.glorton.engine.RectF
import com.glorton.engine.camera.VCamera
import com.glorton.engine.fighter.Fighter

enum class PlatformType { FIXED, MOVING, AI, BOOM, KILLER }

/**
 * A collision platform of a stage. The original game tests timeline MovieClips
 * named Fixed*, Moving*, AI*, Boom*, Killer* with Flash shape hitTests
 * (World.as). The port models them as AABB rectangles; slanted stage geometry
 * must be approximated by several rectangles.
 */
class Platform(
    val name: String,
    val type: PlatformType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    /** AI helper subtype: the digit after "AI" in the original name (jump hint type). */
    val aiSubtype: Char = '0',
) {
    val rect: RectF get() = RectF(x, y, x + width, y + height)
}

/**
 * Port of the object returned by World.CollisionDetect in World.as.
 * Flags accumulate across every platform touched in one call, like the AS2 version.
 */
data class CollisionResult(
    /** Basic-mode result: something was touched. */
    val hitSomething: Boolean = false,
    /** AI helper subtype when an AI platform was hit in basic mode. */
    val aiType: Char? = null,
    /** Highest platform top (max platform._y) among touches; fighters rest feet here. */
    val floor: Float? = null,
    val moving: Boolean = false,
    val left: Boolean = false,
    val right: Boolean = false,
    val leftBc: Boolean = false,
    val rightBc: Boolean = false,
    val bottom: Boolean = false,
    val top: Boolean = false,
    /** Height of the platform touched at the top (AS2 field H). */
    val platformHeight: Float? = null,
    /** Platform that set bottom/top/border flags (AS2 field MC). */
    val platform: Platform? = null,
) {
    /** AS2 field Hit: any sampled point of the caster was inside a platform. */
    val hit: Boolean get() = left || right || leftBc || rightBc || bottom || top
}

enum class HitType { PLAYER, ITEM }

data class HitCandidate(val obj: Any, val type: HitType)

/** Anything that can be hitTest-ed against platforms (Fighter, Item, Projectile). */
interface Body {
    /** Bounding box in world coordinates (approximates Flash getBounds(_root)). */
    val bodyRect: RectF
}

/**
 * Port of class World (World.as). Platform flags Fixed/Moving/AI/Boom/Killer are
 * resolved from the stage definition instead of timeline MovieClip names.
 * Boom/Killer special behaviors are not implemented in v1 (no such platforms on
 * the Rooftop slice).
 */
class World(
    val platforms: List<Platform>,
    val spawnPoints: Map<String, PointF>,
    val bounds: RectF,
    val camBounds: RectF,
) {
    val players = mutableListOf<Fighter>()
    val items = mutableListOf<com.glorton.engine.item.Item>()
    val projectiles = mutableListOf<com.glorton.engine.projectile.Projectile>()
    val explosions = mutableListOf<com.glorton.engine.effects.Explosion>()

    /**
     * Camera that reacts to world events (Explosion's `VCam.Shake(-500)` in
     * Explosion.as). Wired by GameFactory; optional so plain-World tests stay
     * camera-free.
     */
    var camera: VCamera? = null

    fun addPlayer(player: Fighter) {
        players.add(player)
    }

    /**
     * Port of World.CollisionDetect (World.as).
     *
     * @param basic when true only reports hitSomething + AI subtype (used by the
     *   AI jump checks and simple obstacle probes).
     * @param platformsEnabled include FIXED and MOVING platforms.
     * @param helpers include AI helper platforms.
     * @param movingC include MOVING platforms.
     */
    fun collisionDetect(
        caster: Body,
        basic: Boolean,
        platformsEnabled: Boolean = true,
        helpers: Boolean = false,
        movingC: Boolean = true,
        lowRes: Boolean = false,
    ): CollisionResult {
        var floor: Float? = null
        var anyMoving = false
        var anyFixed = false
        var left = false
        var right = false
        var leftBc = false
        var rightBc = false
        var bottom = false
        var top = false
        var platformHeight: Float? = null
        var lastPlatform: Platform? = null

        val body = caster.bodyRect
        val midX = body.centerX
        val midY = body.centerY

        for (platform in platforms) {
            when (platform.type) {
                PlatformType.FIXED -> if (!platformsEnabled) continue
                PlatformType.MOVING -> if (!platformsEnabled || !movingC) continue
                PlatformType.AI -> if (!helpers) continue
                else -> Unit
            }

            val pRect = platform.rect
            if (!body.intersects(pRect)) continue

            if (basic) {
                return CollisionResult(
                    hitSomething = true,
                    aiType = if (platform.type == PlatformType.AI) platform.aiSubtype else null,
                )
            }

            // AS2: Floor takes the max platform._y among all touched platforms.
            floor = maxOf(floor ?: pRect.top, pRect.top)

            if (platform.type == PlatformType.MOVING) {
                anyMoving = true
            } else {
                anyFixed = true
            }

            // Point sampling approximating the AS2 shape hitTests (World.as).
            if (platform.type != PlatformType.MOVING || !lowRes) {
                if (pRect.contains(body.left, midY)) {
                    left = true
                }
                if (pRect.contains(body.right, midY)) {
                    right = true
                }
            }
            if (pRect.contains(body.right, body.bottom)) {
                rightBc = true
                lastPlatform = platform
            }
            if (pRect.contains(body.left, body.bottom)) {
                leftBc = true
                lastPlatform = platform
            }
            if (pRect.contains(midX, body.bottom)) {
                bottom = true
                lastPlatform = platform
            }
            if (pRect.contains(midX, body.top)) {
                top = true
                platformHeight = pRect.height
                lastPlatform = platform
            }
        }

        return CollisionResult(
            hitSomething = anyMoving || anyFixed || left || right || leftBc || rightBc || bottom || top,
            floor = floor,
            moving = anyMoving,
            left = left,
            right = right,
            leftBc = leftBc,
            rightBc = rightBc,
            bottom = bottom,
            top = top,
            platformHeight = platformHeight,
            platform = lastPlatform,
        )
    }

    /** Port of World.CollisionDetectAttack (World.as); AABB instead of shape hitTest. */
    fun collisionDetectAttack(caster: Body, includeItems: Boolean = false): List<HitCandidate> {
        val result = mutableListOf<HitCandidate>()
        for (player in players) {
            if (player !== caster && player.bodyRect.intersects(caster.bodyRect)) {
                result.add(HitCandidate(player, HitType.PLAYER))
            }
        }
        if (includeItems) {
            for (item in items) {
                if (item !== caster && item.bodyRect.intersects(caster.bodyRect)) {
                    result.add(HitCandidate(item, HitType.ITEM))
                }
            }
        }
        return result
    }
}
