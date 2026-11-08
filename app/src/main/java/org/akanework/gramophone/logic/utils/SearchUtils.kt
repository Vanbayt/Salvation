package org.akanework.gramophone.logic.utils

object SearchUtils {
    fun transliterateToLatin(input: String): String {
        val s = input.trim()
        val sb = StringBuilder()
        for (ch in s) {
            when (ch.lowercaseChar()) {
                'а' -> sb.append('a')
                'б' -> sb.append('b')
                'в' -> sb.append('v')
                'г' -> sb.append('g')
                'д' -> sb.append('d')
                'е', 'ё' -> sb.append('e')
                'ж' -> sb.append("zh")
                'з' -> sb.append('z')
                'и' -> sb.append('i')
                'й' -> sb.append('y')
                'к' -> sb.append('k')
                'л' -> sb.append('l')
                'м' -> sb.append('m')
                'н' -> sb.append('n')
                'о' -> sb.append('o')
                'п' -> sb.append('p')
                'р' -> sb.append('r')
                'с' -> sb.append('s')
                'т' -> sb.append('t')
                'у' -> sb.append('u')
                'ф' -> sb.append('f')
                'х' -> sb.append("kh")
                'ц' -> sb.append("ts")
                'ч' -> sb.append("ch")
                'ш' -> sb.append("sh")
                'щ' -> sb.append("shch")
                'ъ', 'ь' -> {}
                'ы' -> sb.append('y')
                'э' -> sb.append('e')
                'ю' -> sb.append("yu")
                'я' -> sb.append("ya")
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("yy", "y").replace("ij", "i").replace("iy", "i")
    }

    fun transliterateToCyrillic(input: String): String {
        var s = input.lowercase().trim()
        val pairs = listOf(
            "shch" to "щ", "yo" to "ё", "zh" to "ж", "kh" to "х",
            "ts" to "ц", "ch" to "ч", "sh" to "ш", "yu" to "ю", "ya" to "я",
            "aria" to "ария", "bespechnyy" to "беспечный", "bespechnyy angel" to "беспечный ангел",
            "angel" to "ангел", "shtil" to "штиль", "ia" to "ия", "iya" to "ия", "x" to "кс"
        )
        for ((lat, cyr) in pairs) {
            s = s.replace(lat, cyr)
        }
        val singleMap = mapOf(
            'a' to 'а', 'b' to 'б', 'c' to 'к', 'd' to 'д',
            'e' to 'е', 'f' to 'ф', 'g' to 'г', 'h' to 'х',
            'i' to 'и', 'j' to 'й', 'k' to 'к', 'l' to 'л',
            'm' to 'м', 'n' to 'н', 'o' to 'о', 'p' to 'п',
            'q' to 'к', 'r' to 'р', 's' to 'с', 't' to 'т',
            'u' to 'у', 'v' to 'в', 'w' to 'в',
            'y' to 'ы', 'z' to 'з'
        )
        val sb = StringBuilder()
        for (ch in s) {
            sb.append(singleMap[ch] ?: ch)
        }
        return sb.toString()
    }

    fun matchesQuery(title: String, artist: String, album: String?, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true

        val tLow = title.lowercase()
        val aLow = artist.lowercase()
        val albLow = album?.lowercase() ?: ""

        if (tLow.contains(q) || aLow.contains(q) || albLow.contains(q)) return true

        val cyrQ = transliterateToCyrillic(q)
        if (cyrQ.isNotEmpty() && cyrQ != q) {
            if (tLow.contains(cyrQ) || aLow.contains(cyrQ) || albLow.contains(cyrQ)) return true
        }

        val latQ = transliterateToLatin(q)
        if (latQ.isNotEmpty() && latQ != q) {
            if (tLow.contains(latQ) || aLow.contains(latQ) || albLow.contains(latQ)) return true
        }

        if ((q.contains("aria") || q.contains("ария")) && (aLow.contains("aria") || aLow.contains("ария"))) {
            return true
        }

        return false
    }
}
