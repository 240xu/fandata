package io.legado.engine.coroutine

class CompositeCoroutine {
    private val jobs = mutableListOf<Coroutine<*>>()
    fun add(job: Coroutine<*>) { synchronized(jobs) { jobs.add(job) } }
    fun remove(job: Coroutine<*>) { synchronized(jobs) { jobs.remove(job) } }
    fun cancel() { synchronized(jobs) { jobs.forEach { it.cancel() }; jobs.clear() } }
    val size: Int get() = synchronized(jobs) { jobs.size }
}
