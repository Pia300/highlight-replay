package io.github.pia300.highlightreplay.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/** 为 Service 内的 Compose 内容提供 Lifecycle、ViewModel 与 SavedState 宿主（Service 不自带这些）。 */
internal class ServiceComposeOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    // Service 无系统回调，生命周期状态需手动驱动。
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        field = lifecycleRegistry
    private val _viewModelStore = ViewModelStore()
    // Service 场景无可恢复状态，SavedState 注册表仅占位。
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // Service 无系统回调：手动推进到 RESUMED 使 Compose 内容可挂载。
    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /** 销毁宿主：推进到 DESTROYED 并清除所有 ViewModel。 */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        _viewModelStore.clear()
    }
}
