package net.essentuan.erika.kord.framework.message

import java.util.Date

enum class Timestamp(
    private val char: Char
) {
    SHORT_TIME('t'), //12:00 AM
    LONG_TIME('T'), //12:00:00 AM
    SHORT_DATE('d'), //1/1/24
    LONG_DATE('D'), //January 1, 2024
    LONG_DATE_WITH_TIME('f'), //January 1, 2024 at 12:00 AM
    LONG_DATE_WITH_DAY('F'), //Monday, January 1, 2024 at 12:00 AM
    RELATIVE('R'); //7 months ago

    operator fun invoke(date: Date): String =
        "<t:${date.time/1000}:$char>"
}