package com.feilema.app.data.preferences

import android.content.Context
import com.feilema.app.domain.model.CardSize
import com.feilema.app.domain.model.GridRect
import com.feilema.app.domain.model.StatsCard
import com.feilema.app.domain.model.StatsLayout
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 统计页布局偏好：卡片顺序、尺寸与显示集合。
 *
 * 兼容旧版本（v0.4.x）：旧键拆成图表/指标两组，这里在新键缺失时把它们合并成统一布局。
 */
@Singleton
class StatsPreferences @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _layout = MutableStateFlow(readLayout())
    val layout: StateFlow<StatsLayout> = _layout.asStateFlow()

    fun saveLayout(layout: StatsLayout) {
        prefs.edit()
            .putString(KEY_ORDER, layout.order.joinToString(",") { it.name })
            .putString(
                KEY_SIZES,
                layout.sizes.entries.joinToString(",") { "${it.key.name}:${it.value.name}" },
            )
            .putString(
                KEY_POSITIONS,
                layout.positions.entries.joinToString(",") { (card, rect) ->
                    "${card.name}:${rect.column},${rect.row}"
                },
            )
            .apply()
        _layout.value = layout
    }

    fun reset() {
        prefs.edit().clear().apply()
        _layout.value = StatsLayout()
    }

    private fun readLayout(): StatsLayout {
        val rawOrder = prefs.getString(KEY_ORDER, null)
        if (rawOrder != null) {
            val order = rawOrder.split(",")
                .mapNotNull { name -> StatsCard.entries.firstOrNull { it.name == name } }
                .distinct()
            return StatsLayout(
                order = order.ifEmpty { StatsCard.defaultOrder },
                sizes = readSizes(),
                positions = readPositions(),
            )
        }
        return readLegacyLayout()
    }

    private fun readLegacyLayout(): StatsLayout {
        val legacyCharts = readLegacyEnabled(KEY_LEGACY_CHARTS)
        val legacyMetrics = readLegacyEnabled(KEY_LEGACY_METRICS)
        val charts = readChartNames() ?: legacyCharts
        val metrics = readMetricNames() ?: legacyMetrics
        val chartCards = charts
            ?.mapNotNull { name -> StatsCard.entries.firstOrNull { it.name == name } }
            .orEmpty()
            .ifEmpty { StatsCard.entries.filter { it.isChart } }
        val metricCards = metrics
            ?.mapNotNull { name -> StatsCard.entries.firstOrNull { it.name == name } }
            .orEmpty()
            .ifEmpty { StatsCard.entries.filterNot { it.isChart } }
        // 旧 key 里的顺序/开关只决定可见的图表与指标卡。
        val legacyHiddenCharts = prefs.getString(KEY_LEGACY_HIDDEN_CHARTS, null)
            ?.split(",")
            ?.mapNotNull { name -> StatsCard.entries.firstOrNull { it.name == name } }
            ?.toSet()
            .orEmpty()
        val legacyHiddenMetrics = prefs.getString(KEY_LEGACY_HIDDEN_METRICS, null)
            ?.split(",")
            ?.mapNotNull { name -> StatsCard.entries.firstOrNull { it.name == name } }
            ?.toSet()
            .orEmpty()
        val order = (chartCards + metricCards)
            .filterNot { it in legacyHiddenCharts || it in legacyHiddenMetrics }
            .distinct()
            .ifEmpty { StatsCard.defaultOrder }
        return StatsLayout(order = order, sizes = readLegacySizes())
    }

    private fun readChartNames(): List<String>? =
        prefs.getString(KEY_LEGACY_CHART_ORDER, null)?.split(",")?.filter { it.isNotBlank() }

    private fun readMetricNames(): List<String>? =
        prefs.getString(KEY_LEGACY_METRIC_ORDER, null)?.split(",")?.filter { it.isNotBlank() }

    private fun readLegacyEnabled(key: String): List<String>? =
        prefs.getString(key, null)?.split(",")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }

    private fun readSizes(): Map<StatsCard, CardSize> {
        val raw = prefs.getString(KEY_SIZES, null) ?: return emptyMap()
        return raw.split(",").mapNotNull { item ->
            val parts = item.split(":")
            if (parts.size != 2) return@mapNotNull null
            val card = StatsCard.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            // v0.6.x 还有 1×2 的「高卡片」，已删除该尺寸，读到旧值时升级成 2×2。
            val size = if (parts[1] == "TALL") {
                CardSize.LARGE
            } else {
                CardSize.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
            }
            card to size
        }.toMap()
    }

    private fun readPositions(): Map<StatsCard, GridRect> {
        val raw = prefs.getString(KEY_POSITIONS, null) ?: return emptyMap()
        return raw.split(",").mapNotNull { item ->
            val parts = item.split(":")
            if (parts.size != 2) return@mapNotNull null
            val card = StatsCard.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            val coordinates = parts[1].split(",")
            if (coordinates.size != 2) return@mapNotNull null
            val column = coordinates[0].toIntOrNull() ?: return@mapNotNull null
            val row = coordinates[1].toIntOrNull() ?: return@mapNotNull null
            card to GridRect(column = column, row = row, columns = 0, rows = 0)
        }.toMap()
    }

    private fun readLegacySizes(): Map<StatsCard, CardSize> {
        val raw = prefs.getString(KEY_LEGACY_SIZES, null) ?: return emptyMap()
        return raw.split(",").mapNotNull { item ->
            val parts = item.split(":")
            if (parts.size != 2) return@mapNotNull null
            val card = StatsCard.entries.firstOrNull { it.name == parts[0] } ?: return@mapNotNull null
            when (parts[1]) {
                "NORMAL" -> card to CardSize.SMALL
                "WIDE" -> card to CardSize.WIDE
                else -> null
            }
        }.toMap()
    }

    private companion object {
        const val PREFS_NAME = "stats_prefs"
        const val KEY_ORDER = "layout_order"
        const val KEY_SIZES = "layout_sizes"
        const val KEY_POSITIONS = "layout_positions"

        // v0.4.x 的旧键，仅用于迁移。
        const val KEY_LEGACY_CHART_ORDER = "chart_order"
        const val KEY_LEGACY_METRIC_ORDER = "metric_order"
        const val KEY_LEGACY_HIDDEN_CHARTS = "hidden_charts"
        const val KEY_LEGACY_HIDDEN_METRICS = "hidden_metrics"
        const val KEY_LEGACY_SIZES = "metric_sizes"
        const val KEY_LEGACY_CHARTS = "enabled_charts"
        const val KEY_LEGACY_METRICS = "enabled_metrics"
    }
}
