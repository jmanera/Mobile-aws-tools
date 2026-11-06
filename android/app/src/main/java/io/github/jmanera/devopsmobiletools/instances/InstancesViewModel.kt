// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.instances

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.jmanera.devopsmobiletools.DevOpsMobileToolsApp
import io.github.jmanera.devopsmobiletools.core.aws.context.Selection
import io.github.jmanera.devopsmobiletools.core.aws.context.SelectionRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.plugin.PermissionDeniedException
import io.github.jmanera.devopsmobiletools.plugin.Target
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface InstancesState {
    data object Loading : InstancesState

    data class Loaded(val targets: List<Target>) : InstancesState

    /** [backToSignIn]: the problem is fixed on the sign-in screen (sign in, pick a role). */
    data class Failed(val message: String, val backToSignIn: Boolean = false) : InstancesState
}

/** The instances the EC2 shell plugin finds for the selected role and region. */
class InstancesViewModel(app: Application) : AndroidViewModel(app) {

    private val aws = app as DevOpsMobileToolsApp
    private var job: Job? = null

    var state by mutableStateOf<InstancesState>(InstancesState.Loading)
        private set

    var selection by mutableStateOf<Selection?>(null)
        private set

    var query by mutableStateOf("")

    /** The loaded instances that match [query]. */
    val visible: List<Target>
        get() = (state as? InstancesState.Loaded)?.targets.orEmpty().matching(query)

    init {
        refresh()
    }

    fun refresh() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            state = InstancesState.Loading
            state = try {
                selection = withContext(Dispatchers.IO) { aws.contexts.selection() }
                val context = withContext(Dispatchers.IO) { aws.contexts.context() }
                InstancesState.Loaded(aws.ec2Shell.discover(context))
            } catch (e: SelectionRequiredException) {
                InstancesState.Failed("Pick an account, a role and a region first.", backToSignIn = true)
            } catch (e: SignInRequiredException) {
                InstancesState.Failed("Please sign in again: ${e.message}", backToSignIn = true)
            } catch (e: PermissionDeniedException) {
                InstancesState.Failed(
                    "This role cannot list instances. Add ${e.permission.action} to its permission set " +
                        "in IAM Identity Center, or pick another role.",
                    backToSignIn = true,
                )
            } catch (e: Exception) {
                // SDK messages name the failing operation, never the credentials.
                Log.w(TAG, "Discovery failed: ${e.javaClass.simpleName}: ${e.message}")
                InstancesState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private companion object {
        const val TAG = "instances"
    }
}

/** Instances whose name, id or details contain [query], ignoring case. */
internal fun List<Target>.matching(query: String): List<Target> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { t ->
        t.name.contains(q, ignoreCase = true) ||
            t.id.contains(q, ignoreCase = true) ||
            t.attributes.values.any { it.contains(q, ignoreCase = true) }
    }
}
