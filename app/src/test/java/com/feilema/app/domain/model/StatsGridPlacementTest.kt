package com.feilema.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsGridPlacementTest {

    private val defaultOrder = StatsCard.defaultOrder

    private fun assertValidGrid(slots: Map<StatsCard, GridRect>, order: List<StatsCard>) {
        assertEquals(order.size, slots.size)
        val occupied = HashSet<Int>()
        for (card in order) {
            val rect = requireNotNull(slots[card]) { "missing slot for $card" }
            assertTrue("$card outside grid: $rect", rect.fitsGrid())
            for (r in rect.row until rect.row + rect.rows) {
                for (c in rect.column until rect.column + rect.columns) {
                    assertTrue("cell ($c,$r) overlapped by $card", occupied.add(c * 1000 + r))
                }
            }
        }
    }

    private fun GridRect.rank(): Int = row * 10 + column

    @Test
    fun defaultLayoutIsCompactAndValid() {
        val slots = computeGridSlots(defaultOrder) { defaultCardSize(it) }
        assertValidGrid(slots, defaultOrder)
        // 默认排布从原点开始，不留顶部空格。
        assertTrue(slots.values.any { it.row == 0 && it.column == 0 })
    }

    @Test
    fun resizeKeepsAtLeastOneCellOverlap() {
        val layout = StatsLayout()
        val previous = computeGridSlots(layout.order) { layout.sizeOf(it) }
        val resized = StatsCard.HOUR_DISTRIBUTION
        val old = requireNotNull(previous[resized])
        val newSlots = placeAfterResize(
            previous = previous,
            order = layout.order,
            resized = resized,
            newSize = CardSize.LARGE,
            sizeOf = { layout.sizeOf(it) },
        )
        val updated = requireNotNull(newSlots[resized])
        assertTrue("resized card must overlap old rect: old=$old new=$updated", updated.intersects(old))
        assertValidGrid(newSlots, layout.order)
    }

    @Test
    fun shrinkingPullsFollowingCardsUp() {
        val layout = StatsLayout()
        val previous = computeGridSlots(layout.order) { layout.sizeOf(it) }
        val resized = StatsCard.TREND
        val following = StatsCard.HOUR_DISTRIBUTION
        val oldFollowing = requireNotNull(previous[following])
        val newSlots = placeAfterResize(
            previous = previous,
            order = layout.order,
            resized = resized,
            newSize = CardSize.SMALL,
            sizeOf = { layout.sizeOf(it) },
        )
        assertValidGrid(newSlots, layout.order)
        val newFollowing = requireNotNull(newSlots[following])
        assertTrue(
            "following card should move up into freed space: old=$oldFollowing new=$newFollowing",
            newFollowing.rank() < oldFollowing.rank(),
        )
    }

    @Test
    fun followingCardsFillGapsInOrder() {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.ACTIVE_DAYS, StatsCard.MAX_DAY)
        val previous = linkedMapOf(
            StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
            StatsCard.ACTIVE_DAYS to GridRect(0, 1, 1, 1),
            StatsCard.MAX_DAY to GridRect(1, 1, 1, 1),
        )
        val slots = placeAfterResize(
            previous = previous,
            order = order,
            resized = StatsCard.PERIOD_COUNT,
            newSize = CardSize.SMALL,
            sizeOf = { CardSize.SMALL },
        )
        assertValidGrid(slots, order)
        // (1,0) 的空格会被后面的卡片顺次补上：B 上移到 (1,0)，C 上移到 (0,1)。
        assertEquals(1, requireNotNull(slots[StatsCard.ACTIVE_DAYS]).column)
        assertEquals(0, requireNotNull(slots[StatsCard.ACTIVE_DAYS]).row)
        assertEquals(0, requireNotNull(slots[StatsCard.MAX_DAY]).column)
        assertEquals(1, requireNotNull(slots[StatsCard.MAX_DAY]).row)
        // 顺序保持不变。
        assertTrue(
            requireNotNull(slots[StatsCard.ACTIVE_DAYS]).rank() < requireNotNull(slots[StatsCard.MAX_DAY]).rank(),
        )
    }

    @Test
    fun gapRemainsWhenFollowingCardCannotFit() {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.HOUR_DISTRIBUTION)
        val previous = linkedMapOf(
            StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
            StatsCard.HOUR_DISTRIBUTION to GridRect(0, 1, 2, 1),
        )
        val slots = placeAfterResize(
            previous = previous,
            order = order,
            resized = StatsCard.PERIOD_COUNT,
            newSize = CardSize.SMALL,
            sizeOf = { if (it == StatsCard.HOUR_DISTRIBUTION) CardSize.WIDE else CardSize.SMALL },
        )
        assertValidGrid(slots, order)
        val occupied = slots.values.flatMap { rect ->
            (rect.row until rect.row + rect.rows).flatMap { r ->
                (rect.column until rect.column + rect.columns).map { c -> c to r }
            }
        }.toSet()
        // 宽卡放不进 (1,0) 这一格，允许它留空。
        assertTrue((1 to 0) !in occupied)
    }

    @Test
    fun displacedCardMovesDownWhenNoRoomAbove() {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.ACTIVE_DAYS)
        val previous = linkedMapOf(
            StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
            StatsCard.ACTIVE_DAYS to GridRect(1, 0, 1, 1),
        )
        val slots = placeAfterResize(
            previous = previous,
            order = order,
            resized = StatsCard.PERIOD_COUNT,
            newSize = CardSize.LARGE,
            sizeOf = { if (it == StatsCard.PERIOD_COUNT) CardSize.LARGE else CardSize.SMALL },
        )
        assertValidGrid(slots, order)
        assertEquals(2, requireNotNull(slots[StatsCard.ACTIVE_DAYS]).row)
        assertEquals(0, requireNotNull(slots[StatsCard.ACTIVE_DAYS]).column)
    }

    @Test
    fun resolveSlotsUsesSavedPositionsWhenValid() {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.ACTIVE_DAYS)
        val layout = StatsLayout(
            order = order,
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(1, 0, 0, 0),
                StatsCard.ACTIVE_DAYS to GridRect(0, 3, 0, 0),
            ),
        )
        val slots = layout.resolveSlots()
        assertNotNull(slots)
        assertEquals(1, slots!![StatsCard.PERIOD_COUNT]?.column)
        assertEquals(3, slots[StatsCard.ACTIVE_DAYS]?.row)
    }

    @Test
    fun resolveSlotsReturnsNullOnOverlap() {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.ACTIVE_DAYS)
        val layout = StatsLayout(
            order = order,
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(0, 0, 0, 0),
                StatsCard.ACTIVE_DAYS to GridRect(0, 0, 0, 0),
            ),
        )
        assertNull(layout.resolveSlots())
    }

    private fun gappedLayout(): StatsLayout {
        val order = listOf(StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD)
        return StatsLayout(
            order = order,
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(0, 0, 0, 0),
                StatsCard.LAST_RECORD to GridRect(0, 2, 0, 0),
            ),
        )
    }

    @Test
    fun freeDropRectCanTargetEmptyCells() {
        val layout = gappedLayout()
        // (1,1) 是空出来的格子，可以把卡片拖到这里。
        val rect = layout.freeDropRect(StatsCard.PERIOD_COUNT, 1, 1)
        assertNotNull(rect)
        assertEquals(1, rect!!.column)
        assertEquals(1, rect.row)
        // 已有卡片的位置不能作为自由落点。
        assertNull(layout.freeDropRect(StatsCard.PERIOD_COUNT, 0, 2))
    }

    @Test
    fun withCardPositionMovesOnlyDraggedCard() {
        val layout = gappedLayout()
        val moved = layout.withCardPosition(StatsCard.PERIOD_COUNT, GridRect(1, 1, 1, 1))
        val slots = requireNotNull(moved.resolveSlots())
        assertEquals(1, requireNotNull(slots[StatsCard.PERIOD_COUNT]).column)
        assertEquals(1, requireNotNull(slots[StatsCard.PERIOD_COUNT]).row)
        // 其他卡片保持原位，原来的格子空出来。
        assertEquals(0, requireNotNull(slots[StatsCard.LAST_RECORD]).column)
        assertEquals(2, requireNotNull(slots[StatsCard.LAST_RECORD]).row)
        assertTrue(slots.values.none { it.row == 0 && it.column == 0 })
    }

    @Test
    fun withCardPositionRejectsOverlap() {
        val layout = gappedLayout()
        val result = layout.withCardPosition(StatsCard.LAST_RECORD, GridRect(0, 0, 1, 1))
        assertEquals(layout.positions, result.positions)
    }
}
