package com.cslearningos.graph.domain

import java.security.MessageDigest

/**
 * 命令内容指纹(RFC §3.1 不变量: 所有写走幂等命令 commandId+fingerprint).
 * canonicalize 把命令负载归一为稳定 JSON 串, 再做 SHA-256; 同一语义负载必得同一指纹.
 */
object Fingerprint {

    /** 指纹比对结果 */
    enum class MatchResult {
        /** 无历史记录: 首次执行 */
        NEW,

        /** 同 commandId 同指纹: 幂等重放, 应返回首次结果且不得双写 */
        REPLAY,

        /** 同 commandId 异指纹: 冲突, 必须拒绝 */
        CONFLICT,
    }

    /** canonicalize 的格式版本(变更归一规则时升级, 避免新旧指纹串扰) */
    private const val SPEC_CANON_VERSION = "PrerequisiteSpec:v1"

    /**
     * 把 [spec] 归一化为稳定 JSON 串:
     * trim 标题/正文、空 existingNodeId 归一为 null、子列表按 (title, 自身 canonical 串) 递归排序、键按字典序输出.
     * 同一语义负载(与 children 书写顺序、空白差异无关)必得同一串.
     */
    fun canonicalize(spec: PrerequisiteSpec): String = canonOf(spec).json

    /** [spec] 的内容指纹 = SHA-256(版本前缀 + canonicalize) */
    fun fingerprint(spec: PrerequisiteSpec): String = sha256Hex(SPEC_CANON_VERSION + "\n" + canonicalize(spec))

    /** 任意已归一化文本负载的指纹(用于非 spec 类命令) */
    fun fingerprintOf(canonicalPayload: String): String = sha256Hex(canonicalPayload)

    /**
     * 幂等三态判定: [storedFingerprint] 为 null → NEW; 与 [fingerprint] 相同 → REPLAY; 不同 → CONFLICT.
     * [commandId] 仅用于调用方构造错误/日志, 判定只依赖指纹.
     */
    fun matches(commandId: String, fingerprint: String, storedFingerprint: String?): MatchResult {
        // commandId 不参与判定, 仅供调用方透传日志/错误上下文
        return when {
            storedFingerprint == null -> MatchResult.NEW
            storedFingerprint == fingerprint -> MatchResult.REPLAY
            else -> MatchResult.CONFLICT
        }
    }

    /** SHA-256 十六进制(小写)摘要 */
    fun sha256Hex(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    // ------------------------------------------------------------------
    // 内部: 递归归一
    // ------------------------------------------------------------------

    private data class Canon(val title: String, val json: String, val node: Map<String, Any?>)

    private fun canonOf(spec: PrerequisiteSpec): Canon {
        val title = spec.title.trim()
        val children = spec.children
            .map { canonOf(it) }
            .sortedWith(compareBy({ it.title }, { it.json }))
        val node: Map<String, Any?> = linkedMapOf(
            "children" to children.map { it.node },
            "existingNodeId" to spec.existingNodeId?.trim()?.takeIf { it.isNotEmpty() },
            "markdownBody" to spec.markdownBody.trim(),
            "title" to title,
        )
        return Canon(title, MiniJson.write(node), node)
    }
}

/**
 * 模块内最小 JSON 读写器(确定性输出: 对象键按字典序; 只支持 null/Boolean/Number/String/Array/Object).
 * 用于 canonical 指纹串、提案 payload、导出契约的手写序列化与解析.
 */
internal object MiniJson {

    /** 确定性序列化(对象键排序, 数组保序) */
    fun write(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        is Int, is Long -> value.toString()
        is Double -> value.toString()
        is Float -> value.toDouble().toString()
        is Map<*, *> -> value.entries
            .sortedBy { it.key.toString() }
            .joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + write(it.value) }
        is Iterable<*> -> value.joinToString(",", "[", "]") { write(it) }
        else -> quote(value.toString())
    }

    /** 字符串加引号并转义 */
    fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else ->
                    if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0'))
                    else append(c)
            }
        }
        append('"')
    }

    /** 解析 JSON 文本为 Map/List/String/Double/Boolean/null 结构; 非法输入抛 [IllegalArgumentException] */
    fun parse(text: String): Any? {
        val parser = Parser(text)
        parser.skipWs()
        val value = parser.readValue()
        parser.skipWs()
        if (!parser.eof()) throw IllegalArgumentException("JSON 尾部存在多余内容 @ ${parser.pos}")
        return value
    }

    private class Parser(val s: String) {
        var pos = 0

        fun eof(): Boolean = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(): Any? {
            if (eof()) throw IllegalArgumentException("JSON 意外结束")
            return when (val c = s[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("非法 JSON 字符 '$c' @ $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // consume '{'
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (!eof() && s[pos] == '}') {
                pos++
                return map
            }
            while (true) {
                skipWs()
                val key = readString()
                skipWs()
                expect(':')
                skipWs()
                map[key] = readValue()
                skipWs()
                when {
                    !eof() && s[pos] == ',' -> pos++
                    !eof() && s[pos] == '}' -> {
                        pos++
                        return map
                    }
                    else -> throw IllegalArgumentException("对象缺少 ',' 或 '}' @ $pos")
                }
            }
        }

        private fun readArray(): List<Any?> {
            pos++ // consume '['
            val list = ArrayList<Any?>()
            skipWs()
            if (!eof() && s[pos] == ']') {
                pos++
                return list
            }
            while (true) {
                skipWs()
                list.add(readValue())
                skipWs()
                when {
                    !eof() && s[pos] == ',' -> pos++
                    !eof() && s[pos] == ']' -> {
                        pos++
                        return list
                    }
                    else -> throw IllegalArgumentException("数组缺少 ',' 或 ']' @ $pos")
                }
            }
        }

        private fun readString(): String {
            if (eof() || s[pos] != '"') throw IllegalArgumentException("应为字符串 @ $pos")
            pos++
            val sb = StringBuilder()
            while (!eof()) {
                when (val c = s[pos++]) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (eof()) throw IllegalArgumentException("转义不完整 @ $pos")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw IllegalArgumentException("\\u 转义不完整 @ $pos")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw IllegalArgumentException("非法转义 '\\$e' @ $pos")
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("字符串未闭合")
        }

        private fun readNumber(): Double {
            val start = pos
            if (!eof() && s[pos] == '-') pos++
            while (!eof() && (s[pos].isDigit() || s[pos] == '.' || s[pos] == 'e' || s[pos] == 'E' ||
                    s[pos] == '+' || s[pos] == '-')
            ) {
                pos++
            }
            return s.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("非法数字 @ $start")
        }

        private fun <T> readLiteral(lit: String, value: T): T {
            if (s.startsWith(lit, pos)) {
                pos += lit.length
                return value
            }
            throw IllegalArgumentException("非法字面量 @ $pos")
        }

        private fun expect(c: Char) {
            if (eof() || s[pos] != c) throw IllegalArgumentException("应为 '$c' @ $pos")
            pos++
        }
    }
}
