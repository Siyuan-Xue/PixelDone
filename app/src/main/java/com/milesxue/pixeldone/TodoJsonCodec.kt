package com.milesxue.pixeldone

object TodoJsonCodec {
    fun encode(items: List<TodoItem>): String {
        return items.joinToString(prefix = "[", postfix = "]", transform = ::encodeTodoItem)
    }

    fun decode(json: String): List<TodoItem> {
        return runCatching {
            JsonCursor(json).parseArray().mapNotNull { value ->
                todoFrom(value as? Map<*, *> ?: return@mapNotNull null)
            }
        }.getOrDefault(emptyList())
    }

    fun encodeState(state: TodoChecklistState): String {
        return buildString {
            append("{")
            append("\"selectedListId\":\"").append(escape(state.selectedListId)).append("\",")
            append("\"lists\":")
            append(
                state.lists.joinToString(prefix = "[", postfix = "]") { checklist ->
                    buildString {
                        append("{")
                        append("\"id\":\"").append(escape(checklist.id)).append("\",")
                        append("\"name\":\"").append(escape(checklist.name)).append("\",")
                        append("\"createdAtMillis\":").append(checklist.createdAtMillis).append(",")
                        append("\"items\":").append(encode(checklist.items))
                        append("}")
                    }
                },
            )
            append("}")
        }
    }

    fun decodeState(json: String, fallbackCreatedAtMillis: Long): TodoChecklistState? {
        return runCatching {
            val values = JsonCursor(json).parseObject()
            val selectedListId = values["selectedListId"] as? String ?: return null
            val rawLists = values["lists"] as? List<*> ?: return null
            val lists = rawLists.mapNotNull { rawList ->
                val listValues = rawList as? Map<*, *> ?: return@mapNotNull null
                val id = listValues["id"] as? String ?: return@mapNotNull null
                val name = listValues["name"] as? String ?: return@mapNotNull null
                val createdAtMillis =
                    listValues["createdAtMillis"] as? Long ?: return@mapNotNull null
                val rawItems = listValues["items"] as? List<*> ?: return@mapNotNull null
                val items = rawItems.mapNotNull { rawItem ->
                    todoFrom(rawItem as? Map<*, *> ?: return@mapNotNull null)
                }

                TodoChecklist(
                    id = id,
                    name = name,
                    items = items,
                    createdAtMillis = createdAtMillis,
                )
            }

            normalizeChecklistState(
                state = TodoChecklistState(
                    lists = lists,
                    selectedListId = selectedListId,
                ),
                fallbackCreatedAtMillis = fallbackCreatedAtMillis,
            )
        }.getOrNull()
    }

    private fun encodeTodoItem(item: TodoItem): String {
        return buildString {
            append("{")
            append("\"id\":\"").append(escape(item.id)).append("\",")
            append("\"title\":\"").append(escape(item.title)).append("\",")
            append("\"priority\":\"").append(item.priority.name).append("\",")
            append("\"dueAtMillis\":").append(item.dueAtMillis).append(",")
            append("\"reminderRepeat\":\"").append(item.reminderRepeat.name).append("\",")
            append("\"completed\":").append(item.completed).append(",")
            append("\"createdAtMillis\":").append(item.createdAtMillis)
            append("}")
        }
    }

    private fun todoFrom(values: Map<*, *>): TodoItem? {
        val id = values["id"] as? String ?: return null
        val title = values["title"] as? String ?: return null
        val priorityName = values["priority"] as? String ?: return null
        val priority = TodoPriority.entries.firstOrNull { it.name == priorityName } ?: return null
        val dueAtMillis = values["dueAtMillis"] as? Long ?: return null
        val repeatName = values["reminderRepeat"] as? String
        val reminderRepeat = repeatName
            ?.let { name -> ReminderRepeat.entries.firstOrNull { it.name == name } }
            ?: ReminderRepeat.NONE
        val completed = values["completed"] as? Boolean ?: return null
        val createdAtMillis = values["createdAtMillis"] as? Long ?: return null

        return TodoItem(
            id = id,
            title = title,
            priority = priority,
            dueAtMillis = dueAtMillis,
            completed = completed,
            createdAtMillis = createdAtMillis,
            reminderRepeat = reminderRepeat,
        )
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

    fun parseArray(): List<Any> {
        skipWhitespace()
        val items = parseArrayValue()
        skipWhitespace()
        if (index != source.length) error("Unexpected trailing content at $index")
        return items
    }

    fun parseObject(): Map<String, Any> {
        skipWhitespace()
        val values = parseObjectValue()
        skipWhitespace()
        if (index != source.length) error("Unexpected trailing content at $index")
        return values
    }

    private fun parseArrayValue(): List<Any> {
        expect('[')
        val items = mutableListOf<Any>()
        skipWhitespace()
        if (consumeIf(']')) return items

        while (true) {
            items += parseValue()
            skipWhitespace()
            when {
                consumeIf(',') -> continue
                consumeIf(']') -> break
                else -> error("Expected array separator at $index")
            }
        }
        return items
    }

    private fun parseObjectValue(): Map<String, Any> {
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
            '[' -> parseArrayValue()
            '{' -> parseObjectValue()
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
