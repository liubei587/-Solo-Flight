package com.feilema.app.domain.model

import kotlin.math.abs

/**
 * 统计页两列网格的位置计算。
 *
 * 这里只做纯计算（单位是“格”），UI 层负责换算成 dp。
 * 支持两种排布：
 * - [computeGridSlots]：按顺序紧凑排布（默认行为 / 拖动排序时的预览）；
 * - [placeAfterResize]：只改一张卡片的大小时，优先让它与旧位置至少重叠一格，
 *   其余卡片保持原有先后顺序，并尽量向上/向左补位（允许留下无法填补的空格）。
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

/** 被调整大小的卡片尝试保留“至少重叠一格”时的候选位移，左优先于右、上优先于下。 */
private val RESIZE_OFFSETS = listOf(
    0 to 0,
    -1 to 0, 1 to 0, -2 to 0, 2 to 0,
    0 to -1, -1 to -1, 1 to -1, -2 to -1, 2 to -1,
    0 to -2, -1 to -2, 1 to -2,
    0 to 1, -1 to 1, 1 to 1, -2 to 1, 2 to 1,
    0 to 2, -1 to 2, 1 to 2,
)

/**
 * 调整 [resized] 卡片到 [newSize] 后的位置：
 * 1. 被调整卡片优先留在旧位置附近，要求与旧矩形至少重叠一格；
 *    候选顺序为 原位 → 左移 → 右移 → 上移 → 下移（左优先于右，上优先于下）。
 * 2. 排在被调整卡片之前的卡片保持原位（放不下时从最上方找空位）。
 * 3. 之后的卡片保持原有先后顺序，从被调整卡片的位置开始依次寻找最早的空位，
 *    能补位就补位（例如 2×2 缩成 2×1 空出一行，后面的单格卡片会顺次上移），
 *    补不了的格子允许留空，不做整体重排。
 */
fun placeAfterResize(
    previous: Map<StatsCard, GridRect>,
    order: List<StatsCard>,
    resized: StatsCard,
    newSize: CardSize,
    sizeOf: (StatsCard) -> CardSize,
): LinkedHashMap<StatsCard, GridRect> {
    val occupied = HashSet<Int>()
    val placed = HashMap<StatsCard, GridRect>()
    val resizedIndex = order.indexOf(resized)

    fun tryPlace(card: StatsCard, rect: GridRect) {
        occupied.addAll(rect.cells())
        placed[card] = rect
    }

    fun canPlace(rect: GridRect): Boolean =
        rect.fitsGrid() && rect.cells().none { it in occupied }

    fun keepOrFirstFree(card: StatsCard, size: CardSize): GridRect {
        val old = previous[card]
        if (old != null) {
            val candidate = GridRect(old.column, old.row, size.columns, size.rows)
            if (old.columns == size.columns && old.rows == size.rows && canPlace(candidate)) {
                return candidate
            }
        }
        return firstFreeRect(occupied, size)
    }

    // 1. 排在被调整卡片之前的卡片：尽量保持原位。
    for (card in order.take(resizedIndex)) {
        tryPlace(card, keepOrFirstFree(card, sizeOf(card)))
    }

    // 2. 被调整卡片：在原位置附近找候选，要求与旧矩形至少重叠一格。
    val oldRect = previous[resized]
    var resizedRect: GridRect? = null
    if (oldRect != null) {
        for ((deltaColumn, deltaRow) in RESIZE_OFFSETS) {
            val candidate = GridRect(
                column = oldRect.column + deltaColumn,
                row = oldRect.row + deltaRow,
                columns = newSize.columns,
                rows = newSize.rows,
            )
            if (candidate.fitsGrid() && candidate.intersects(oldRect) && canPlace(candidate)) {
                resizedRect = candidate
                break
            }
        }
    }
    if (resizedRect == null) {
        resizedRect = firstFreeRect(occupied, newSize)
    }
    tryPlace(resized, resizedRect)

    // 3. 之后的卡片：保持顺序，从被调整卡片的起始格往后找最早的空位。
    var cursorColumn = resizedRect.column
    var cursorRow = resizedRect.row
    for (card in order.drop(resizedIndex + 1)) {
        val size = sizeOf(card)
        val rect = firstFreeRectFrom(occupied, size, cursorColumn, cursorRow)
        tryPlace(card, rect)
        cursorColumn = rect.column
        cursorRow = rect.row
    }

    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) {
        placed[card]?.let { result[card] = it }
    }
    return result
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

/**
 * 拖动排序：只交换被拖动卡片与目标卡片的落点，其他卡片原地不动。
 *
 * 与 [placeAfterResize] 不同，拖动不会让后面的卡片顺次上移补位；
 * 需要留空时宁可让被换下来的目标卡片找最近空位，也不整体重排。
 */
fun StatsLayout.withDragged(card: StatsCard, toIndex: Int): StatsLayout {
    if (card !in order) return this
    val slots = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
    if (slots.size != order.size) return withMoved(card, toIndex)
    val oldRect = slots[card] ?: return withMoved(card, toIndex)
    val anchorCard = order.getOrNull(toIndex)?.takeIf { it != card } ?: return this
    val anchorRect = slots[anchorCard] ?: return this
    val draggedSize = sizeOf(card)
    val anchorSize = sizeOf(anchorCard)

    val nextOrder = order.toMutableList()
    nextOrder.remove(card)
    nextOrder.add(toIndex.coerceIn(0, nextOrder.size), card)

    // 其他卡片保持原位：只计算被拖动卡片与目标卡片的新位置。
    val others = slots.filterKeys { it != card && it != anchorCard }.values.toList()
    val draggedCandidate = GridRect(
        column = anchorRect.column.coerceIn(0, (STATS_GRID_COLUMNS - draggedSize.columns).coerceAtLeast(0)),
        row = anchorRect.row,
        columns = draggedSize.columns,
        rows = draggedSize.rows,
    )
    val draggedRect = if (isFree(draggedCandidate, others)) {
        draggedCandidate
    } else {
        nearestFreeRect(draggedCandidate, others, draggedSize)
    }

    val anchorCandidate = GridRect(
        column = oldRect.column,
        row = oldRect.row,
        columns = anchorSize.columns,
        rows = anchorSize.rows,
    )
    val occupiedForAnchor = others + draggedRect
    val anchorRectNew = if (isFree(anchorCandidate, occupiedForAnchor)) {
        anchorCandidate
    } else {
        nearestFreeRect(anchorCandidate, occupiedForAnchor, anchorSize)
    }

    val nextPositions = HashMap(slots)
    nextPositions[card] = draggedRect
    nextPositions[anchorCard] = anchorRectNew
    return copy(order = nextOrder, positions = nextPositions)
}

private fun isFree(rect: GridRect, others: List<GridRect>): Boolean =
    rect.fitsGrid() && others.none { it.intersects(rect) }

/**
 * 自由落点：把 [card] 放到以 ([column], [row]) 为左上角的位置（尺寸不变），其他卡片位置不动。
 *
 * 用于把卡片拖到布局里空出来的格子（例如改过大小后留下的空位）。
 * 目标位置越界、和别的卡片重叠时返回 null（此时应退回「与目标卡片交换」的语义）。
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
 *
 * 与 [freeDropRect] 配套使用；[rect] 非法或与别人重叠时原样返回（不修改布局）。
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

/** 离 [anchor] 最近的空位（按行列距离），都放不下时退化为从左上角找第一个空位。 */
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

/**
 * 从 ([startColumn], [startRow]) 开始按行优先顺序找第一个能放下的位置。
 * 同行的列不会早于 startColumn，保证卡片的先后顺序不变。
 */
private fun firstFreeRectFrom(
    occupied: Set<Int>,
    size: CardSize,
    startColumn: Int,
    startRow: Int,
): GridRect {
    var row = startRow
    while (true) {
        val firstColumn = if (row == startRow) startColumn.coerceAtLeast(0) else 0
        val maxColumn = STATS_GRID_COLUMNS - size.columns
        for (column in firstColumn..maxColumn) {
            val rect = GridRect(column, row, size.columns, size.rows)
            if (rect.cells().none { it in occupied }) return rect
        }
        row++
    }
}
