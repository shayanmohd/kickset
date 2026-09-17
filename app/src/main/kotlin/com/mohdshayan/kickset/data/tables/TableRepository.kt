package com.mohdshayan.kickset.data.tables

import android.content.Context
import com.mohdshayan.kickset.core.fittings.TableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Reads the four bundled ASME table files once. */
class TableRepository(private val context: Context) {
    private val mutex = Mutex()
    @Volatile private var cached: TableSet? = null

    suspend fun get(): TableSet = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            fun read(name: String) = context.assets.open("tables/$name").bufferedReader().use { it.readText() }
            TableSet.parse(read("asme_b16_9.json"), read("asme_b16_11.json"), read("asme_b16_5_bolts.json"), read("asme_b36_10_19.json"))
        }.also { cached = it }
    }

    val tables: Flow<TableSet> = flow { emit(get()) }.flowOn(Dispatchers.IO)
}
