package com.camelcreatives.rekodi.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface RekodiDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val computation: CoroutineDispatcher
}

class DefaultRekodiDispatchers : RekodiDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val computation: CoroutineDispatcher = Dispatchers.Default
}
