// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.self.life

object FundingCampaignChecker {
    private const val FUNDING_CAMPAIGN_START = 1703160000000L
    private const val FUNDING_CAMPAIGN_END = 1717200000000L

    fun campaignRunning(): Boolean {
        val currentTime = System.currentTimeMillis()
        return currentTime >= FUNDING_CAMPAIGN_START && currentTime <= FUNDING_CAMPAIGN_END
    }
}
