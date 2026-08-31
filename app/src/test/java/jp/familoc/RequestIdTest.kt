package jp.familoc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestIdTest {
    @Test fun acceptsOnlyBoundedOpaqueIds() {
        assertTrue(isValidRequestId("request_123"))
        assertFalse(isValidRequestId("../request"))
        assertFalse(isValidRequestId("short"))
    }
}
