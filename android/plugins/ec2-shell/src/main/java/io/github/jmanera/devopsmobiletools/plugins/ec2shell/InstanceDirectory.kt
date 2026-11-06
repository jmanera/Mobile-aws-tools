// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugins.ec2shell

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.resourcegroupstaggingapi.ResourceGroupsTaggingClient
import aws.sdk.kotlin.services.resourcegroupstaggingapi.getResources
import aws.sdk.kotlin.services.resourcegroupstaggingapi.model.TagFilter
import aws.sdk.kotlin.services.ssm.SsmClient
import aws.sdk.kotlin.services.ssm.describeInstanceInformation
import aws.sdk.kotlin.services.ssm.model.InstanceInformationStringFilter
import aws.smithy.kotlin.runtime.ServiceException
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import io.github.jmanera.devopsmobiletools.plugin.AwsContext
import io.github.jmanera.devopsmobiletools.plugin.IamPermission
import io.github.jmanera.devopsmobiletools.plugin.PermissionDeniedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An EC2 instance as Systems Manager sees it. */
data class ManagedInstance(
    val id: String,
    val platform: String?,
    val agentVersion: String?,
    val ipAddress: String?,
)

/** Where the plugin finds instances. One per [AwsContext]. */
interface InstanceDirectory {
    /**
     * EC2 instances whose SSM Agent is online: the only ones a session can
     * reach. Throws [PermissionDeniedException] if the role may not list them.
     */
    suspend fun onlineInstances(): List<ManagedInstance>

    /** The `Name` tag of every tagged instance in the region, by instance id. */
    suspend fun nameTags(): Map<String, String>
}

/** [InstanceDirectory] backed by the AWS SDK for Kotlin, on [Dispatchers.IO]. */
class SdkInstanceDirectory(private val context: AwsContext) : InstanceDirectory {

    private val credentials = StaticCredentialsProvider(
        Credentials(
            accessKeyId = context.credentials.accessKeyId,
            secretAccessKey = context.credentials.secretAccessKey,
            sessionToken = context.credentials.sessionToken,
        ),
    )

    override suspend fun onlineInstances(): List<ManagedInstance> = withContext(Dispatchers.IO) {
        SsmClient {
            region = context.region
            credentialsProvider = credentials
        }.use { c ->
            val instances = mutableListOf<ManagedInstance>()
            var next: String? = null
            do {
                val out = denied(DESCRIBE_INSTANCE_INFORMATION) { c.describeInstanceInformation {
                    filters = listOf(
                        InstanceInformationStringFilter { key = "PingStatus"; values = listOf("Online") },
                        // Leaves out on-premises servers (mi-…) registered with hybrid activations.
                        InstanceInformationStringFilter { key = "ResourceType"; values = listOf("EC2Instance") },
                    )
                    nextToken = next
                } }
                out.instanceInformationList.orEmpty().mapNotNullTo(instances) { i ->
                    i.instanceId?.let {
                        ManagedInstance(
                            id = it,
                            platform = listOfNotNull(i.platformName, i.platformVersion).joinToString(" ").ifBlank { null },
                            agentVersion = i.agentVersion,
                            ipAddress = i.ipAddress,
                        )
                    }
                }
                next = out.nextToken
            } while (next != null)
            instances
        }
    }

    override suspend fun nameTags(): Map<String, String> = withContext(Dispatchers.IO) {
        ResourceGroupsTaggingClient {
            region = context.region
            credentialsProvider = credentials
        }.use { c ->
            val names = mutableMapOf<String, String>()
            var next: String? = null
            do {
                val out = c.getResources {
                    resourceTypeFilters = listOf("ec2:instance")
                    tagFilters = listOf(TagFilter { key = "Name" })
                    paginationToken = next
                }
                for (mapping in out.resourceTagMappingList.orEmpty()) {
                    val id = mapping.resourceArn?.substringAfterLast("instance/", "")?.ifEmpty { null } ?: continue
                    val name = mapping.tags.orEmpty().firstOrNull { it.key == "Name" }?.value ?: continue
                    names[id] = name
                }
                // The last page carries an empty token rather than none.
                next = out.paginationToken?.ifEmpty { null }
            } while (next != null)
            names
        }
    }

    /** Turns an access-denied answer into [PermissionDeniedException] for [permission]. */
    private inline fun <T> denied(permission: IamPermission, call: () -> T): T = try {
        call()
    } catch (e: ServiceException) {
        if (e.sdkErrorMetadata.errorCode == "AccessDeniedException") throw PermissionDeniedException(permission, e)
        throw e
    }

    private companion object {
        val DESCRIBE_INSTANCE_INFORMATION = IamPermission("ssm:DescribeInstanceInformation")
    }
}
