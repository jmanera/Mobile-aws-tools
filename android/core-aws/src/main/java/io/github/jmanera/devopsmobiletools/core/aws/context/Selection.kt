// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.context

import io.github.jmanera.devopsmobiletools.core.aws.sso.SsoRole
import java.io.File
import java.io.IOException
import java.util.Properties

/** The role the user works as and the region plugins act in. */
data class Selection(val role: SsoRole, val region: String) {
    init {
        require(AwsRegions.of(region) != null) { "not a known region: $region" }
    }
}

/**
 * The last [Selection], per access portal: signing in to another
 * organization must not bring back a role from the previous one.
 */
interface SelectionStore {
    fun load(startUrl: String): Selection?

    fun save(startUrl: String, selection: Selection)
}

class InMemorySelectionStore : SelectionStore {
    private var saved: Pair<String, Selection>? = null

    @Synchronized override fun load(startUrl: String) = saved?.takeIf { it.first == startUrl }?.second

    @Synchronized override fun save(startUrl: String, selection: Selection) {
        saved = startUrl to selection
    }
}

/** Account and role names are not secret, so the selection is a plain properties file. */
class FileSelectionStore(private val file: File) : SelectionStore {

    @Synchronized override fun load(startUrl: String): Selection? {
        if (!file.exists()) return null
        val p = Properties()
        try {
            file.inputStream().use(p::load)
        } catch (e: IOException) {
            return null
        }
        if (p.getProperty(START_URL) != startUrl) return null
        return try {
            Selection(
                role = SsoRole(
                    accountId = p.getProperty(ACCOUNT_ID) ?: return null,
                    accountName = p.getProperty(ACCOUNT_NAME) ?: return null,
                    roleName = p.getProperty(ROLE_NAME) ?: return null,
                ),
                region = p.getProperty(REGION) ?: return null,
            )
        } catch (e: IllegalArgumentException) {
            // A region dropped from the list since it was saved.
            null
        }
    }

    @Synchronized override fun save(startUrl: String, selection: Selection) {
        val p = Properties()
        p.setProperty(START_URL, startUrl)
        p.setProperty(ACCOUNT_ID, selection.role.accountId)
        p.setProperty(ACCOUNT_NAME, selection.role.accountName)
        p.setProperty(ROLE_NAME, selection.role.roleName)
        p.setProperty(REGION, selection.region)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { p.store(it, null) }
        if (!tmp.renameTo(file)) throw IOException("Cannot write ${file.path}")
    }

    private companion object {
        const val START_URL = "startUrl"
        const val ACCOUNT_ID = "accountId"
        const val ACCOUNT_NAME = "accountName"
        const val ROLE_NAME = "roleName"
        const val REGION = "region"
    }
}
