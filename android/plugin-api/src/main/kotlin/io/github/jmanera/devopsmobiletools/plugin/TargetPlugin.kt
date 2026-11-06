// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugin

/** Identity of a plugin, shown in the marketplace and checked before loading it. */
data class PluginInfo(
    /** Reverse-DNS identifier, such as `io.github.jmanera.devopsmobiletools.ec2shell`. */
    val id: String,
    val name: String,
    /** The plugin's own version. */
    val version: String,
    /** The [PLUGIN_API_VERSION] the plugin was built against. */
    val apiVersion: String = PLUGIN_API_VERSION,
)

/** An IAM action, and optionally the resource, a plugin needs. */
data class IamPermission(val action: String, val resource: String = "*") {
    init {
        require(ACTION.matches(action)) { "IAM action must look like service:Action, got '$action'" }
    }

    private companion object {
        val ACTION = Regex("""[a-z0-9-]+:[A-Za-z0-9*]+""")
    }
}

/** Something a plugin can connect to: an instance, a database, a table. */
data class Target(
    /** Identifier the plugin understands, such as an EC2 instance id. */
    val id: String,
    /** Name shown to the user. */
    val name: String,
    /** Free-form details for display, such as the platform or the agent version. */
    val attributes: Map<String, String> = emptyMap(),
)

/**
 * A destination type the app can open: the EC2 shell, RDS, DynamoDB.
 *
 * The core knows targets only through this interface. Plugins do not touch
 * the UI or the user's SSO token.
 */
interface TargetPlugin {
    val info: PluginInfo

    /** Every IAM permission the plugin needs; the core grants nothing else. */
    val permissions: List<IamPermission>

    /** Lists the targets reachable with [context]. */
    suspend fun discover(context: AwsContext): List<Target>

    /**
     * Opens a connection to [target]. Output and state changes arrive on
     * [listener] until the connection reports [ConnectionState.CLOSED] or
     * [ConnectionState.FAILED].
     */
    suspend fun connect(target: Target, context: AwsContext, listener: ConnectionListener): Connection
}
