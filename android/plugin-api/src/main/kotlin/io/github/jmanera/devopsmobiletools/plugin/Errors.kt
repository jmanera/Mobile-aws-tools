// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.plugin

/**
 * AWS refused a call because the role lacks [permission]. Thrown by plugins
 * so the core can tell the user what to add to the permission set; the
 * message is safe to show.
 */
class PermissionDeniedException(
    val permission: IamPermission,
    cause: Throwable? = null,
) : Exception("The role is not allowed to call ${permission.action}", cause)
