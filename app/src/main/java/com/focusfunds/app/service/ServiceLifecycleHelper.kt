package com.focusfunds.app.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

class MyServiceLifecycleOwner : LifecycleOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    fun onCreate() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

class MySavedStateRegistryOwner(private val lifecycleOwner: MyServiceLifecycleOwner) : SavedStateRegistryOwner {
    private val controller = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle = lifecycleOwner.lifecycle
    override val savedStateRegistry: SavedStateRegistry = controller.savedStateRegistry

    init {
        controller.performRestore(null)
    }
}
