package com.mohdshayan.kickset.di

import android.content.Context
import com.mohdshayan.kickset.data.db.AppDatabase
import com.mohdshayan.kickset.data.prefs.AppPrefs
import com.mohdshayan.kickset.data.session.Session
import com.mohdshayan.kickset.data.tables.TableRepository

/** Manual dependency container, initialised in App.onCreate. */
object ServiceLocator {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    private fun ctx(): Context = appContext ?: error("ServiceLocator.init() must be called before use")

    val appPrefs: AppPrefs by lazy { AppPrefs(ctx()) }
    val database: AppDatabase by lazy { AppDatabase.get(ctx()) }
    val jobDao get() = database.jobDao()
    val calcDao get() = database.calcDao()
    val tables: TableRepository by lazy { TableRepository(ctx()) }
    val session: Session by lazy { Session() }
}
