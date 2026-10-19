package com.limersvp

import android.app.Application
import android.view.KeyEvent
import com.limersvp.data.LibraryRepository
import com.limersvp.data.SettingsRepository
import com.limersvp.data.StatsRepository
import com.limersvp.feel.Haptics
import com.limersvp.parse.BookImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class LimeApp : Application() {
    lateinit var container: Container
        private set

    override fun onCreate() {
        super.onCreate()
        container = Container(this)
    }
}

class Container(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsRepository(app)
    val library = LibraryRepository(app, scope)
    val stats = StatsRepository(app, scope)
    val haptics = Haptics(app)
    val importer = BookImporter(app, library, settings, scope)

    /** The open reader takes hardware keys while it is showing. */
    @Volatile
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    /** A book another app asked us to open, waiting for the UI. */
    val openRequest = MutableStateFlow<String?>(null)

    init {
        scope.launch(Dispatchers.Main) {
            settings.state.collect { haptics.level = it.haptics }
        }
        scope.launch {
            importer.events.collect { e ->
                if (e.open && e !is com.limersvp.parse.ImportEvent.Failed) openRequest.value = e.id
            }
        }
    }

    fun flush() {
        library.flush(); stats.flush()
    }
}
