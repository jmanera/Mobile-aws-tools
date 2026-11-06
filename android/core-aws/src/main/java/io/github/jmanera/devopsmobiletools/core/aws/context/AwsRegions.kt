// Copyright 2026 The DevOps Mobile Tools Authors.
// SPDX-License-Identifier: Apache-2.0

package io.github.jmanera.devopsmobiletools.core.aws.context

data class AwsRegion(val code: String, val name: String) {
    override fun toString() = "$name ($code)"
}

/**
 * Commercial AWS regions, for the region picker. Opt-in regions only work
 * once enabled in the account; calls there fail like any other refusal.
 */
object AwsRegions {
    val ALL = listOf(
        AwsRegion("us-east-1", "US East (N. Virginia)"),
        AwsRegion("us-east-2", "US East (Ohio)"),
        AwsRegion("us-west-1", "US West (N. California)"),
        AwsRegion("us-west-2", "US West (Oregon)"),
        AwsRegion("af-south-1", "Africa (Cape Town)"),
        AwsRegion("ap-east-1", "Asia Pacific (Hong Kong)"),
        AwsRegion("ap-east-2", "Asia Pacific (Taipei)"),
        AwsRegion("ap-south-1", "Asia Pacific (Mumbai)"),
        AwsRegion("ap-south-2", "Asia Pacific (Hyderabad)"),
        AwsRegion("ap-southeast-1", "Asia Pacific (Singapore)"),
        AwsRegion("ap-southeast-2", "Asia Pacific (Sydney)"),
        AwsRegion("ap-southeast-3", "Asia Pacific (Jakarta)"),
        AwsRegion("ap-southeast-4", "Asia Pacific (Melbourne)"),
        AwsRegion("ap-southeast-5", "Asia Pacific (Malaysia)"),
        AwsRegion("ap-southeast-6", "Asia Pacific (New Zealand)"),
        AwsRegion("ap-southeast-7", "Asia Pacific (Thailand)"),
        AwsRegion("ap-northeast-1", "Asia Pacific (Tokyo)"),
        AwsRegion("ap-northeast-2", "Asia Pacific (Seoul)"),
        AwsRegion("ap-northeast-3", "Asia Pacific (Osaka)"),
        AwsRegion("ca-central-1", "Canada (Central)"),
        AwsRegion("ca-west-1", "Canada West (Calgary)"),
        AwsRegion("eu-central-1", "Europe (Frankfurt)"),
        AwsRegion("eu-central-2", "Europe (Zurich)"),
        AwsRegion("eu-west-1", "Europe (Ireland)"),
        AwsRegion("eu-west-2", "Europe (London)"),
        AwsRegion("eu-west-3", "Europe (Paris)"),
        AwsRegion("eu-south-1", "Europe (Milan)"),
        AwsRegion("eu-south-2", "Europe (Spain)"),
        AwsRegion("eu-north-1", "Europe (Stockholm)"),
        AwsRegion("il-central-1", "Israel (Tel Aviv)"),
        AwsRegion("me-south-1", "Middle East (Bahrain)"),
        AwsRegion("me-central-1", "Middle East (UAE)"),
        AwsRegion("mx-central-1", "Mexico (Central)"),
        AwsRegion("sa-east-1", "South America (São Paulo)"),
    )

    private val byCode = ALL.associateBy { it.code }

    fun of(code: String): AwsRegion? = byCode[code]
}
