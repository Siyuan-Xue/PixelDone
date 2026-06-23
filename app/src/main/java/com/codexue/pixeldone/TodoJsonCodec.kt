package com.codexue.pixeldone

object TodoJsonCodec {
    fun encode(items: List<TodoItem>): String {
        return items.joinToString(prefix = "[", postfix = "]") { item ->
            buildString {
                append("{")
                append("\"id\":\"").append(escape(item.id)).append("\",")
                append("\"title\":\"").append(escape(item.title)).append("\",")
                append("\"priority\":\"").append(item.priority.name).append("\",")
                append("\"dueAtMillis\":").append(item.dueAtMillis).append(",")
                append("\"completed\":").append(item.completed).append(",")
                append("\"createdAtMillis\":").append(item.createdAtMillis)
                append("}")
            }
        }
    }

    fun decode(json: String): List<TodoItem> {
        return runCatching {
            JsonCursor(json).parseArray().mapNotNull { values ->
                val id = values["id"] as? String ?: return@mapNotNull null
                val title = values["title"] as? String ?: return@mapNotNull null
                val priorityName = values["priority"] as? String ?: return@mapNotNull null
                val priority = TodoPriority.entries.firstOrNull { it.name == priorityName }
                    ?: return@mapNotNull null
                val dueAtMillis = values["dueAtMillis"] as? Long ?: return@mapNotNull null
                val completed = values["completed"] as? Boolean ?: return@mapNotNull null
                val createdAtMillis = values["createdAtMillis"] as? Long ?: return@mapNotNull null

                TodoItem(
                    id = id,
                    title = title,
                    priority = priority,
                    dueAtMillis = dueAtMillis,
                    completed = completed,
                    createdAtMillis = createdAtMillis,
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
        }
    }
}

private class JsonCursor(private val source: String) {
    private var index = 0

    fun parseArray(): List<Map<String, Any>> {
        skipWhitespace()
        expect('[')
        val items = mutableListOf<Map<String, Any>>()
        skipWhitespace()
        if (consumeIf(']')) return items

        while (true) {
            items += parseObject()
            skipWhitespace()
            when {
                consumeIf(',') -> continue
                consumeIf(']') -> break
                else -> error("Expected array separator at $index")
            }
        }

        skipWhitespace()
        if (index != source.length) error("Unexpected trailing content at $index")
        return items
    }

    private fun parseObject(): Map<String, Any> {
        skipWhitespace()
        expect('{')
        val values = mutableMapOf<String, Any>()
        skipWhitespace()
        if (consumeIf('}')) return values

        while (true) {
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when {
                consumeIf(',') -> continue
                consumeIf('}') -> break
                else -> error("Expected object separator at $index")
            }
        }
        return values
    }

    private fun parseValue(): Any {
        skipWhitespace()
        return when (peek()) {
            '"' -> parseString()
            't' -> {
                expectLiteral("true")
                true
            }
            'f' -> {
                expectLiteral("false")
                false
            }
            else -> parseLong()
        }
    }

    private fun parseString(): String {
        expect('"')
        return buildString {
            while (index < source.length) {
                val char = source[index++]
                when (char) {
                    '"' -> return@buildString
                    '\\' -> append(parseEscapedChar())
                    else -> append(char)
                }
            }
            error("Unterminated string")
        }
    }

    private fun parseEscapedChar(): Char {
        if (index >= source.length) error("Unterminated escape")
        return when (val char = source[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                val end = index + 4
                if (end > source.length) error("Bad unicode escape")
                val value = source.substring(index, end).toInt(16)
                index = end
                value.toChar()
            }
            else -> error("Bad escape $char")
        }
    }

    private fun parseLong(): Long {
        val start = index
        if (peek() == '-') index++
        while (index < source.length && source[index].isDigit()) {
            index++
        }
        if (start == index || (source[start] == '-' && start + 1 == index)) {
            error("Expected number at $start")
        }
        return source.substring(start, index).toLong()
    }

    private fun expect(expected: Char) {
        skipWhitespace()
        if (index >= source.length || source[index] != expected) {
            error("Expected $expected at $index")
        }
        index++
    }

    private fun expectLiteral(literal: String) {
        if (!source.regionMatches(index, literal, 0, literal.length)) {
            error("Expected $literal at $index")
        }
        index += literal.length
    }

    private fun consumeIf(char: Char): Boolean {
        skipWhitespace()
        if (index < source.length && source[index] == char) {
            index++
            return true
        }
        return false
    }

    private fun peek(): Char {
        if (index >= source.length) error("Unexpected end of JSON")
        return source[index]
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) {
            index++
        }
    }
}
