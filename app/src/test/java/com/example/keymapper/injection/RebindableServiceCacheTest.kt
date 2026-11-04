package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class RebindableServiceCacheTest {
    @Test fun factoryRunsOnlyOnceUntilInvalidated() {
        val cache = RebindableServiceCache<Any>()
        var builds = 0
        val first = cache.get { builds++; Any() }
        val second = cache.get { builds++; Any() }

        assertSame(first, second)
        assertEquals(1, builds)

        cache.invalidate()
        val third = cache.get { builds++; Any() }
        assertNotSame(first, third)
        assertEquals(2, builds)
    }

    @Test fun invalidatingAnOldInstanceDoesNotClearANewerOne() {
        val cache = RebindableServiceCache<Any>()
        val old = cache.get { Any() }
        cache.invalidate(old)
        val current = cache.get { Any() }

        cache.invalidate(old)
        assertSame(current, cache.get { error("cache should retain the current instance") })
    }
}
