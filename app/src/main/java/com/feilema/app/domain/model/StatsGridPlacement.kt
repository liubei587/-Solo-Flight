package com.feilema.app.domain.model

/**
 * 统计页两列网格的位置计算（纯计算，单位是“格”，UI 层负责换算成 dp）。
 *
 * 布局规则（本轮重做）：
 * - 卡片顺序 = 阅读顺序（先行后列、行内从左到右），顺序由 [StatsLayout.order] 决定；
 * - 拖动排序是“插入”而不是“交换”：把卡片插到目标卡片前面/后面，目标及其后面的卡片
 *   按原顺序整体向后（向下）让位，不会把目标卡片拽到被拖卡片的旧位置；
 * - 调整大小时被改卡片锚定原左上角，放不下的卡片沿自己的列向下让位，
 *   这样下面的卡片会按原布局整体下移，而不是被塞到别的地方；
 * - 只有缩小时才补位，而且只允许“紧挨着空位”的卡片向上补，空位和远处卡片之间不存在
 *   相邻关系时保持留空，不会从下面很远的地方飞一张卡片上来；
 * - 拖动到明确的空格时走 [StatsLayout.freeDropRect] / [StatsLayout.withCardPosition]，
 *   只移动被拖卡片，其余卡片不动。
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

/** 按顺序 + 尺寸做紧凑排布：从第 0 行开始，逐行找空位。 */
fun computeGridSlots(
    order: List<StatsCard>,
    sizeOf: (StatsCard) -> CardSize,
): LinkedHashMap<StatsCard, GridRect> {
    val occupied = HashSet<Int>()
    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) {
        val rect = firstFreeRect(occupied, sizeOf(card))
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

/**
 * 调整 [resized] 的大小后的完整位置表。
 *
 * 规则：
 * 1. 被调整卡片锚定原左上角（列夹取到两列网格内），只向右/向下生长；
 * 2. 按阅读顺序摆放其余卡片：原来的位置没被占就原地不动，被占则沿同一个列向下让位，
 *    这样“下面的卡片按原布局整体下移”，不会改变相对顺序；
 * 3. 卡片缩小时，被空出来的格子按“紧挨着”的规则向上补位：只有正好在空位正下方、
 *    并且整行都能放下的卡片才会移动，移动后又空出来的格子可以继续带动更下面的卡片
 *    （相邻链式补位），和空位不相邻的卡片不会移动。
 */
fun placeAfterResize(
    previous: Map<StatsCard, GridRect>,
    order: List<StatsCard>,
    resized: StatsCard,
    newSize: CardSize,
    sizeOf: (StatsCard) -> CardSize,
): LinkedHashMap<StatsCard, GridRect> {
    if (resized !in order) return LinkedHashMap(previous)

    // previous 里的矩形带着调整前的尺寸（resolveSlots / computeGridSlots 生成），
    // 不能再用 sizeOf(resized)（调用方可能已经把新尺寸写进去了）。
    val oldRect = previous[resized] ?: return computeGridSlots(order) {
        if (it == resized) newSize else sizeOf(it)
    }

    val newColumns = newSize.columns.coerceIn(1, STATS_GRID_COLUMNS)
    val baseRect = GridRect(
        column = oldRect.column.coerceIn(0, (STATS_GRID_COLUMNS - newColumns).coerceAtLeast(0)),
        row = oldRect.row.coerceAtLeast(0),
        columns = newColumns,
        rows = newSize.rows,
    )

    val placed = LinkedHashMap<StatsCard, GridRect>()
    val occupied = HashSet<Int>()
    for (card in order) {
        val size = if (card == resized) newSize else sizeOf(card)
        var rect: GridRect = if (card == resized) {
            baseRect
        } else {
            val old = previous[card] ?: continue
            GridRect(old.column, old.row, size.columns, size.rows)
        }
        rect = rect.copy(
            column = rect.column.coerceIn(
                0,
                (STATS_GRID_COLUMNS - rect.columns).coerceAtLeast(0),
            ),
        )
        if (!rect.fitsGrid()) continue
        rect = pushDown(occupied, rect)
        placed[card] = rect
        occupied.addAll(rect.cells())
    }

    val freed = oldRect.cells().toSet() - baseRect.cells().toSet()
    if (freed.isNotEmpty()) {
        fillAdjacentGaps(
            order = order,
            placed = placed,
            occupied = occupied,
            freed = freed.toMutableSet(),
        )
    }

    val result = LinkedHashMap<StatsCard, GridRect>()
    for (card in order) placed[card]?.let { result[card] = it }
    return result
}

/** 沿同一列向下找到第一个放得下的行。 */
private fun pushDown(occupied: Set<Int>, rect: GridRect): GridRect {
    var candidate = rect
    while (candidate.cells().any { it in occupied }) {
        candidate = candidate.copy(row = candidate.row + 1)
    }
    return candidate
}

/**
 * 只补“紧挨着空位”的格子：卡片上方一整行必须都是空的，且这一行里至少有一格是刚从
 * 被调整卡片那里空出来的；移动后卡片下方新空出的格子会加入 [freed]，从而允许相邻的
 * 下一张卡片继续补位（相邻链式补位），不相邻的卡片不会移动。
 */
private fun fillAdjacentGaps(
    order: List<StatsCard>,
    placed: MutableMap<StatsCard, GridRect>,
    occupied: MutableSet<Int>,
    freed: MutableSet<Int>,
) {
    var changed = true
    while (changed) {
        changed = false
        for (card in order) {
            val rect = placed[card] ?: continue
            if (rect.row <= 0) continue
            val up = rect.copy(row = rect.row - 1)
            if (!up.fitsGrid()) continue
            val upCells = up.cells()
            if (upCells.any { it in occupied }) continue
            if (upCells.none { it in freed }) continue

            val oldCells = rect.cells()
            oldCells.forEach { occupied.remove(it) }
            upCells.forEach { occupied.add(it) }
            placed[card] = up

            freed.removeAll(upCells)
            freed.addAll(oldCells.filterNot { it in upCells })
            changed = true
        }
    }
}

/**
 * 拖动排序：按拖动方向把 [card] 插到目标卡片前/后，目标及后面的卡片依次向下让位，
 * 保持原有相对顺序；被拖卡片的旧位置留空（不自动补位）。
 *
 * - 从下往上拖（旧顺序在目标之后）→ 插到目标卡片之前；
 * - 从上往下拖（旧顺序在目标之前）→ 插到目标卡片之后。
 */
fun StatsLayout.withDragged(card: StatsCard, toIndex: Int): StatsLayout {
    if (card !in order) return this
    val target = order.getOrNull(toIndex)?.takeIf { it != card } ?: return this
    val current = resolveSlots() ?: computeGridSlots(order) { sizeOf(it) }
    if (current.size != order.size) return withMoved(card, toIndex)

    val from = order.indexOf(card)
    val without = order.filter { it != card }.toMutableList()
    val targetIndexInWithout = without.indexOf(target)
    val insertIndex = if (from < toIndex) targetIndexInWithout + 1 else targetIndexInWithout
    without.add(insertIndex.coerceIn(0, without.size), card)
    val nextOrder = without

    val placed = LinkedHashMap<StatsCard, GridRect>()
    val occupied = HashSet<Int>()
    for (position in nextOrder) {
        val size = sizeOf(position)
        var rect: GridRect = if (position == card) {
            val anchor = current[target] ?: current[card] ?: return this
            GridRect(
                column = anchor.column,
                row = anchor.row.coerceAtLeast(0),
                columns = size.columns,
                rows = size.rows,
            )
        } else {
            val old = current[position] ?: continue
            GridRect(old.column, old.row, size.columns, size.rows)
        }
        rect = rect.copy(
            column = rect.column.coerceIn(
                0,
                (STATS_GRID_COLUMNS - rect.columns).coerceAtLeast(0),
            ),
        )
        if (!rect.fitsGrid()) continue
        rect = pushDown(occupied, rect)
        placed[position] = rect
        occupied.addAll(rect.cells())
    }

    val positions = LinkedHashMap<StatsCard, GridRect>()
    for (position in nextOrder) placed[position]?.let { positions[position] = it }
    return copy(order = nextOrder, positions = positions)
}

private fun isFree(rect: GridRect, others: List<GridRect>): Boolean =
    rect.fitsGrid() && others.none { it.intersects(rect) }

/**
 * 自由落点：把 [card] 放到以 ([column], [row]) 为左上角的位置（尺寸不变），其他卡片位置不动。
 * 目标越界或和别的卡片重叠时返回 null（此时退回拖动挤位逻辑）。
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

/** 把 [card] 直接放到 [rect]（其他卡片不动，允许留空格）；[rect] 非法或与别人重叠时原样返回。 */
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

private fun firstFreeRect(occupied: Set<Int>, size: CardSize): GridRect {
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
