package com.vedica.labs.ind.app.docora.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatchers.
 *
 * Nothing in Docora is allowed to reference [Dispatchers] directly outside of this file:
 * tests substitute a test dispatcher and every blocking call is pinned to [io].
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

/** Production dispatchers. */
object DefaultDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default

    /**
     * Bounded parallelism would starve the shared pool when several document scans run in
     * parallel, so heavy file IO keeps using the standard IO pool which Kotlin sizes
     * appropriately and which WorkManager already throttles.
     */
    override val io: CoroutineDispatcher = Dispatchers.IO
}
