package com.rork.plcpanelstudio.data

/**
 * The three PLC memory areas a bit-sized part can be wired to.
 *
 * A full address is the area letter followed by "byte.bit", e.g. I3.4, Q3.2, M5.4.
 */
enum class TagArea(val prefix: String, val displayName: String) {
    INPUT("I", "Input"),
    OUTPUT("Q", "Output"),
    MEMORY("M", "Memory");

    companion object {
        /** The area for a letter ('i', 'Q', 'm', ...), or null when the letter is not an area. */
        fun fromPrefix(letter: Char): TagArea? =
            entries.firstOrNull { it.prefix[0] == letter.uppercaseChar() }

        /** The area an existing address belongs to, or null when the address is empty/unknown. */
        fun of(address: String): TagArea? =
            address.trim().firstOrNull()?.let { fromPrefix(it) }
    }
}

/** A parsed bit address: area + byte + bit, e.g. M5.4. */
data class TagAddress(val area: TagArea, val byte: Int, val bit: Int) {
    /** The part the user types, without the area letter: "5.4". */
    val body: String get() = "$byte.$bit"

    override fun toString(): String = "${area.prefix}$body"
}

/** Highest byte number accepted; bits are always 0..7. */
const val MAX_TAG_BYTE = 255

/** "byte.bit" with byte 0..255 and bit 0..7. The area letter is not part of this. */
private val BODY_PATTERN = Regex("""^(\d{1,3})\.([0-7])$""")

/** True when [body] is a well-formed "byte.bit" (no area letter), e.g. "3.4". */
fun isValidTagBody(body: String): Boolean {
    val match = BODY_PATTERN.matchEntire(body.trim()) ?: return false
    return match.groupValues[1].toInt() <= MAX_TAG_BYTE
}

/** Parses a full address such as "I3.4"; returns null when it does not fit the format. */
fun parseTagAddress(text: String): TagAddress? {
    val trimmed = text.trim().uppercase()
    val area = TagArea.fromPrefix(trimmed.firstOrNull() ?: return null) ?: return null
    val match = BODY_PATTERN.matchEntire(trimmed.drop(1)) ?: return null
    val byte = match.groupValues[1].toInt()
    if (byte > MAX_TAG_BYTE) return null
    return TagAddress(area, byte, match.groupValues[2].toInt())
}

/** True when [text] is a complete, valid address such as "Q3.2". */
fun isValidTagAddress(text: String): Boolean = parseTagAddress(text) != null

/**
 * The "byte.bit" part of [address], whatever letter it happens to start with. Used to prefill
 * the address field when opening a part that was saved under a different area letter before
 * areas became fixed by part kind, so the byte and bit the user already set are not lost.
 */
fun tagBodyIgnoringArea(address: String): String {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return ""
    val rest = trimmed.drop(1)
    return if (isValidTagBody(rest)) rest else ""
}

/**
 * Keeps only what can become a "byte.bit" while the user types: digits, at most one dot,
 * at most three digits before it and one digit after it. Anything else is dropped, so the
 * field can never hold something that is not on its way to a valid address.
 */
fun filterTagBody(raw: String): String {
    var byte = ""
    var bit: String? = null
    for (ch in raw) {
        when {
            ch == '.' && bit == null && byte.isNotEmpty() -> bit = ""
            ch.isDigit() && bit == null && byte.length < 3 -> byte += ch
            ch.isDigit() && bit != null && bit.isEmpty() -> bit = ch.toString()
        }
    }
    return if (bit == null) byte else "$byte.$bit"
}

/** Builds the full address shown and stored, e.g. MEMORY + "5.4" -> "M5.4". */
fun tagAddressOf(area: TagArea, body: String): String {
    val trimmed = body.trim()
    return if (trimmed.isEmpty()) "" else "${area.prefix}$trimmed"
}

/**
 * The one and only memory area a part of this [kind] can be wired to: a button or selector is
 * always an input, a lamp is always an output. There is no other option, so the address always
 * starts with this letter and the sheet never offers a chip to change it.
 */
fun fixedAreaFor(kind: ComponentKind): TagArea = if (kind.isLamp) TagArea.OUTPUT else TagArea.INPUT

/**
 * The direction a part behaves with. It follows straight from [fixedAreaFor]: an input-area
 * part writes to the PLC, an output-area part only mirrors it. Kept as a separate name so call
 * sites read as "what does this part do" rather than "which area is it in".
 */
fun fixedDirectionFor(kind: ComponentKind): IoDirection =
    if (fixedAreaFor(kind) == TagArea.OUTPUT) IoDirection.OUTPUT else IoDirection.INPUT
