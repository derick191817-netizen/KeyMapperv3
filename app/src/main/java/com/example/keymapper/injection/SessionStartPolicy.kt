package com.example.keymapper.injection

/**
 * Keeps a live Shizuku callback valid when MappingService repeats start() for the same
 * service lifecycle. A new epoch is created only for a new lifecycle or when callback state
 * is unexpectedly missing; otherwise begin() would invalidate the callback already registered
 * inside the remote UserService.
 */
internal object SessionStartPolicy {
    fun shouldCreateFreshSession(isActive: Boolean, hasInputCallback: Boolean): Boolean =
        !isActive || !hasInputCallback
}
