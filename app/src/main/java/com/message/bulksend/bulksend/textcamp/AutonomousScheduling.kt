package com.message.bulksend.bulksend.textcamp

import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.db.AutonomousSendQueueEntity
import kotlin.math.ceil

private const val SAFE_DAILY_LIMIT = 24
private const val HOURLY_MIN_LIMIT = 5
private const val HOURLY_MAX_LIMIT = 7
private const val SAFE_START_HOUR = 8
private const val SAFE_END_HOUR = 22
private const val LAUNCH_BURST_COUNT = 3
private const val LAUNCH_BURST_GAP_MS = 45_000L
private const val LAUNCH_BURST_LEAD_MS = 3_000L
private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

data class AutonomousExecutionStats(
    val sentToday: Int = 0,
    val queuedToday: Int = 0,
    val queued: Int = 0,
    val failed: Int = 0,
    val remainingDays: Int = 0,
    val nextSendTimeMillis: Long? = null,
    val autoPauseReason: String? = null
)

fun recommendedAutonomousDays(totalContacts: Int): Int {
    if (totalContacts <= 0) return 1
    return ceil(totalContacts / SAFE_DAILY_LIMIT.toDouble()).toInt().coerceAtLeast(1)
}

fun buildAutonomousQueuePlan(
    campaignId: String,
    contacts: List<Contact>,
    selectedDays: Int,
    startTimeMillis: Long = System.currentTimeMillis(),
    random: kotlin.random.Random = kotlin.random.Random(contacts.size * 31 + selectedDays * 17)
): List<AutonomousSendQueueEntity> {
    if (contacts.isEmpty()) return emptyList()

    val sortedContacts = contacts.shuffled(random)
    val launchBurstContacts = sortedContacts.take(LAUNCH_BURST_COUNT)
    val remainingContacts = sortedContacts.drop(launchBurstContacts.size)
    val result = mutableListOf<AutonomousSendQueueEntity>()
    launchBurstContacts.forEachIndexed { index, contact ->
        val plannedMillis = startTimeMillis + LAUNCH_BURST_LEAD_MS + (index * LAUNCH_BURST_GAP_MS)
        val hourOfDay = java.util.Calendar.getInstance().apply {
            timeInMillis = plannedMillis
        }.get(java.util.Calendar.HOUR_OF_DAY)
        result += AutonomousSendQueueEntity(
            campaignId = campaignId,
            contactNumber = contact.number,
            contactName = contact.name,
            plannedTimeMillis = plannedMillis,
            dayIndex = 0,
            hourOfDay = hourOfDay,
            status = "queued",
            retryCount = 0,
            lastError = null,
            sentTimeMillis = null
        )
    }

    if (remainingContacts.isEmpty()) {
        return result.sortedBy { it.plannedTimeMillis }
    }

    val effectiveDays = maxOf(
        selectedDays.coerceAtLeast(1),
        recommendedAutonomousDays(remainingContacts.size)
    )
    val scheduleStartMillis = startTimeMillis + LAUNCH_BURST_LEAD_MS +
        (launchBurstContacts.size * LAUNCH_BURST_GAP_MS)
    val dayWindows = buildSchedulingDayWindows(
        scheduleStartMillis = scheduleStartMillis,
        requestedDays = effectiveDays,
        remainingMessages = remainingContacts.size,
        launchBurstCount = launchBurstContacts.size
    )
    val dayTargets = allocateMessagesAcrossDayWindows(
        total = remainingContacts.size,
        dayWindows = dayWindows,
        random = random
    )
    var contactIndex = 0

    dayTargets.forEachIndexed { dayIndex, messagesForDay ->
        if (messagesForDay <= 0) return@forEachIndexed
        val dayWindow = dayWindows[dayIndex]
        val hourlyTargets = allocateMessagesPerHour(
            messagesForDay = messagesForDay,
            availableHours = dayWindow.availableHours,
            random = random,
            preferEarlyHours = dayIndex == 0
        )
        val dayStart = dayWindow.dayStartMillis

        hourlyTargets.forEach { (hourWindow, messagesInHour) ->
            val minuteSlots = buildMinuteSlots(
                count = messagesInHour,
                minuteStart = hourWindow.minuteStart,
                minuteEnd = hourWindow.minuteEnd,
                random = random
            )
            minuteSlots.forEach { minute ->
                val contact = remainingContacts.getOrNull(contactIndex) ?: return@forEach
                val plannedMillis = dayStart +
                    hourWindow.hourOfDay * 60L * 60L * 1000L +
                    minute * 60L * 1000L +
                    random.nextLong(5_000L, 45_000L)
                result += AutonomousSendQueueEntity(
                    campaignId = campaignId,
                    contactNumber = contact.number,
                    contactName = contact.name,
                    plannedTimeMillis = plannedMillis,
                    dayIndex = dayWindow.dayIndex,
                    hourOfDay = hourWindow.hourOfDay,
                    status = "queued",
                    retryCount = 0,
                    lastError = null,
                    sentTimeMillis = null
                )
                contactIndex++
            }
        }
    }

    return result.sortedBy { it.plannedTimeMillis }
}

fun computeNextRetryPlan(
    nowMillis: Long,
    random: kotlin.random.Random = kotlin.random.Random(nowMillis)
): Pair<Long, Int> {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
    cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
    val nextHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    cal.set(java.util.Calendar.MINUTE, random.nextInt(5, 50))
    cal.set(java.util.Calendar.SECOND, random.nextInt(5, 55))
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis to nextHour
}

fun startAndEndOfToday(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val start = cal.timeInMillis
    cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
    cal.add(java.util.Calendar.MILLISECOND, -1)
    return start to cal.timeInMillis
}

private fun allocateMessagesPerDay(
    total: Int,
    days: Int,
    random: kotlin.random.Random
): List<Int> {
    if (days <= 1) return listOf(total.coerceAtMost(SAFE_DAILY_LIMIT))

    val allocations = mutableListOf<Int>()
    var remaining = total
    var daysLeft = days
    while (daysLeft > 0) {
        val minFuture = maxOf(0, (daysLeft - 1))
        val maxForCurrent = minOf(SAFE_DAILY_LIMIT, remaining - minFuture)
        val avg = ceil(remaining / daysLeft.toDouble()).toInt()
        val candidate = (avg + random.nextInt(-2, 3)).coerceIn(1, maxForCurrent)
        allocations += candidate
        remaining -= candidate
        daysLeft--
    }

    var pointer = 0
    while (remaining > 0) {
        val room = SAFE_DAILY_LIMIT - allocations[pointer]
        if (room > 0) {
            allocations[pointer] += 1
            remaining--
        }
        pointer = (pointer + 1) % allocations.size
    }

    return allocations
}

private fun allocateMessagesPerHour(
    messagesForDay: Int,
    availableHours: List<SchedulingHourWindow>,
    random: kotlin.random.Random,
    preferEarlyHours: Boolean = false
): List<Pair<SchedulingHourWindow, Int>> {
    if (messagesForDay <= 0 || availableHours.isEmpty()) return emptyList()

    val maxCapacity = availableHours.sumOf { it.capacity }
    val boundedMessages = messagesForDay.coerceAtMost(maxCapacity)
    val selectedHours = selectHourWindows(
        hourWindows = availableHours,
        requiredMessages = boundedMessages,
        random = random,
        preferEarlyHours = preferEarlyHours
    )

    var remaining = boundedMessages
    val hourlyTargets = mutableListOf<Pair<SchedulingHourWindow, Int>>()
    selectedHours.forEachIndexed { index, hourWindow ->
        val leftSlots = selectedHours.size - index
        val futureCapacity = selectedHours.drop(index + 1).sumOf { it.capacity }
        val minCurrent = maxOf(1, remaining - futureCapacity)
        val maxCurrent = minOf(hourWindow.capacity, remaining - (leftSlots - 1))
        val preferredMin = minOf(HOURLY_MIN_LIMIT, maxCurrent).coerceAtLeast(minCurrent)
        val current = when {
            leftSlots == 1 -> remaining
            preferEarlyHours -> maxCurrent
            else -> random.nextInt(preferredMin, maxCurrent + 1)
        }
        hourlyTargets += hourWindow to current
        remaining -= current
    }
    return hourlyTargets
}

private fun buildMinuteSlots(
    count: Int,
    minuteStart: Int,
    minuteEnd: Int,
    random: kotlin.random.Random
): List<Int> {
    if (count <= 0) return emptyList()
    if (minuteStart > minuteEnd) return emptyList()
    if (count == 1) return listOf(random.nextInt(minuteStart, minuteEnd + 1))

    val span = minuteEnd - minuteStart + 1
    val slots = (1..count).map { index ->
        val base = minuteStart + ((index * span.toDouble()) / (count + 1)).toInt()
        (base + random.nextInt(-3, 4)).coerceIn(minuteStart, minuteEnd)
    }.distinct().toMutableList()

    if (slots.size < count) {
        slots += (minuteStart..minuteEnd)
            .filterNot { it in slots }
            .shuffled(random)
            .take(count - slots.size)
    }

    return slots.sorted()
}

private data class SchedulingDayWindow(
    val dayIndex: Int,
    val dayStartMillis: Long,
    val availableHours: List<SchedulingHourWindow>,
    val capacity: Int
)

private data class SchedulingHourWindow(
    val hourOfDay: Int,
    val minuteStart: Int = 1,
    val minuteEnd: Int = 58,
    val capacity: Int = HOURLY_MAX_LIMIT
)

private fun buildSchedulingDayWindows(
    scheduleStartMillis: Long,
    requestedDays: Int,
    remainingMessages: Int,
    launchBurstCount: Int
): List<SchedulingDayWindow> {
    val windows = mutableListOf<SchedulingDayWindow>()
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = scheduleStartMillis
    }
    val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = calendar.get(java.util.Calendar.MINUTE)
    val sameDayHours = buildSameDayHourWindows(
        currentHour = currentHour,
        currentMinute = currentMinute,
        launchBurstCount = launchBurstCount
    )
    val startOffsetDays = if (sameDayHours.isEmpty()) 1 else 0
    val firstDayMidnight = midnightFor(scheduleStartMillis) + (startOffsetDays * MILLIS_PER_DAY)

    repeat(requestedDays) { index ->
        val hours = if (index == 0 && sameDayHours.isNotEmpty()) {
            sameDayHours
        } else {
            fullSafeHourWindows()
        }
        val safeDailyCapacity = if (index == 0 && sameDayHours.isNotEmpty()) {
            (SAFE_DAILY_LIMIT - launchBurstCount).coerceAtLeast(0)
        } else {
            SAFE_DAILY_LIMIT
        }
        windows += SchedulingDayWindow(
            dayIndex = index,
            dayStartMillis = firstDayMidnight + (index * MILLIS_PER_DAY),
            availableHours = hours,
            capacity = minOf(hours.sumOf { it.capacity }, safeDailyCapacity)
        )
    }

    var totalCapacity = windows.sumOf { it.capacity }
    var nextIndex = windows.size
    while (totalCapacity < remainingMessages) {
        windows += SchedulingDayWindow(
            dayIndex = nextIndex,
            dayStartMillis = firstDayMidnight + (nextIndex * MILLIS_PER_DAY),
            availableHours = fullSafeHourWindows(),
            capacity = SAFE_DAILY_LIMIT
        )
        totalCapacity += SAFE_DAILY_LIMIT
        nextIndex++
    }

    return windows
}

private fun allocateMessagesAcrossDayWindows(
    total: Int,
    dayWindows: List<SchedulingDayWindow>,
    random: kotlin.random.Random
): List<Int> {
    if (total <= 0 || dayWindows.isEmpty()) return List(dayWindows.size) { 0 }

    val capacities = dayWindows.map { it.capacity }
    val allocations = MutableList(dayWindows.size) { 0 }
    var remaining = total
    val guaranteedFirstDay = guaranteedFirstDayAllocation(total, dayWindows.firstOrNull())

    capacities.forEachIndexed { index, capacity ->
        if (remaining <= 0) return@forEachIndexed
        val futureCapacity = capacities.drop(index + 1).sum()
        val minNeededHere = (remaining - futureCapacity).coerceAtLeast(0)
        val dayFloor = if (index == 0) {
            minOf(remaining, maxOf(minNeededHere, guaranteedFirstDay))
        } else {
            minNeededHere
        }
        val daysLeft = capacities.size - index
        val averageTarget = ceil(remaining / daysLeft.toDouble()).toInt()
        val desired = (averageTarget + random.nextInt(-2, 3))
            .coerceAtLeast(dayFloor)
            .coerceAtMost(capacity)
            .coerceAtMost(remaining)
        allocations[index] = desired
        remaining -= desired
    }

    var pointer = 0
    while (remaining > 0) {
        val room = capacities[pointer] - allocations[pointer]
        if (room > 0) {
            allocations[pointer] += 1
            remaining--
        }
        pointer = (pointer + 1) % allocations.size
    }

    return allocations
}

private fun guaranteedFirstDayAllocation(
    total: Int,
    firstWindow: SchedulingDayWindow?
): Int {
    if (firstWindow == null || firstWindow.capacity <= 0) return 0

    return when {
        total >= SAFE_DAILY_LIMIT -> minOf(firstWindow.capacity, minOf(total, HOURLY_MIN_LIMIT * 2))
        total >= HOURLY_MAX_LIMIT * 2 -> minOf(firstWindow.capacity, minOf(total, HOURLY_MIN_LIMIT))
        total >= HOURLY_MIN_LIMIT -> minOf(firstWindow.capacity, minOf(total, 2))
        else -> 0
    }
}

private fun midnightFor(timeMillis: Long): Long {
    return java.util.Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun buildSameDayHourWindows(
    currentHour: Int,
    currentMinute: Int,
    launchBurstCount: Int
): List<SchedulingHourWindow> {
    return when {
        currentHour < SAFE_START_HOUR -> fullSafeHourWindows()
        currentHour > SAFE_END_HOUR -> emptyList()
        else -> {
            val windows = mutableListOf<SchedulingHourWindow>()
            val currentHourMinuteStart = currentMinute + 1
            val availableMinuteCount = if (currentHourMinuteStart <= 58) {
                58 - currentHourMinuteStart + 1
            } else {
                0
            }
            val currentHourCapacity = minOf(
                (HOURLY_MAX_LIMIT - launchBurstCount).coerceAtLeast(0),
                availableMinuteCount
            )
            if (currentHourCapacity > 0 && currentHourMinuteStart <= 58) {
                windows += SchedulingHourWindow(
                    hourOfDay = currentHour,
                    minuteStart = currentHourMinuteStart,
                    minuteEnd = 58,
                    capacity = currentHourCapacity
                )
            }
            ((currentHour + 1)..SAFE_END_HOUR).forEach { hour ->
                windows += SchedulingHourWindow(hourOfDay = hour)
            }
            windows
        }
    }
}

private fun fullSafeHourWindows(): List<SchedulingHourWindow> {
    return (SAFE_START_HOUR..SAFE_END_HOUR).map { hour ->
        SchedulingHourWindow(hourOfDay = hour)
    }
}

private fun selectHourWindows(
    hourWindows: List<SchedulingHourWindow>,
    requiredMessages: Int,
    random: kotlin.random.Random,
    preferEarlyHours: Boolean
): List<SchedulingHourWindow> {
    if (hourWindows.isEmpty() || requiredMessages <= 0) return emptyList()

    if (preferEarlyHours) {
        val selected = mutableListOf<SchedulingHourWindow>()
        var coveredCapacity = 0
        hourWindows.forEach { hourWindow ->
            if (coveredCapacity >= requiredMessages) return@forEach
            selected += hourWindow
            coveredCapacity += hourWindow.capacity
        }
        return selected
    }

    val selected = mutableListOf<SchedulingHourWindow>()
    var coveredCapacity = 0
    hourWindows.shuffled(random).forEach { hourWindow ->
        if (coveredCapacity >= requiredMessages && selected.isNotEmpty()) return@forEach
        selected += hourWindow
        coveredCapacity += hourWindow.capacity
    }
    return selected.sortedBy { it.hourOfDay }
}
