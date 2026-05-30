package io.legado.engine.coroutine

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class Coroutine<T>(
    private val scope: CoroutineScope,
    context: CoroutineContext = Dispatchers.IO,
    private val executeContext: CoroutineContext = Dispatchers.Main,
    block: suspend CoroutineScope.() -> T
) {
    private val job: Job
    private var success: (suspend (T) -> Unit)? = null
    private var error: (suspend (Throwable) -> Unit)? = null
    private var finally: (suspend () -> Unit)? = null
    val isActive get() = job.isActive
    val isCompleted get() = job.isCompleted
    init {
        job = scope.launch(executeContext) {
            try {
                val value = withContext(context) { block() }
                success?.invoke(value)
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { error?.invoke(e) }
            finally { finally?.invoke() }
        }
    }
    fun onSuccess(block: suspend (T) -> Unit): Coroutine<T> { success = block; return this }
    fun onError(block: suspend (Throwable) -> Unit): Coroutine<T> { error = block; return this }
    fun onFinally(block: suspend () -> Unit): Coroutine<T> { finally = block; return this }
    fun cancel() { job.cancel() }
}
