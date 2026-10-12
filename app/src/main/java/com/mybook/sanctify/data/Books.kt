package com.mybook.sanctify.data

/** Нумерация 1..66 как в большинстве API (порядок протестантского канона). */
object Books {
    const val COUNT = 66
    const val NT_START = 40

    private val data = listOf(
        "Бытие" to "Быт", "Исход" to "Исх", "Левит" to "Лев", "Числа" to "Чис",
        "Второзаконие" to "Втор", "Иисус Навин" to "Нав", "Судей" to "Суд", "Руфь" to "Руф",
        "1 Царств" to "1Цар", "2 Царств" to "2Цар", "3 Царств" to "3Цар", "4 Царств" to "4Цар",
        "1 Паралипоменон" to "1Пар", "2 Паралипоменон" to "2Пар", "Ездра" to "Езд",
        "Неемия" to "Неем", "Есфирь" to "Есф", "Иов" to "Иов", "Псалтирь" to "Пс",
        "Притчи" to "Прит", "Екклесиаст" to "Еккл", "Песнь Песней" to "Песн", "Исаия" to "Ис",
        "Иеремия" to "Иер", "Плач Иеремии" to "Плач", "Иезекииль" to "Иез", "Даниил" to "Дан",
        "Осия" to "Ос", "Иоиль" to "Иоил", "Амос" to "Ам", "Авдий" to "Авд", "Иона" to "Ион",
        "Михей" to "Мих", "Наум" to "Наум", "Аввакум" to "Авв", "Софония" to "Соф",
        "Аггей" to "Агг", "Захария" to "Зах", "Малахия" to "Мал",
        "От Матфея" to "Мф", "От Марка" to "Мк", "От Луки" to "Лк", "От Иоанна" to "Ин",
        "Деяния" to "Деян", "К Римлянам" to "Рим", "1 Коринфянам" to "1Кор",
        "2 Коринфянам" to "2Кор", "К Галатам" to "Гал", "К Ефесянам" to "Еф",
        "К Филиппийцам" to "Флп", "К Колоссянам" to "Кол", "1 Фессалоникийцам" to "1Фес",
        "2 Фессалоникийцам" to "2Фес", "1 Тимофею" to "1Тим", "2 Тимофею" to "2Тим",
        "К Титу" to "Тит", "К Филимону" to "Флм", "К Евреям" to "Евр", "Иакова" to "Иак",
        "1 Петра" to "1Пет", "2 Петра" to "2Пет", "1 Иоанна" to "1Ин", "2 Иоанна" to "2Ин",
        "3 Иоанна" to "3Ин", "Иуды" to "Иуд", "Откровение" to "Откр",
    )

    fun name(book: Int): String = data.getOrNull(book - 1)?.first ?: "?"
    fun short(book: Int): String = data.getOrNull(book - 1)?.second ?: "?"
}

/** «Ин 3:16» или «Ин 3:16–18». */
fun refText(book: Int, chapter: Int, from: Int, to: Int = from): String =
    "${Books.short(book)} $chapter:$from" + if (to > from) "–$to" else ""

/** Группирует номера стихов в подряд идущие диапазоны: {1,2,3,5} → [1..3, 5..5]. */
fun verseRanges(verses: Collection<Int>): List<IntRange> {
    val sorted = verses.sorted()
    if (sorted.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var start = sorted[0]
    var prev = start
    for (v in sorted.drop(1)) {
        if (v != prev + 1) {
            out.add(start..prev)
            start = v
        }
        prev = v
    }
    out.add(start..prev)
    return out
}

class RefTarget(val book: Int, val chapter: Int, val verse: Int?)

private fun normKey(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("[\\s.]"), "")

private val bookKeys: List<List<String>> by lazy {
    (1..Books.COUNT).map { n ->
        val full = Books.name(n)
        listOf(full, full.removePrefix("От ").removePrefix("К "), Books.short(n)).map(::normKey).distinct()
    }
}

private val refRegex = Regex("^(.*?)\\s*(\\d+)(?:\\s*[:.,\\s]\\s*(\\d+)(?:\\s*[-–—]\\s*\\d+)?)?\\s*$")

/**
 * Разбирает ссылку вида «Ин 3:16», «1 Кор 13», «Иоанна 3 16», «Быт 1». Возвращает null, если это не ссылка.
 * Книга определяется по названию, сокращению или началу названия.
 */
fun parseRef(input: String): RefTarget? {
    val m = refRegex.matchEntire(input.trim()) ?: return null
    val key = normKey(m.groupValues[1])
    if (key.length < 2) return null
    val chapter = m.groupValues[2].toIntOrNull() ?: return null
    if (chapter < 1) return null
    val verse = m.groupValues[3].toIntOrNull()
    val exact = bookKeys.indexOfFirst { keys -> key in keys }
    val idx = if (exact >= 0) exact else bookKeys.indexOfFirst { keys -> keys.any { it.startsWith(key) } }
    if (idx < 0) return null
    return RefTarget(idx + 1, chapter, verse)
}
