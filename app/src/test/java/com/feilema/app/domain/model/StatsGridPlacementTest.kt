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

    private fun assertValid(slots: Map<StatsCard, GridRect>) {
        val rects = mutableListOf<GridRect>()
        slots.forEach { (card, rect) ->
            assertTrue("$card 应该能放进两列网格", rect.fitsGrid())
            rects.forEach { other ->
                assertFalse("$card 与已有卡片重叠", rect.intersects(other))
            }
            rects += rect
        }
    }

    private fun GridRect.readingRank(): Int = row * STATS_GRID_COLUMNS + column

    /**
     * 截图里的布局：周内(2×1)、时段(2×1)、趋势(2×2)、箱线图(2×2)，上下依次排开。
     */
    private fun screenshotLayout(): StatsLayout = StatsLayout(
        order = listOf(
            StatsCard.WEEKDAY_DISTRIBUTION,
            StatsCard.HOUR_DISTRIBUTION,
            StatsCard.TREND,
            StatsCard.BOX_PLOT,
        ),
        positions = mapOf(
            StatsCard.WEEKDAY_DISTRIBUTION to GridRect(0, 0, 2, 1),
            StatsCard.HOUR_DISTRIBUTION to GridRect(0, 1, 2, 1),
            StatsCard.TREND to GridRect(0, 2, 2, 2),
            StatsCard.BOX_PLOT to GridRect(0, 4, 2, 2),
        ),
    )

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
    fun dragUpInsertsBeforeTargetAndPushesFollowingCardsDownInOrder() {
        val layout = screenshotLayout()
        val moved = layout.withDragged(StatsCard.TREND, toIndex = 0)
        val slots = moved.resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)

        assertEquals(
            listOf(
                StatsCard.TREND,
                StatsCard.WEEKDAY_DISTRIBUTION,
                StatsCard.HOUR_DISTRIBUTION,
                StatsCard.BOX_PLOT,
            ),
            moved.order,
        )
        assertEquals(GridRect(0, 0, 2, 2), slots.getValue(StatsCard.TREND))
        assertEquals(
            "周内分布和时段分布应同时下移，且周内仍在时段上方",
            GridRect(0, 2, 2, 1),
            slots.getValue(StatsCard.WEEKDAY_DISTRIBUTION),
        )
        assertEquals(GridRect(0, 3, 2, 1), slots.getValue(StatsCard.HOUR_DISTRIBUTION))
        assertEquals(GridRect(0, 4, 2, 2), slots.getValue(StatsCard.BOX_PLOT))
    }

    @Test
    fun dragDownInsertsAfterTarget() {
        val layout = screenshotLayout()
        val moved = layout.withDragged(StatsCard.TREND, toIndex = 1)
        val slots = moved.resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)

        assertEquals(
            listOf(
                StatsCard.WEEKDAY_DISTRIBUTION,
                StatsCard.TREND,
                StatsCard.HOUR_DISTRIBUTION,
                StatsCard.BOX_PLOT,
            ),
            moved.order,
        )
        val weekdayRow = slots.getValue(StatsCard.WEEKDAY_DISTRIBUTION).row
        val trendRow = slots.getValue(StatsCard.TREND).row
        assertTrue("往下拖时趋势应落在周内分布下面", trendRow > weekdayRow)
        assertTrue(
            "后面卡片保持原来的先后关系",
            slots.getValue(StatsCard.HOUR_DISTRIBUTION).readingRank() >
                slots.getValue(StatsCard.TREND).readingRank(),
        )
    }

    @Test
    fun draggedCardKeepsItsOwnSizeAndOccupiesTargetSlot() {
        val layout = StatsLayout()
        val moved = layout.withDragged(StatsCard.BOX_PLOT, toIndex = 0)
        val slots = moved.resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)
        assertEquals(StatsCard.BOX_PLOT, moved.order.first())
        val box = slots.getValue(StatsCard.BOX_PLOT)
        assertEquals(2, box.columns)
        assertEquals(2, box.rows)
        assertEquals(0, box.row)
    }

    @Test
    fun draggingLeavesSourceSlotEmpty() {
        val layout = StatsLayout(order = smallOrder)
        val moved = layout.withDragged(StatsCard.PERIOD_COUNT, toIndex = 1)
        val slots = moved.resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)
        val period = slots.getValue(StatsCard.PERIOD_COUNT)
        assertTrue("被拖卡片应落在目标卡片的下方", period.readingRank() > 1)
        assertTrue(
            "旧位置应该空出来，不会被别的卡片自动补上",
            slots.values.none { GridRect(0, 0, 1, 1).intersects(it) },
        )
    }

    @Test
    fun enlargeCardPushesFollowingCardsDownOneRowKeepingOrder() {
        val layout = screenshotLayout()
        val slots = layout.withSize(StatsCard.WEEKDAY_DISTRIBUTION, CardSize.LARGE)
            .resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)

        assertEquals(GridRect(0, 0, 2, 2), slots.getValue(StatsCard.WEEKDAY_DISTRIBUTION))
        assertEquals("时段分布应整体下移一行", GridRect(0, 2, 2, 1), slots.getValue(StatsCard.HOUR_DISTRIBUTION))
        assertEquals("趋势应整体下移一行", GridRect(0, 3, 2, 2), slots.getValue(StatsCard.TREND))
        assertEquals("箱线图应整体下移一行", GridRect(0, 5, 2, 2), slots.getValue(StatsCard.BOX_PLOT))
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
    fun shrinkingDoesNotPullNonAdjacentCards() {
        val layout = StatsLayout(
            order = listOf(
                StatsCard.WEEKDAY_DISTRIBUTION,
                StatsCard.HOUR_DISTRIBUTION,
                StatsCard.TREND,
                StatsCard.BOX_PLOT,
                StatsCard.PERIOD_COUNT,
            ),
            positions = mapOf(
                StatsCard.WEEKDAY_DISTRIBUTION to GridRect(0, 0, 2, 1),
                StatsCard.HOUR_DISTRIBUTION to GridRect(0, 1, 2, 1),
                StatsCard.TREND to GridRect(0, 2, 2, 2),
                StatsCard.BOX_PLOT to GridRect(0, 4, 2, 2),
                StatsCard.PERIOD_COUNT to GridRect(0, 6, 1, 1),
            ),
        )
        val slots = layout.withSize(StatsCard.WEEKDAY_DISTRIBUTION, CardSize.SMALL)
            .resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)

        assertEquals(GridRect(0, 0, 1, 1), slots.getValue(StatsCard.WEEKDAY_DISTRIBUTION))
        assertEquals("时段分布没有被挤到，也不该上移", GridRect(0, 1, 2, 1), slots.getValue(StatsCard.HOUR_DISTRIBUTION))
        assertEquals(GridRect(0, 2, 2, 2), slots.getValue(StatsCard.TREND))
        assertEquals(GridRect(0, 4, 2, 2), slots.getValue(StatsCard.BOX_PLOT))
        assertEquals("远处的小卡片不该飞上来补位", GridRect(0, 6, 1, 1), slots.getValue(StatsCard.PERIOD_COUNT))
    }

    @Test
    fun shrinkingPullsOnlyDirectlyAdjacentCardUp() {
        val layout = StatsLayout(
            order = listOf(StatsCard.TREND, StatsCard.PERIOD_COUNT),
            positions = mapOf(
                StatsCard.TREND to GridRect(0, 0, 2, 2),
                StatsCard.PERIOD_COUNT to GridRect(0, 2, 1, 1),
            ),
        )
        val slots = layout.withSize(StatsCard.TREND, CardSize.WIDE).resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)
        assertEquals(GridRect(0, 0, 2, 1), slots.getValue(StatsCard.TREND))
        assertEquals(
            "紧挨着空位的卡片应该补上来",
            GridRect(0, 1, 1, 1),
            slots.getValue(StatsCard.PERIOD_COUNT),
        )
    }

    @Test
    fun shrinkingCascadesUpThroughAdjacentCards() {
        val layout = StatsLayout(
            order = listOf(StatsCard.TREND, StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD),
            positions = mapOf(
                StatsCard.TREND to GridRect(0, 0, 2, 2),
                StatsCard.PERIOD_COUNT to GridRect(0, 2, 1, 1),
                StatsCard.LAST_RECORD to GridRect(0, 3, 1, 1),
            ),
        )
        val slots = layout.withSize(StatsCard.TREND, CardSize.WIDE).resolveSlots()
        assertNotNull(slots)
        assertValid(slots!!)
        assertEquals(GridRect(0, 1, 1, 1), slots.getValue(StatsCard.PERIOD_COUNT))
        assertEquals(GridRect(0, 2, 1, 1), slots.getValue(StatsCard.LAST_RECORD))
    }

    @Test
    fun freeDropRectCanTargetEmptyCells() {
        val gapped = StatsLayout(
            order = listOf(StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD),
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
                StatsCard.LAST_RECORD to GridRect(0, 2, 1, 1),
            ),
        )
        assertNotNull(gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 1, row = 1))
        assertNull(gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 0, row = 2))
        val clamped = gapped.freeDropRect(StatsCard.PERIOD_COUNT, column = 9, row = 3)
        assertNotNull(clamped)
        assertEquals(1, clamped!!.column)
    }

    @Test
    fun withCardPositionMovesOnlyDraggedCard() {
        val gapped = StatsLayout(
            order = listOf(StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD),
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
                StatsCard.LAST_RECORD to GridRect(0, 2, 1, 1),
            ),
        )
        val slots = gapped.withCardPosition(StatsCard.PERIOD_COUNT, GridRect(1, 1, 1, 1))
            .resolveSlots()
        assertNotNull(slots)
        assertEquals(GridRect(1, 1, 1, 1), slots!!.getValue(StatsCard.PERIOD_COUNT))
        assertEquals(GridRect(0, 2, 1, 1), slots.getValue(StatsCard.LAST_RECORD))
    }

    @Test
    fun withCardPositionRejectsOverlap() {
        val gapped = StatsLayout(
            order = listOf(StatsCard.PERIOD_COUNT, StatsCard.LAST_RECORD),
            positions = mapOf(
                StatsCard.PERIOD_COUNT to GridRect(0, 0, 1, 1),
                StatsCard.LAST_RECORD to GridRect(0, 2, 1, 1),
            ),
        )
        val slots = gapped.withCardPosition(StatsCard.PERIOD_COUNT, GridRect(0, 2, 1, 1))
            .resolveSlots()
        assertNotNull(slots)
        assertEquals(GridRect(0, 0, 1, 1), slots!!.getValue(StatsCard.PERIOD_COUNT))
    }
}
