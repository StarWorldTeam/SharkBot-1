package shark.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoroutineUtilTest {

    @Test
    fun `Promise Test`() {
        val promise1 = promise {
            kotlinx.coroutines.delay(100)
            resolve(1)
        }
        val promise2 = promise1.thenRun {
            2
        }
        val promise3 = promise2.thenRun { 3 }
        promise3.thenRun { 1 }
        assertEquals(promise3.awaitSync(), 3)
    }

}