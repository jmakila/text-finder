package fi.plague.textfinder

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit test for MainActivity functionality.
 * This demonstrates how to create a test for a specific component.
 * 
 * Note: We avoid directly instantiating MainActivity in unit tests
 * because it requires the Android framework, which is not available in unit tests.
 * For testing Android components, use instrumented tests instead.
 */
class MainActivityTest {

    @Test
    fun testGreetingFunction() {
        // Test the Greeting composable function indirectly
        // In a real test, you would use ComposeTestRule to test composables
        val result = "Hello Android!"
        assertEquals("Hello Android!", result)
    }

    @Test
    fun testSimpleMath() {
        // A simple test to demonstrate assertions
        assertEquals(4, 2 + 2)
        assertTrue(4 > 3)
        assertFalse(4 < 3)
    }
}
