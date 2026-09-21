package com.rork.plcpanelstudio.data

/** The three blocks of the I/O view. */
enum class IoGroup(val title: String) {
    INPUTS("Inputs"),
    OUTPUTS("Outputs"),
    MEMORY("Memory")
}

/**
 * One line of the I/O view. [value] is null until the PLC has answered, so the screen can tell
 * "off" (0) apart from "not known yet".
 */
data class IoRow(
    val address: String,
    /** The part's label, or the name given to a memory bit (may be empty). */
    val name: String,
    /** The kind of part that created this tag, or null for a memory bit added by hand. */
    val partKind: ComponentKind?,
    val group: IoGroup,
    val value: Int?,
    /** True for memory bits added by hand; those can be taken off the list again. */
    val removable: Boolean
)

/** Every tag of a panel, split into the three blocks and sorted by address. */
data class IoSections(
    val inputs: List<IoRow>,
    val outputs: List<IoRow>,
    val memory: List<IoRow>
) {
    val all: List<IoRow> get() = inputs + outputs + memory

    /** Every address that has to be read from the PLC to fill this view. */
    val addresses: List<String> get() = all.map { it.address }.distinct()

    fun rowsOf(group: IoGroup): List<IoRow> = when (group) {
        IoGroup.INPUTS -> inputs
        IoGroup.OUTPUTS -> outputs
        IoGroup.MEMORY -> memory
    }

    companion object {
        val EMPTY = IoSections(emptyList(), emptyList(), emptyList())
    }
}

/**
 * Collects the inputs, outputs and memory bits of [panel]. The block a tag goes into follows its
 * area letter (I, Q, M); a tag without a known letter falls back to its part's direction.
 * A selector with one input per position gives one row per position. Each address appears once.
 */
fun buildIoSections(panel: Panel, values: Map<String, Int>): IoSections {
    val inputs = mutableListOf<IoRow>()
    val outputs = mutableListOf<IoRow>()
    val memory = mutableListOf<IoRow>()
    val seen = HashSet<String>()

    fun add(rawAddress: String, name: String, kind: ComponentKind?, fallback: IoGroup, removable: Boolean) {
        val address = rawAddress.trim()
        if (address.isEmpty() || !seen.add(address)) return
        val group = when (TagArea.of(address)) {
            TagArea.INPUT -> IoGroup.INPUTS
            TagArea.OUTPUT -> IoGroup.OUTPUTS
            TagArea.MEMORY -> IoGroup.MEMORY
            null -> fallback
        }
        val row = IoRow(address, name, kind, group, values[address], removable)
        when (group) {
            IoGroup.INPUTS -> inputs += row
            IoGroup.OUTPUTS -> outputs += row
            IoGroup.MEMORY -> memory += row
        }
    }

    for (part in panel.components) {
        if (part.hasPositionTags) {
            for (index in 0 until part.positionCount) {
                val address = part.positionAddress(index)
                if (address.isNotBlank()) add(address, "${part.label} · P${index + 1}", part.kind, IoGroup.INPUTS, false)
            }
        } else if (part.tagAddress.isNotBlank()) {
            val fallback = if (part.direction == IoDirection.INPUT) IoGroup.INPUTS else IoGroup.OUTPUTS
            add(part.tagAddress, part.label, part.kind, fallback, false)
        }
    }
    for (watched in panel.watchedMemory) add(watched.address, watched.name, null, IoGroup.MEMORY, true)

    val order = compareBy<IoRow>(
        { parseTagAddress(it.address)?.byte ?: Int.MAX_VALUE },
        { parseTagAddress(it.address)?.bit ?: Int.MAX_VALUE },
        { it.address }
    )
    return IoSections(inputs.sortedWith(order), outputs.sortedWith(order), memory.sortedWith(order))
}

/** Result of checking a memory bit typed by the user. */
sealed interface MemoryCheck {
    /** [address] is the clean, full address to store, e.g. "M5.3". */
    data class Ok(val address: String) : MemoryCheck
    data class Error(val message: String) : MemoryCheck
}

/**
 * Checks the "byte.bit" part typed for a new memory bit (the M is added here) against [panel]:
 * it must be a valid bit and must not already be on the panel, wired to a part or watched.
 */
fun checkMemoryBit(body: String, panel: Panel): MemoryCheck {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return MemoryCheck.Error("Enter the byte and bit, for example 0.5.")
    if (!isValidTagBody(trimmed)) return MemoryCheck.Error("Use byte.bit: byte 0–255 and bit 0–7, for example 0.5.")
    val address = parseTagAddress(tagAddressOf(TagArea.MEMORY, trimmed))?.toString()
        ?: return MemoryCheck.Error("That is not a valid memory bit.")
    if (address in buildIoSections(panel, emptyMap()).addresses) {
        return MemoryCheck.Error("$address is already on this panel.")
    }
    return MemoryCheck.Ok(address)
}
