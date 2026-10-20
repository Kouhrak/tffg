package com.glorton.engine.fighter

/**
 * Duck-typed "sender" accepted by AS2 `Fighter.Throw(Power, Angle, Sender,
 * Force)`: the original reads only `Sender.Weight` from the passed object
 * (and substitutes a plain `{Weight: 1}` object when Sender is undefined).
 *
 * Implemented by [Fighter] (fighters pass themselves) and by
 * `com.glorton.engine.projectile.Projectile` — EnergyBall.as throws *the
 * projectile itself* (`Throw(2.5, 45 * dir, this)`), using the shooter
 * weight copied at init.
 */
interface ThrowSource {
    /** AS2 `Sender.Weight` — inverse knockback factor of the throw formula. */
    val weight: Float
}
