package com.feilema.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsGridPlacementTest {

    private val smallOrder = listOf(
        StatsCard.PERIOD_COUNT,
        StatsCard.ACTIVE_DAYS,
        StatsCard.MAX_DAY,
        StatsCard.DAILY_AVERAGE,
        StatsCard.LAST_RECORD,
        StatsCard.BEST_HOUR,
    )

    private val smallSizeOf: (StatsCard) -> CardSize = { CardSize.SMALL }

    private fun layoutWith(
        order: List<StatsCard> = smallOrder,
        positions: Map<StatsCard, GridRect> = emptyMap(),
    ): StatsLayout = StatsLayout(order = order, positions = positions)

    private fun gappedLayout(): StatsLayout = layoutWith(
        order = listOf(StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD),
        positions = mapOf(
            StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
            StatsCard.LAST_RECORD to GridRect(0, 2, 1, 1),
        ),
    )

    private fun assertValid(slots: Map<StatsCard, GridRect>) {
        val rects = mutableListOf<GridRect>()
        slots.forEach { (card, rect) ->
            assertTrue("$card 应该能放进两列网格", rect.fitsGrid())
            assertTrue("$card 列数至少 1", rect.columns >= 1)
            assertTrue("$card 行数至少 1", rect.rows >= 1)
            rects.forEach { other ->
                assertFalse("$card 与已有卡片重叠", rect.intersects(other))
            }
            rects += rect
        }
    }

    private fun GridRect.readingRank(): Int = row * STATS_GRID_COLUMNS + column

    @Test
    fun cardSizeNoLongerOffersTall() {
        assertEquals(listOf("1×1", "2×1", "2×2"), CardSize.entries.map { it.label })
        assertTrue(CardSize.entries.none { it.name == "TALL" })
    }

    @Test
    fun defaultLayoutIsCompactAndValid() {
        val layout = StatsLayout()
        val slots = computeGridSlots(layout.order) { layout.sizeOf(it) }
        assertValid(slots)
        assertEquals(GridRect(0, 0, 2, 2), slots[StatsCard.TREND])
        assertEquals(GridRect(0, 2, 2, 1), slots[StatsCard.HOUR_DISTRIBUTION])
        assertEquals(GridRect(0, 3, 2, 1), slots[StatsCard.WEEKDAY_DISTRIBUTION])
        assertEquals(GridRect(0, 4, 2, 2), slots[StatsCard.BOX_PLOT])
    }

    @Test
    fun resizeKeepsAtLeastOneCellOverlap() {
        val previous = computeGridSlots(StatsCard.defaultOrder) { defaultCardSize(it) }
        val resized = placeAfterResize(
            previous = previous,
            order = StatsCard.defaultOrder,
            resized = StatsCard.HOUR_DISTRIBUTION,
            newSize = CardSize.LARGE,
            sizeOf = { defaultCardSize(it) },
        )
        assertValid(resized)
        val oldRect = previous.getValue(StatsCard.HOUR_DISTRIBUTION)
        assertTrue(
            "放大后应与旧位置至少重叠一格",
            resized.getValue(StatsCard.HOUR_DISTRIBUTION).intersects(oldRect),
        )
    }

    @Test
    fun resizePushesDisplacedCardForward() {
        val previous = computeGridSlots(smallOrder, smallSizeOf)
        val resized = placeAfterResize(
            previous = previous,
            order = smallOrder,
            resized = StatsCard.PERIOD_COUNT,
            newSize = CardSize.WIDE,
            sizeOf = smallSizeOf,
        )
        assertValid(resized)
        assertEquals(GridRect(0, 0, 2, 1), resized[StatsCard.PERIOD_COUNT])
        assertTrue(
            "被挤开的卡片应往阅读方向后移",
            resized.getValue(StatsCard.ACTIVE_DAYS).readingRank() >
                previous.getValue(StatsCard.ACTIVE_DAYS).readingRank(),
        )
    }

    @Test
    fun cardsNotHitByResizeStayInPlace() {
        val previous = computeGridSlots(smallOrder, smallSizeOf)
        val resized = placeAfterResize(
            previous = previous,
            order = smallOrder,
            resized = StatsCard.PERIOD_COUNT,
            newSize = CardSize.WIDE,
            sizeOf = smallSizeOf,
        )
        assertValid(resized)
        listOf(StatsCard.MAX_DAY, StatsCard.DAILY_AVERAGE, StatsCard.LAST_RECORD, StatsCard.BEST_HOUR)
            .forEach { card ->
                assertEquals("$card 没被挤到，应该原地不动", previous[card], resized[card])
            }
    }

    @Test
    fun shrinkingCardPullsFollowingCardsUp() {
        val previous = computeGridSlots(StatsCard.defaultOrder) { defaultCardSize(it) }
        val resized = placeAfterResize(
            previous = previous,
            order = StatsCard.defaultOrder,
            resized = StatsCard.TREND,
            newSize = CardSize.SMALL,
            sizeOf = { defaultCardSize(it) },
        )
        assertValid(resized)
        val oldRank = previous.getValue(StatsCard.HOUR_DISTRIBUTION).readingRank()
        assertTrue(
            "缩小后后面的卡片应尽量顺次上移",
            resized.getValue(StatsCard.HOUR_DISTRIBUTION).readingRank() < oldRank,
        )
    }

    @Test
    fun freeDropRectCanTargetEmptyCells() {
        val gapped = gappedLayout()
        assertNotNull(gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 1, row = 1))
        assertNull(gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 0, row = 2))
        val clamped = gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 9, row = 3)
        assertNotNull(clamped)
        assertEquals(1, clamped!!.column)
    }

    @Test
    fun withCardPositionMovesOnlyDraggedCard() {
        val gapped = gappedLayout()
        val moved = gapped.withCardPosition(StatsCard.PERIOD_COUNT, GridRect(1, 1, 1, 1))
        val resolved = moved.resolveSlots()
        assertNotNull(resolved)
        val slots = resolved!!
        assertEquals(GridRect(1, 1, 1, 1), slots[StatsCard.PERIOD_COUNT])
        assertEquals(GridRect(0, 2, 1, 1), slots[StatsCard.LAST_RECORD])
    }

    @Test
    fun withCardPositionRejectsOverlap() {
        val gapped = gappedLayout()
        val moved = gapped.withCardPosition(StatsCard.PERIOD_COUNT, GridRect(0, 2, 1, 1))
        val resolved = moved.resolveSlots()
        assertNotNull(resolved)
        val slots = resolved!!
        assertEquals(GridRect(0, 0, 1, 1), slots[StatsCard.PERIOD_COUNT])
    }

    @Test
    fun withDraggedPushesTargetCardsForward() {
        val layout = layoutWith()
        val moved = layout.withDragged(StatsCard.PERIOD_COUNT, toIndex = 1)
        val resolved = moved.resolveSlots()
        assertNotNull(resolved)
        val slots = resolved!!
        assertValid(slots)
        assertEquals("被拖卡片应占据目标卡片的位置", 1, slots.getValue(StatsCard.PERIOD_COUNT).column)
        assertEquals(0, slots.getValue(StatsCard.PERIOD_COUNT).row)
        assertTrue("目标卡片被往后挤", slots.getValue(StatsCard.ACTIVE_DAYS).readingRank() > 1)
    }

    @Test
    fun withDraggedKeepsOthersInPlaceAndLeavesGap() {
        val layout = layoutWith()
        val moved = layout.withDragged(StatsCard.PERIOD_COUNT, toIndex = 1)
        val resolved = moved.resolveSlots()
        assertNotNull(resolved)
        val slots = resolved!!
        assertEquals(GridRect(0, 1, 1, 1), slots[StatsCard.MAX_DAY])
        assertTrue(
            "原来被拖卡片的位置应该空出来",
            slots.values.none { GridRect(0, 0, 1, 1).intersects(it) },
        )
    }

    @Test
    fun draggedCardKeepsItsOwnSize() {
        val layout = StatsLayout()
        val moved = layout.withDragged(StatsCard.BOX_PLOT, toIndex = 0)
        val resolved = moved.resolveSlots()
        assertNotNull(resolved)
        val slots = resolved!!
        assertValid(slots)
        val box = slots.getValue(StatsCard.BOX_PLOT)
        assertEquals(2, box.columns)
        assertEquals(2, box.rows)
    }
}
