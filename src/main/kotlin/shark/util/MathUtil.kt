package shark.util

import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.random.nextLong

object MathUtil {
    fun randomIntGenerator(random: Random = Random.Default, range: IntRange = 0 .. 1) = sequence {
        while (true) {
            yield(random.nextInt(range))
        }
    }

    fun randomLongGenerator(random: Random = Random.Default, range: LongRange = 0L .. 1L) = sequence {
        while (true) {
            yield(random.nextLong(range))
        }
    }

    fun randomFloatGenerator(random: Random = Random.Default) = sequence {
        while (true) {
            yield(random.nextFloat())
        }
    }

    fun randomDoubleGenerator(random: Random = Random.Default) = sequence {
        while (true) {
            yield(random.nextDouble())
        }
    }

}