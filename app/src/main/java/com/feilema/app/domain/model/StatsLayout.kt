package com.feilema.app.domain.model

/**
 * 统计页卡片尺寸：宽高各占 1 或 2 格（网格固定两列）。
 */
enum class CardSize(val label: String, val columns: Int, val rows: Int) {
    SMALL("1×1", 1, 1),
    WIDE("2×1", 2, 1),
    LARGE("2×2", 2, 2),
}

enum class StatsCardKind { CHART, METRIC }

/**
 * 统计页上可自由摆放的卡片（图表 + 指标卡统一成一类）。
 */
enum class StatsCard(val label: String, val kind: StatsCardKind) {
    TREND("趋势", StatsCardKind.CHART),
    HOUR_DISTRIBUTION("时段分布", StatsCardKind.CHART),
    WEEKDAY_DISTRIBUTION("周内分布", StatsCardKind.CHART),
    BOX_PLOT("箱线图（每周）", StatsCardKind.CHART),
    PERIOD_COUNT("周期次数", StatsCardKind.METRIC),
    ACTIVE_DAYS("活跃天数", StatsCardKind.METRIC),
    MAX_DAY("单日最高", StatsCardKind.METRIC),
    DAILY_AVERAGE("日均", StatsCardKind.METRIC),
    ACTIVE_DAILY_AVERAGE("活跃日均", StatsCardKind.METRIC),
    CHANGE("环比", StatsCardKind.METRIC),
    AVERAGE_INTERVAL("平均间隔", StatsCardKind.METRIC),
    MEDIAN_INTERVAL("间隔中位数", StatsCardKind.METRIC),
    LONGEST_STREAK("最长连续", StatsCardKind.METRIC),
    BEST_HOUR("最常时段", StatsCardKind.METRIC),
    BEST_WEEKDAY("最常星期", StatsCardKind.METRIC),
    LAST_RECORD("上次记录", StatsCardKind.METRIC),
    ;

    val isChart: Boolean get() = kind == StatsCardKind.CHART

    companion object {
        /** 默认顺序：4 张图表在上，12 张指标卡在下。 */
        val defaultOrder: List<StatsCard> = entries.toList()
    }
}

/** 默认尺寸：趋势/箱线图 2×2，时段/周内分布 2×1，指标卡 1×1。 */
fun defaultCardSize(card: StatsCard): CardSize = when (card) {
    StatsCard.TREND -> CardSize.LARGE
    StatsCard.BOX_PLOT -> CardSize.LARGE
    StatsCard.HOUR_DISTRIBUTION,
    StatsCard.WEEKDAY_DISTRIBUTION,
    -> CardSize.WIDE
    else -> CardSize.SMALL
}

/**
 * 统计页布局：卡片顺序、尺寸、位置与被移除（隐藏）的卡片。
 *
 * order 里只保留当前可见卡片；从 order 移除即“删除卡片”，
 * 所有卡片仍可从「增加卡片」重新加回来。
 *
 * positions 在“调整单张卡片大小”和“拖动排序”时保留：改大小时被改卡片锚定左上角，
 * 被挤到的卡片沿原列下移、保持先后顺序；拖动是“插入”语义，目标及后面的卡片依次下移，
 * 两种情况都允许网格里留空。缩小时只有紧挨着空位的卡片会补位（相邻链式）。
 * 增删卡片时会清空 positions，回到紧凑排布。
 */
data class StatsLayout(
    val order: List<StatsCard> = StatsCard.defaultOrder,
    val sizes: Map<StatsCard, CardSize> = emptyMap(),
    val positions: Map<StatsCard, GridRect> = emptyMap(),
) {

    fun sizeOf(card: StatsCard): CardSize = sizes[card] ?: defaultCardSize(card)

    fun isVisible(card: StatsCard): Boolean = card in order

    val hiddenCards: List<StatsCard>
        get() = StatsCard.defaultOrder.filterNot { it in order }

    fun withMoved(card: StatsCard, toIndex: Int): StatsLayout {
        if (card !in order) return this
        val order = order.toMutableList()
        val from = order.indexOf(card)
        order.removeAt(from)
        val target = toIndex.coerceIn(0, order.size)
        order.add(target, card)
        return copy(order = order, positions = emptyMap())
    }

    fun withRemoved(card: StatsCard): StatsLayout = copy(
        order = order - card,
        sizes = sizes - card,
        positions = emptyMap(),
    )

    fun withAdded(card: StatsCard): StatsLayout =
        if (card in order) this else copy(order = order + card, positions = emptyMap())

    /**
     * 调整一张卡片的大小：锚定原左上角；被挤到的卡片沿自己的列向下让位并保持先后顺序。
     * 缩小时只有紧挨着空位的卡片会向上补位，允许网格里留空。
     */
    fun withSize(card: StatsCard, size: CardSize): StatsLayout {
        if (card !in order) return this
        val nextSizes = sizes + (card to size)
        val currentSlots = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
        val nextPositions = placeAfterResize(
            previous = currentSlots,
            order = order,
            resized = card,
            newSize = size,
            sizeOf = { nextSizes[it] ?: defaultCardSize(it) },
        )
        return copy(sizes = nextSizes, positions = nextPositions)
    }

    fun reset(): StatsLayout = StatsLayout()
}
