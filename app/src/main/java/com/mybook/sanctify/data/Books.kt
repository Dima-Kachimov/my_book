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
