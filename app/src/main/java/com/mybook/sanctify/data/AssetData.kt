package com.mybook.sanctify.data

import android.content.Context
import android.util.LruCache

/**
 * Встроенные данные (папка assets), подготовленные tools/build_assets.py:
 *  - xrefs/{книга}.txt — перекрёстные ссылки OpenBible.info (CC BY): по 10 лучших на стих;
 *  - words/{книга}.txt — слова оригинала (греч. NA28 / евр. Ленинградский кодекс) с номерами Стронга, STEPBible/Tyndale House (CC BY 4.0);
 *  - lexicon/lexicon.txt — краткий словарь (Abbott-Smith для греческого, BDB-основа для еврейского).
 */
object AssetData {
    class XRef(val book: Int, val chapter: Int, val from: Int, val to: Int)
    class Word(val orig: String, val translit: String, val gloss: String, val strong: String, val lemma: String)
    class LexEntry(val lemma: String, val translit: String, val gloss: String, val definition: String)

    private val xrefCache = LruCache<Int, Map<String, List<XRef>>>(4)
    private val wordCache = LruCache<Int, Map<String, List<Word>>>(4)
    private var lexicon: Map<String, LexEntry>? = null

    private fun parseRef(s: String): XRef? {
        val p = s.split('.')
        if (p.size < 3) return null
        val b = p[0].toIntOrNull() ?: return null
        val c = p[1].toIntOrNull() ?: return null
        val vs = p[2].split('-')
        val v = vs[0].toIntOrNull() ?: return null
        val e = vs.getOrNull(1)?.toIntOrNull() ?: v
        return XRef(b, c, v, e)
    }

    fun crossRefs(ctx: Context, book: Int, chapter: Int, verse: Int): List<XRef> {
        val map = xrefCache.get(book) ?: run {
            val m = HashMap<String, List<XRef>>()
            try {
                ctx.assets.open("xrefs/$book.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val t = line.indexOf('\t')
                        if (t > 0) m[line.substring(0, t)] = line.substring(t + 1).split(';').mapNotNull { parseRef(it) }
                    }
                }
            } catch (e: Exception) {
                // нет файла — нет ссылок
            }
            xrefCache.put(book, m)
            m
        }
        return map["$chapter:$verse"] ?: emptyList()
    }

    fun words(ctx: Context, book: Int, chapter: Int, verse: Int): List<Word> {
        val map = wordCache.get(book) ?: run {
            val m = HashMap<String, List<Word>>()
            try {
                ctx.assets.open("words/$book.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size > 1) {
                            m[parts[0]] = parts.drop(1).map { w ->
                                val f = w.split('|')
                                Word(f.getOrElse(0) { "" }, f.getOrElse(1) { "" }, f.getOrElse(2) { "" }, f.getOrElse(3) { "" }, f.getOrElse(4) { "" })
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // нет файла — нет слов
            }
            wordCache.put(book, m)
            m
        }
        return map["$chapter:$verse"] ?: emptyList()
    }

    @Synchronized
    fun lexicon(ctx: Context, strong: String): LexEntry? {
        val lex = lexicon ?: HashMap<String, LexEntry>().also { m ->
            try {
                ctx.assets.open("lexicon/lexicon.txt").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val f = line.split('\t')
                        if (f.size >= 5) m[f[0]] = LexEntry(f[1], f[2], f[3], f[4].replace("\\n", "\n").trim())
                    }
                }
            } catch (e: Exception) {
                // нет словаря
            }
            lexicon = m
        }
        return lex[strong] ?: lex[strong.dropLastWhile { it.isLetter() }]
    }
}
