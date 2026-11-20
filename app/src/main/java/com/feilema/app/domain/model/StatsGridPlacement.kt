package com.feilema.app.domain.model

import kotlin.math.abs

/**
 * 统计页两列网格的位置计算（纯计算，单位是“格”，UI 层负责换算成 dp）。
 *
 * 支持：
 * - [computeGridSlots]：按顺序紧凑排布（默认行为 / 增删卡片后的回退）；
 * - [placeAfterResize]：只改一张卡片的大小时，尽量留在原位附近，只把真正被挤到的卡片
 *   按顺序往后让位；缩小时让后面的卡片顺次上移补位，补不了的格子允许留空；
 * - [StatsLayout.withDragged]：拖动排序时被拖卡片占据目标位置，挡路的卡片往后挤，
 *   其余卡片原地不动（允许出现空格）；
 * - [StatsLayout.freeDropRect] / [StatsLayout.withCardPosition]：拖到空出来的格子上。
 */
data class GridRect(
    val column: Int,
    val row: Int,
    val columns: Int,
    val rows: Int,
)

const val STATS_GRID_COLUMNS = 2

private fun cellKey(column: Int, row: Int): Int = column * 1000 + row

private fun GridRect.cells(): List<Int> {
    val result = ArrayList<Int>(columns * rows)
    for (r in row until row + rows) {
        for (c in column until column + columns) {
            result.add(cellKey(c, r))
        }
    }
    return result
}

/** 网格里的阅读顺序：行优先、同一行从左到右。 */
private fun GridRect.rank(): Int = row * STATS_GRID_COLUMNS + column

fun GridRect.intersects(other: GridRect): Boolean =
    column < other.column + other.columns &&
        other.column < column + columns &&
        row < other.row + other.rows &&
        other.row < row + rows

fun GridRect.fitsGrid(): Boolean =
    column >= 0 &&
        row >= 0 &&
        columns in 1..STATS_GRID_COLUMNS &&
        rows >= 1 &&
        column + columns <= STATS_GRID_COLUMNS

/** 按顺序 + 尺寸做紧凑排布：从第 0 行开始，逐行从左到右找空位。 */
fun computeGridSlots(
    order: List<StatsCard>,
    sizeOf: (StatsCard) -> CardSize,
): LinkedHashMap<StatsCard, GridRect> {
    val occupied = HashSet<Int>()
    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) {
        val size = sizeOf(card)
        val rect = firstFreeRect(occupied, size)
        occupied.addAll(rect.cells())
        result[card] = rect
    }
    return result
}

/** 如果布局里保存的位置完整且合法，直接使用；否则返回 null，交给紧凑排布。 */
fun StatsLayout.resolveSlots(): LinkedHashMap<StatsCard, GridRect>? {
    if (positions.isEmpty() || positions.size != order.size) return null
    val occupied = HashSet<Int>()
    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) {
        val position = positions[card] ?: return null
        val size = sizeOf(card)
        val rect = GridRect(position.column, position.row, size.columns, size.rows)
        if (!rect.fitsGrid()) return null
        val cells = rect.cells()
        if (cells.any { it in occupied }) return null
        occupied.addAll(cells)
        result[card] = rect
    }
    return result
}

/** 被调整大小的卡片尝试保留“至少重叠一格”时的候选位移，近的优先、左优先于右、上优先于下。 */
private val RESIZE_OFFSETS = listOf(
    0 to 0,
    -1 to 0, 1 to 0, -2 to 0, 2 to 0,
    0 to -1, -1 to -1, 1 to -1, -2 to -1, 2 to -1,
    0 to -2, -1 to -2, 1 to -2,
    0 to 1, -1 to 1, 1 to 1, -2 to 1, 2 to 1,
    0 to 2, -1 to 2, 1 to 2,
    0 to 3, 0 to -3,
)

/**
 * 调整 [resized] 卡片到 [newSize] 后的位置。
 *
 * 规则（按优先级）：
 * 1. 被调整卡片尽量留在原位附近，要求与旧矩形至少重叠一格；
 *    在满足条件的候选里优先选“不会挤到任何其他卡片”的位置，避免无谓移动。
 * 2. 没有被它挤到的卡片全部原地不动。
 * 3. 被挤到的卡片保持原有先后顺序，从被调整卡片的阅读位置往后找最近的空位，
 *    只向后让位，不会跳到别处；允许网格里留空。
 * 4. 卡片缩小时，排在它后面的卡片顺次向上补位；补不了的格子留空，不整体重排。
 */
fun placeAfterResize(
    previous: Map<StatsCard, GridRect>,
    order: List<StatsCard>,
    resized: StatsCard,
    newSize: CardSize,
    sizeOf: (StatsCard) -> CardSize,
): LinkedHashMap<StatsCard, GridRect> {
    val oldRect = previous[resized]
    val resizedIndex = order.indexOf(resized)
    if (resizedIndex < 0) return LinkedHashMap(previous)
    val others = order.filter { it != resized }

    fun oldRectOf(card: StatsCard): GridRect? {
        val old = previous[card] ?: return null
        val size = sizeOf(card)
        return GridRect(old.column, old.row, size.columns, size.rows)
    }

    // 没有旧位置（理论上不会发生）时退回紧凑排布。
    if (oldRect == null) {
        return computeGridSlots(order) { if (it == resized) newSize else sizeOf(it) }
    }

    // 1. 选择被调整卡片的新位置：与旧位置重叠，且挤到的卡片越少越好。
    var chosen: GridRect? = null
    var chosenConflicts = Int.MAX_VALUE
    for ((deltaColumn, deltaRow) in RESIZE_OFFSETS) {
        val candidate = GridRect(
            column = oldRect.column + deltaColumn,
            row = oldRect.row + deltaRow,
            columns = newSize.columns,
            rows = newSize.rows,
        )
        if (!candidate.fitsGrid() || !candidate.intersects(oldRect)) continue
        val conflicts = others.count { card -> oldRectOf(card)?.intersects(candidate) == true }
        if (conflicts < chosenConflicts) {
            chosen = candidate
            chosenConflicts = conflicts
            if (conflicts == 0) break
        }
    }
    val resizedRect = chosen ?: run {
        val occupied = others.mapNotNull { oldRectOf(it) }.flatMap { it.cells() }.toHashSet()
        firstFreeRect(occupied, newSize)
    }

    // 2. 没被挤到的卡片原地不动。
    val placed = LinkedHashMap<StatsCard, GridRect>()
    placed[resized] = resizedRect
    val occupied = resizedRect.cells().toHashSet()
    for (card in order) {
        if (card == resized) continue
        val rect = oldRectOf(card) ?: continue
        if (rect.intersects(resizedRect)) continue
        placed[card] = rect
        occupied.addAll(rect.cells())
    }

    // 3. 被挤到的卡片按顺序往后让位（阅读顺序只增不减）。
    var cursorRank = resizedRect.rank()
    for (card in order) {
        if (card == resized || card in placed) continue
        val size = sizeOf(card)
        val old = oldRectOf(card)
        val minRank = maxOf(cursorRank, old?.rank() ?: cursorRank)
        val rect = firstFreeRectFromRank(occupied, size, minRank)
        placed[card] = rect
        occupied.addAll(rect.cells())
        cursorRank = maxOf(cursorRank, rect.rank())
    }

    // 4. 缩小时补位：只让后面的卡片向上/向前移动，顺序不变。
    if (newSize.columns * newSize.rows < oldRect.columns * oldRect.rows) {
        var lastRank = resizedRect.rank()
        for (card in order.drop(resizedIndex + 1)) {
            val current = placed[card] ?: continue
            val size = sizeOf(card)
            occupied.removeAll(current.cells().toHashSet())
            val candidate = firstFreeRectBetween(
                occupied = occupied,
                size = size,
                minRank = lastRank + 1,
                maxRank = current.rank() - 1,
            )
            if (candidate != null) {
                placed[card] = candidate
                occupied.addAll(candidate.cells())
                lastRank = candidate.rank()
            } else {
                occupied.addAll(current.cells())
                lastRank = maxOf(lastRank, current.rank())
            }
        }
    }

    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) placed[card]?.let { result[card] = it }
    return result
}

/**
 * 拖动排序：被拖卡片占据目标卡片的位置，挡路的卡片按顺序往后挤开；
 * 其他卡片原地不动，被拖卡片的旧位置留空（不自动补位）。
 *
 * 与 [placeAfterResize] 不同：拖动不会触发向上补位，只有松手后布局才允许留空格。
 */
fun StatsLayout.withDragged(card: StatsCard, toIndex: Int): StatsLayout {
    if (card !in order) return this
    val slots = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
    if (slots.size != order.size) return withMoved(card, toIndex)
    val anchorCard = order.getOrNull(toIndex)?.takeIf { it != card } ?: return this
    val anchorRect = slots[anchorCard] ?: return this

    val nextOrder = order.toMutableList()
    nextOrder.remove(card)
    nextOrder.add(toIndex.coerceIn(0, nextOrder.size), card)

    val oldRects = slots.mapValues { (c, rect) ->
        val size = sizeOf(c)
        GridRect(rect.column, rect.row, size.columns, size.rows)
    }
    val draggedSize = sizeOf(card)
    val draggedRect = GridRect(
        column = anchorRect.column.coerceIn(0, (STATS_GRID_COLUMNS - draggedSize.columns).coerceAtLeast(0)),
        row = anchorRect.row.coerceAtLeast(0),
        columns = draggedSize.columns,
        rows = draggedSize.rows,
    )

    val placed = LinkedHashMap<StatsCard, GridRect>()
    placed[card] = draggedRect
    val occupied = draggedRect.cells().toHashSet()

    // 没被挤到的卡片全部原地不动。
    for (other in order) {
        if (other == card) continue
        val rect = oldRects[other] ?: continue
        if (rect.intersects(draggedRect)) continue
        placed[other] = rect
        occupied.addAll(rect.cells())
    }

    // 被挤到的卡片按新顺序往后让位，不会跳到被拖卡片的旧位置。
    var cursorRank = draggedRect.rank()
    for (other in nextOrder) {
        if (other == card || other in placed) continue
        val size = sizeOf(other)
        val old = oldRects[other]
        val minRank = maxOf(cursorRank, old?.rank() ?: cursorRank)
        val rect = firstFreeRectFromRank(occupied, size, minRank)
        placed[other] = rect
        occupied.addAll(rect.cells())
        cursorRank = maxOf(cursorRank, rect.rank())
    }

    val positions = LinkedHashMap<StatsCard, GridRect>()
    for (other in nextOrder) placed[other]?.let { positions[other] = it }
    return copy(order = nextOrder, positions = positions)
}

private fun isFree(rect: GridRect, others: List<GridRect>): Boolean =
    rect.fitsGrid() && others.none { it.intersects(rect) }

/**
 * 自由落点：把 [card] 放到以 ([column], [row]) 为左上角的位置（尺寸不变），其他卡片位置不动。
 *
 * 用于把卡片拖到布局里空出来的格子；目标越界或和别的卡片重叠时返回 null（此时退回拖动挤位）。
 */
fun StatsLayout.freeDropRect(card: StatsCard, column: Int, row: Int): GridRect? {
    if (card !in order) return null
    val slots = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
    if (slots.size != order.size) return null
    val size = sizeOf(card)
    val rect = GridRect(
        column = column.coerceIn(0, (STATS_GRID_COLUMNS - size.columns).coerceAtLeast(0)),
        row = row.coerceAtLeast(0),
        columns = size.columns,
        rows = size.rows,
    )
    val others = slots.filterKeys { it != card }.values.toList()
    return rect.takeIf { isFree(it, others) }
}

/**
 * 把 [card] 直接放到 [rect]（其他卡片不动，允许留空格）。
 * 与 [freeDropRect] 配套；[rect] 非法或与别人重叠时原样返回。
 */
fun StatsLayout.withCardPosition(card: StatsCard, rect: GridRect): StatsLayout {
    if (card !in order) return this
    val slots = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
    if (slots.size != order.size) return this
    val size = sizeOf(card)
    val target = GridRect(
        column = rect.column.coerceIn(0, (STATS_GRID_COLUMNS - size.columns).coerceAtLeast(0)),
        row = rect.row.coerceAtLeast(0),
        columns = size.columns,
        rows = size.rows,
    )
    val others = slots.filterKeys { it != card }.values.toList()
    if (!isFree(target, others)) return this
    val nextPositions = HashMap(slots)
    nextPositions[card] = target
    return copy(positions = nextPositions)
}

private fun firstFreeRect(
    occupied: Set<Int>,
    size: CardSize,
): GridRect {
    var row = 0
    while (true) {
        val maxColumn = STATS_GRID_COLUMNS - size.columns
        for (column in 0..maxColumn) {
            val rect = GridRect(column, row, size.columns, size.rows)
            if (rect.cells().none { it in occupied }) return rect
        }
        row++
    }
}

/** 从阅读位置 [minRank] 开始按行优先顺序找第一个能放下的位置。 */
private fun firstFreeRectFromRank(
    occupied: Set<Int>,
    size: CardSize,
    minRank: Int,
): GridRect {
    var row = minRank / STATS_GRID_COLUMNS
    while (true) {
        val maxColumn = STATS_GRID_COLUMNS - size.columns
        for (column in 0..maxColumn) {
            val rect = GridRect(column, row, size.columns, size.rows)
            if (rect.rank() < minRank) continue
            if (rect.cells().none { it in occupied }) return rect
        }
        row++
    }
}

/** 在阅读位置区间 [minRank]..[maxRank] 内找第一个空位；找不到返回 null。 */
private fun firstFreeRectBetween(
    occupied: Set<Int>,
    size: CardSize,
    minRank: Int,
    maxRank: Int,
): GridRect? {
    if (maxRank < minRank) return null
    val lastRow = maxRank / STATS_GRID_COLUMNS
    for (row in 0..lastRow) {
        val maxColumn = STATS_GRID_COLUMNS - size.columns
        for (column in 0..maxColumn) {
            val rect = GridRect(column, row, size.columns, size.rows)
            val rank = rect.rank()
            if (rank < minRank) continue
            if (rank > maxRank) break
            if (rect.cells().none { it in occupied }) return rect
        }
    }
    return null
}

/** 离 [anchor] 最近的空位（按行列距离）；都放不下时退化为从左上角找第一个空位。 */
private fun nearestFreeRect(
    anchor: GridRect,
    occupiedRects: List<GridRect>,
    size: CardSize,
): GridRect {
    val occupied = occupiedRects.flatMap { it.cells() }.toHashSet()
    val maxRow = (occupiedRects.maxOfOrNull { it.row + it.rows } ?: anchor.row) + size.rows + 1
    var best: GridRect? = null
    var bestDistance = Int.MAX_VALUE
    for (row in 0..maxRow) {
        val maxColumn = STATS_GRID_COLUMNS - size.columns
        for (column in 0..maxColumn) {
            val candidate = GridRect(column, row, size.columns, size.rows)
            if (candidate.cells().any { it in occupied }) continue
            val distance = abs(candidate.column - anchor.column) + abs(candidate.row - anchor.row)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
    }
    return best ?: firstFreeRect(occupied, size)
}
