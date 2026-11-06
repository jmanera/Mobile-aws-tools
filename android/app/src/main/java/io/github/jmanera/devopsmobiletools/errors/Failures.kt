// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.errors

import io.github.jmanera.devopsmobiletools.core.aws.context.SelectionRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoUnauthorizedException
import io.github.jmanera.devopsmobiletools.plugin.PermissionDeniedException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** What the user can do about a failure; each screen turns it into a button. */
enum class FailureAction {
    /** Sign in to IAM Identity Center again. */
    SIGN_IN,

    /** Pick another account, role or region, or fix the role's permissions. */
    CHANGE_ROLE,

    /** Try again: refresh the list or reconnect the session. */
    RETRY,
}

/** A failure in a sentence, and the action that fixes it. */
data class Failure(val message: String, val action: FailureAction)

/**
 * Turns what went wrong into a [Failure]: by exception type when the core or
 * a plugin raised a typed one, else by the AWS error text that the SDKs and
 * the bridge put in their messages. Those messages name the operation and
 * the error code, never the credentials.
 */
object Failures {

    fun of(e: Throwable): Failure = when (e) {
        is SignInRequiredException, is SsoUnauthorizedException -> SIGNED_OUT
        is SelectionRequiredException -> Failure("Pick an account, a role and a region first.", FailureAction.CHANGE_ROLE)
        is PermissionDeniedException -> denied(e.permission.action)
        else -> when {
            e.causes().any { it is UnknownHostException || it is ConnectException || it is SocketTimeoutException } -> OFFLINE
            else -> of(e.causes().mapNotNull { it.message }.joinToString(" | ").ifBlank { e.javaClass.simpleName })
        }
    }

    /** The same, from an error that only exists as text, such as a bridge state detail. */
    fun of(detail: String): Failure {
        val d = detail.lowercase()
        return when {
            "expiredtoken" in d || "security token included in the request is expired" in d ||
                "credentials have expired" in d -> Failure(
                "The role's temporary credentials expired. Reconnect to get new ones.",
                FailureAction.RETRY,
            )
            "unrecognizedclient" in d || "invalidclienttokenid" in d -> SIGNED_OUT
            "accessdenied" in d || "not authorized to perform" in d -> {
                val action = Regex("""perform: ([a-z0-9-]+:[A-Za-z0-9]+)""").find(detail)?.groupValues?.get(1)
                denied(action ?: if ("kms" in d) "kms:GenerateDataKey" else "the action")
            }
            "targetnotconnected" in d || "is not connected" in d -> Failure(
                "The instance is not connected to Systems Manager: it may be stopped or its SSM Agent offline. " +
                    "Refresh the list, or check the instance in Fleet Manager.",
                FailureAction.RETRY,
            )
            "kms" in d && ("encrypt" in d || "datakey" in d) -> Failure(
                "The session could not be encrypted with the KMS key set in Session Manager's preferences.",
                FailureAction.RETRY,
            )
            OFFLINE_HINTS.any { it in d } -> OFFLINE
            else -> Failure(detail, FailureAction.RETRY)
        }
    }

    private fun denied(action: String): Failure {
        val what = when {
            action == "ssm:StartSession" -> "open a session on this instance"
            action == "ssm:DescribeInstanceInformation" -> "list instances"
            action.startsWith("kms:") -> "use the KMS key that encrypts sessions"
            else -> "do this"
        }
        return Failure(
            "This role is not allowed to $what ($action). Add it to the role's permission set in " +
                "IAM Identity Center, or pick another role.",
            FailureAction.CHANGE_ROLE,
        )
    }

    private fun Throwable.causes(): List<Throwable> = generateSequence(this) { it.cause }.take(10).toList()

    private val SIGNED_OUT = Failure("Your sign-in to IAM Identity Center has expired. Sign in again.", FailureAction.SIGN_IN)

    private val OFFLINE = Failure("No connection to AWS. Check the network and try again.", FailureAction.RETRY)

    private val OFFLINE_HINTS = listOf(
        "no such host", "unable to resolve host", "network is unreachable", "no route to host",
        "i/o timeout", "connection refused", "unknownhost",
    )
}
