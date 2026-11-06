// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.errors

import io.github.jmanera.devopsmobiletools.core.aws.context.SelectionRequiredException
import io.github.jmanera.devopsmobiletools.core.aws.sso.SignInRequiredException
import io.github.jmanera.devopsmobiletools.plugin.IamPermission
import io.github.jmanera.devopsmobiletools.plugin.PermissionDeniedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class FailuresTest {

    @Test
    fun anExpiredSignInAsksToSignIn() {
        assertEquals(FailureAction.SIGN_IN, Failures.of(SignInRequiredException("The refresh token expired")).action)
    }

    @Test
    fun noSelectionGoesBackToThePicker() {
        assertEquals(FailureAction.CHANGE_ROLE, Failures.of(SelectionRequiredException()).action)
    }

    @Test
    fun aMissingPermissionIsNamed() {
        val f = Failures.of(PermissionDeniedException(IamPermission("ssm:StartSession")))
        assertEquals(FailureAction.CHANGE_ROLE, f.action)
        assertTrue(f.message, "open a session" in f.message && "ssm:StartSession" in f.message)
    }

    @Test
    fun accessDeniedFromTheBridgeIsReadFromItsText() {
        // As the bridge reports a failed StartSession.
        val f = Failures.of(
            Exception(
                "StartSession failed: operation error SSM: StartSession, https response error StatusCode: 400, " +
                    "RequestID: 1, api error AccessDeniedException: User: arn:aws:sts::123456789012:assumed-role/R/u " +
                    "is not authorized to perform: ssm:StartSession on resource: arn:aws:ec2:eu-west-3:123456789012:instance/i-1",
            ),
        )
        assertEquals(FailureAction.CHANGE_ROLE, f.action)
        assertTrue(f.message, "ssm:StartSession" in f.message)
    }

    @Test
    fun aDisconnectedInstance() {
        val f = Failures.of(
            "StartSession failed: operation error SSM: StartSession, api error TargetNotConnected: i-1 is not connected.",
        )
        assertEquals(FailureAction.RETRY, f.action)
        assertTrue(f.message, "not connected to Systems Manager" in f.message)
    }

    @Test
    fun expiredCredentials() {
        val f = Failures.of("cannot resume session: api error ExpiredTokenException: The security token included in the request is expired")
        assertTrue(f.message, "credentials expired" in f.message)
    }

    @Test
    fun noNetworkByTypeOrText() {
        val wrapped = RuntimeException("call failed", UnknownHostException("ssm.eu-west-3.amazonaws.com"))
        assertTrue(Failures.of(wrapped).message.startsWith("No connection"))
        assertTrue(Failures.of("dial tcp: lookup ssm.eu-west-3.amazonaws.com: no such host").message.startsWith("No connection"))
    }

    @Test
    fun kmsDenied() {
        val f = Failures.of(
            "AccessDeniedException: User: arn:aws:sts::1:assumed-role/R/u is not authorized to perform: kms:GenerateDataKey on resource: key/abc",
        )
        assertTrue(f.message, "KMS key" in f.message && "kms:GenerateDataKey" in f.message)
    }

    @Test
    fun anythingElseKeepsItsText() {
        assertEquals(Failure("something odd", FailureAction.RETRY), Failures.of("something odd"))
    }
}
