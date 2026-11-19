package com.chronos.clock.notifications

import com.chronos.clock.domain.model.NotificationGroup
import com.chronos.clock.domain.model.NotificationItem
import com.chronos.clock.domain.model.NotificationSettings
import com.chronos.clock.core.constants.AppConstants

/**
 * Pure, unit-testable reducers for the in-memory notification list.
 *
 * Guarantees:
 *  - no duplicates (each Android key appears at most once),
 *  - updates replace in place,
 *  - removals delete exactly one entry,
 *  - group summaries are collapsed into their children,
 *  - the list is bounded to [AppConstants.MAX_NOTIFICATIONS].
 */
object NotificationReducer {

    fun onPosted(items: List<NotificationItem>, item: NotificationItem): List<NotificationItem> {
        val without = items.filterNot { it.key == item.key }
        val merged = if (item.isGroupSummary) {
            // A summary only exists to bundle its children; keep children only.
            without.filterNot {
                !it.isGroupSummary && it.groupKey != null && it.groupKey == item.groupKey
            }.let { existing ->
                // If the summary arrived first, it may be the only entry so far.
                if (existing.none { it.groupKey == item.groupKey && !it.isGroupSummary }) {
                    existing + item
                } else {
                    existing
                }
            }
        } else {
            // Drop a placeholder summary that was posted before its children.
            val children = without.filterNot {
                it.isGroupSummary && it.groupKey != null && it.groupKey == item.groupKey
            }
            children + item
        }
        return merged
            .sortedByDescending { it.postTime }
            .take(AppConstants.MAX_NOTIFICATIONS)
    }

    fun onRemoved(items: List<NotificationItem>, key: String): List<NotificationItem> =
        items.filterNot { it.key == key }

    /** Applies the user's package allow-list (empty = all apps). */
    fun applyFilter(items: List<NotificationItem>, settings: NotificationSettings): List<NotificationItem> {
        if (settings.allowedPackages.isEmpty()) return items
        return items.filter { it.packageName in settings.allowedPackages }
    }

    /** Groups per app, most recent first, for the icon ring. */
    fun group(items: List<NotificationItem>): List<NotificationGroup> =
        items.filterNot { it.isGroupSummary }
            .groupBy { it.packageName }
            .map { (pkg, list) ->
                val sorted = list.sortedByDescending { it.postTime }
                NotificationGroup(
                    packageName = pkg,
                    appLabel = sorted.first().appLabel,
                    count = list.size,
                    latest = sorted.first(),
                    // The app's full set of active (unseen) messages,
                    // newest first — reply sheet context.
                    items = sorted,
                )
            }
            .sortedByDescending { it.latest.postTime }
}
