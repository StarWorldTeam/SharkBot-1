@file:Suppress("DuplicatedCode")

package shark.util

import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.*
import kotlin.time.Duration

object Void

/**
 * 包装了多个错误对象的单个错误对象。
 */
class AggregateError(val errors: Array<Throwable>, message: String?): RuntimeException(message)

/**
 * 返回 Void
 * @see Void
 */
@Suppress("UNUSED_PARAMETER")
fun void (vararg value: Any?) = Void

/**
 * 执行一个函数并忽视这个函数的结果
 */
fun runWithNoError(block: Callable) {
    try {
        block()
    } catch (_: Throwable) {}
}

typealias PromiseFunction <T> = suspend PromiseResolver<T>.() -> Unit

/** 异步执行 */
fun <T> promise(func: PromiseFunction<T>): Promise <T> = PromiseImpl(func)

/** 异步执行（无返回） */
fun promiseVoid(func: PromiseFunction<Void>) = promise(func)

fun <R> asyncFunction(func: suspend PromiseResolver<R>.() -> Unit): () -> Promise <R> = { promise(func) }
fun <R, T1> asyncFunction1(func: suspend PromiseResolver<R>.(T1) -> Unit): (T1) -> Promise <R> = { promise { func(it) } }

/** Promise 控制器 */
interface PromiseResolver<T> {

    /** 获取 Promise */
    fun getPromise(): Promise<T>

    /** 返回并结束执行 */
    fun resolve(value: T) = value

    /** 报错并结束执行 */
    fun reject(throwable: Throwable) = throwable

    /**
     * 报错并结束执行
     * @see CancellationException
     */
    fun reject() = reject(CancellationException())

}

/** 返回Void */
fun PromiseResolver<Void>.resolve() {
    resolve(Void)
}

/** Promise状态 */
enum class PromiseState(
    /** 是否已完成 */ val done: Boolean
) {

    /** 空闲 */ IDLE(false),
    /** 运行中 */ RUNNING(false),
    /** 已兑现 */ COMPLETED(true),
    /** 已失败 */ REJECTED(true),
    /** 已停止 */STOPPED(true);

}

interface Promise<out T> {

    companion object {

        fun delay(tileMillis: Long) = promise {
            kotlinx.coroutines.delay(tileMillis)
            resolve()
        }

        fun delay(duration: Duration) = promise {
            kotlinx.coroutines.delay(duration)
            resolve()
        }

        fun <T> delay(timeMillis: Long, block: suspend () -> T) = promise {
            kotlinx.coroutines.delay(timeMillis)
            resolve(block())
        }

        fun <T> delay(duration: Duration, block: suspend () -> T) = promise {
            kotlinx.coroutines.delay(duration)
            resolve(block)
        }

        /** 创建Promise控制器 */
        fun <T> withResolvers(): PromiseResolver<T> {
            return PromiseImpl<T> {}.getPromiseResolver()
        }

        /**
         * 接受多个 Promise 对象作为输入，并返回一个 Promise。当所有输入的 Promise 都被兑现时，返回的 Promise 也将被兑现（即使未传入 Promise 对象），并返回一个包含所有兑现值的数组。如果输入的任何 Promise 失败，则返回的 Promise 将失败，并带有第一个失败的原因。
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> all(vararg promises: Promise<T>) = promise {
            val results = arrayOfNulls<Any?>(promises.size)
            var count = 0
            promises.forEachIndexed { index, promise ->
                promise.thenRun { result ->
                    results[index] = result
                    count++
                    if (count == promises.size)
                        resolve(Collections.unmodifiableList(results.map { it!! as T }))
                }
                promise.catchRun(::reject)
            }
        }

        /**
         * 将多个 Promise 对象作为输入，并返回一个 Promise。当输入的任何一个 Promise 兑现时，这个返回的 Promise 将会兑现，并返回第一个兑现的值。
         * @throws AggregateError 当 Promise 都失败时（或未传入任何Promise对象），产生此错误
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> any(vararg promises: Promise<T>) = promise {
            var count = 0
            var completed = false
            val errors = mutableListOf<Throwable>()
            promises.forEach { promise ->
                promise.catchRun {
                    if (completed) return@catchRun
                    count++
                    errors.add(it)
                    if (count == promises.size)
                        reject(AggregateError(errors.toTypedArray(), "All promises were rejected"))
                }
                promise.thenRun {
                    if (completed) return@thenRun
                    completed = true
                    resolve(it)
                }
            }
        }

        /**
         * 接受多个 Promise 对象作为输入，并返回一个 Promise。这个返回的 Promise 会随着第一个 Promise 的触发而触发。
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> race(vararg promises: Promise<T>) = promise {
            var completed = false
            promises.forEach { promise ->
                promise.catchRun {
                    if (completed) return@catchRun
                    completed = true
                    reject(it)
                }
                promise.thenRun {
                    if (completed) return@thenRun
                    completed = true
                    resolve(it)
                }
            }
        }

        /** 将值转换为一个 Promise。当传入的值是 Promise 时，直接返回这个 Promise。 */
        fun <T> resolve(value: T) = promise { resolve(value) }
        /** 将值转换为一个 Promise。当传入的值是 Promise 时，直接返回这个 Promise。 */
        fun <T> resolve(promise: Promise<T>) = promise
        /** 返回一个空 Promise */
        fun resolve() = promiseVoid { resolve() }
        /** 返回一个已失败的 Promise 对象，失败原因为给定的参数。 */
        fun reject(throwable: Throwable = Throwable()) = promiseVoid { reject(throwable) }

    }

    /**
     * 获取 Promise 的状态
     * @see PromiseState
     */
    fun getState(): PromiseState = PromiseState.IDLE

    /**
     * 用于 Promise 兑现的回调函数。它立即返回一个等效的 Promise 对象，允许你链接到其他 Promise 方法，从而实现链式调用。
     * @param func 回调函数，返回一个 Promise 对象
     * @return 返回一个 Promise，Promise 的状态随 [func] 返回的 Promise 的状态改变而改变
     */
    fun <R> then(func: (value: T) -> Promise<R>): Promise<R>
    /**
     * 用于 Promise 兑现的回调函数。它立即返回一个等效的 Promise 对象，允许你链接到其他 Promise 方法，从而实现链式调用。
     * @param func 回调函数，返回一个对象
     * @return 返回一个 Promise，Promise 的值为 [func] 的返回值
     */
    fun <R> thenRun(func: (value: T) -> R): Promise<R> {
        return then { value -> promise { resolve(func(value)) } }
    }

    /**
     * 最多接受两个参数：用于 Promise 兑现和失败情况的回调函数。它立即返回一个等效的 Promise 对象，允许你链接到其他 Promise 方法，从而实现链式调用。
     * @param then 一个在此 Promise 对象被兑现时异步执行的函数。它的返回值将成为 then() 返回的 Promise 对象的兑现值。
     * @param catch 一个在此 Promise 对象被拒绝时异步执行的函数。它的返回值将成为 catch() 返回的 Promise 对象的兑现值。
     * @return 立即返回一个新的 Promise 对象，该对象的状态随 [then] 或 [catch] 返回的 Promise 的状态的改变而改变。
     */
    fun <R> on(then: (value: T) -> Promise<R>, catch: ((throwable: Throwable) -> Promise<R>)? = null) = promiseVoid {
        this@Promise.then(then).also { it.thenRun(::resolve) }.catchRun(::reject)
        if (catch != null) catch(catch).also { it.thenRun(::resolve) }.catchRun(::reject)
    }

    /**
     * 最多接受两个参数：用于 Promise 兑现和失败情况的回调函数。它立即返回一个等效的 Promise 对象，允许你链接到其他 Promise 方法，从而实现链式调用。
     * @param then 一个在此 Promise 对象被兑现时异步执行的函数。它的返回值将成为 then() 返回的 Promise 对象的兑现值。
     * @param catch 一个在此 Promise 对象被拒绝时异步执行的函数。它的返回值将成为 catch() 返回的 Promise 对象的兑现值。
     * @return 立即返回一个新的 Promise 对象，该对象的状态随 [then] 或 [catch] 返回的 Promise 的状态的改变而改变。
     */
    fun <R> onRun(then: (value: T) -> R, catch: ((throwable: Throwable) -> R)? = null) = promise {
        thenRun(then).also { it.thenRun(::resolve) }.catchRun(::reject)
        if (catch != null) catchRun(catch).also { it.thenRun(::resolve) }.catchRun(::reject)
    }

    /**
     * 用于注册一个在 Promise 敲定（兑现或拒绝）时调用的函数。它会立即返回一个等效的 Promise 对象，这可以允许你链式调用其他 Promise 方法。
     *
     * 这可以让你避免在 Promise 的 [then] 和 [catch] 处理器中重复编写代码。
     */
    fun <R> finally(func: () -> Promise<R>): Promise<R>

    /**
     * 用于注册一个在 Promise 敲定（兑现或拒绝）时调用的函数。它会立即返回一个等效的 Promise 对象，这可以允许你链式调用其他 Promise 方法。
     *
     * 这可以让你避免在 Promise 的 [then] 和 [catch] 处理器中重复编写代码。
     */
    fun <R> finallyRun(func: () -> R): Promise<R> {
        return finally { promise { resolve(func()) } }
    }

    /**
     * 注册一个在 Promise 失败时调用的函数。它会立即返回一个等效的 Promise 对象，这可以允许你链式调用其他 Promise 的方法。
     * @param func 回调函数，返回一个 Promise 对象
     * @return 返回一个 Promise，Promise 的状态随 [func] 返回的 Promise 的状态改变而改变
     */
    fun <R> catch(func: (value: Throwable) -> Promise<R>): Promise<R>

    /**
     * 注册一个在 Promise 失败时调用的函数。它会立即返回一个等效的 Promise 对象，这可以允许你链式调用其他 Promise 的方法。
     * @param func 回调函数，返回一个对象
     * @return 返回一个 Promise，Promise 的状态随 [func] 返回的 Promise 的状态改变而改变
     */
    fun <R> catchRun(func: (value: Throwable) -> R): Promise<R> {
        return catch { value -> promise { resolve(func(value)) } }
    }

    /**
     * 同步等待 Promise 执行完毕
     * @return 返回 Promise 的兑现结果
     * @throws Throwable 如果 Promise 已失败，则抛出异常。
     */
    fun awaitSync(): T

    /**
     * 异步等待 Promise 执行完毕
     * @return 返回 Promise 的兑现结果
     * @throws Throwable 如果 Promise 已失败，则抛出异常。
     */
    suspend fun await(): T

    /**
     * 立即关闭 Promise 执行
     */
    fun cancel(): Promise<T> = this

    /**
     * 等待 Promise 执行完毕
     */
    fun join(): Promise<T> = this

}

fun Promise<*>.assertNotStopped() {
    if (getState() == PromiseState.STOPPED)
        throw CancellationException()
}

open class PromiseImpl<T> internal constructor(func: PromiseFunction<T>) : Promise <T> {

    private var state = PromiseState.IDLE

    private lateinit var job: Deferred <Unit>

    internal fun getPromiseResolver() = promiseResolver

    private val promiseResolver = object : PromiseResolver <T> {

        override fun getPromise() = this@PromiseImpl

        val resolveCallback = arrayListOf<Runnable>()
        val rejectCallback = arrayListOf<Runnable>()

        var value: T? = null
        var throwable: Throwable? = null

        override fun resolve(value: T) = value.also {
            if (getState().done) return@also
            state = PromiseState.COMPLETED
            this.value = value
            resolveCallback.forEach(Runnable::run)
            resolveCallback.clear()
            job.cancel()
        }

        override fun reject(throwable: Throwable) = throwable.also {
            if (getState().done) return@also
            state = PromiseState.REJECTED
            this.throwable = throwable
            rejectCallback.forEach(Runnable::run)
            rejectCallback.clear()
            job.cancel()
        }

    }

    init {
        job = CoroutineScope(Dispatchers.Default).async {
            state = PromiseState.RUNNING
            func(promiseResolver)
        }
        job.start()
        job.invokeOnCompletion {
            if (it != null)
                promiseResolver.reject(it)
        }
    }

    override fun awaitSync() = runBlocking {
        await()
    }

    override suspend fun await(): T = suspendCoroutine { continuation ->
        assertNotStopped()
        if (getState().done) {
            promiseResolver.throwable?.let(continuation::resumeWithException)
            promiseResolver.value?.let(continuation::resume)
        } else {
            promiseResolver.resolveCallback.add {
                promiseResolver.value?.let(continuation::resume)
            }
            promiseResolver.rejectCallback.add {
                promiseResolver.throwable?.let(continuation::resumeWithException)
            }
        }
    }


    override fun cancel() = this.also {
        job.cancel()
        state = PromiseState.STOPPED
    }

    override fun join() = this.also {
        runWithNoError  {
            awaitSync()
        }
    }

    override fun getState() = state

    override fun <R> then(func: (value: T) -> Promise<R>): Promise<R> {
        return promise {
            if (state.done) {
                assertNotStopped()
                if (promiseResolver.value != null) {
                    val result = func(promiseResolver.value!!)
                    result.catchRun(::reject)
                    result.thenRun(::resolve)
                }
                return@promise
            }
            promiseResolver.resolveCallback.add {
                val result = func(promiseResolver.value!!)
                result.catchRun(::reject)
                result.thenRun(::resolve)
            }
        }
    }

    override fun <R> catch(func: (value: Throwable) -> Promise<R>): Promise<R> {
        return promise {
            if (state.done) {
                assertNotStopped()
                if (promiseResolver.throwable != null) {
                    val result = func(promiseResolver.throwable!!)
                    result.catchRun(::reject)
                    result.thenRun(::resolve)
                }
                return@promise
            }
            promiseResolver.rejectCallback.add {
                val result = func(promiseResolver.throwable!!)
                result.catchRun(::reject)
                result.thenRun(::resolve)
            }
        }
    }

    override fun <R> finally(func: () -> Promise<R>) = promise {
        assertNotStopped()
        if (getState().done) {
            promiseResolver.throwable?.let { func().thenRun(::resolve) }
            promiseResolver.value?.let { func().thenRun(::resolve) }
        } else {
            promiseResolver.resolveCallback.add {
                promiseResolver.value?.let { func().thenRun(::resolve) }
            }
            promiseResolver.rejectCallback.add {
                promiseResolver.throwable?.let { func().thenRun(::resolve) }
            }
        }
    }

}
