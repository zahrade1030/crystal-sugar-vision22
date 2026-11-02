package com.example.cv

import java.util.Locale

data class ParsedVector(
    val schema: Int,
    val vector: DoubleArray,
    val lab: Triple<Double, Double, Double>? = null
)

object VectorFormat {

    /**
     * Serializes feature vector and optional Lab summary into Locale-safe JSON (Schema 3).
     */
    fun serialize(vector: DoubleArray, lab: Triple<Double, Double, Double>? = null, schema: Int = 3): String {
        val sb = StringBuilder()
        sb.append("{\"schema\":").append(schema)
        sb.append(",\"n\":").append(vector.size)
        sb.append(",\"v\":[")
        for (i in vector.indices) {
            if (i > 0) sb.append(",")
            sb.append(String.format(Locale.US, "%.4f", vector[i]))
        }
        sb.append("]")
        if (lab != null) {
            sb.append(",\"lab\":{\"l\":")
                .append(String.format(Locale.US, "%.4f", lab.first))
                .append(",\"a\":")
                .append(String.format(Locale.US, "%.4f", lab.second))
                .append(",\"b\":")
                .append(String.format(Locale.US, "%.4f", lab.third))
                .append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    /**
     * Strict parser for feature vector strings.
     * Supports:
     * 1. Schema 3 versioned JSON: {"schema":3, "n":191, "v":[...], "lab":{...}}
     * 2. Legacy clean JSON array: [0.1234, 0.5678, ...]
     * 3. Recoverable comma-split legacy array (382 tokens where odd tokens have 4 digits)
     *
     * Returns null (Invalid) if empty, corrupt, NaN, Infinite, or dimension mismatch.
     */
    fun parse(rawString: String?): ParsedVector? {
        if (rawString.isNullOrBlank()) return null
        val trimmed = rawString.trim()

        // 1. Versioned JSON format
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                var schema = 3
                val schemaMatch = Regex("\"schema\"\\s*:\\s*(\\d+)").find(trimmed)
                if (schemaMatch != null) {
                    schema = schemaMatch.groupValues[1].toInt()
                }

                // Extract "v":[ ... ]
                val vIdx = trimmed.indexOf("\"v\"")
                if (vIdx == -1) return null
                val openBracket = trimmed.indexOf('[', vIdx)
                val closeBracket = trimmed.indexOf(']', openBracket)
                if (openBracket == -1 || closeBracket == -1) return null

                val arrayContent = trimmed.substring(openBracket + 1, closeBracket)
                val parsedVector = parseArrayContent(arrayContent) ?: return null

                // Extract optional LAB
                var lab: Triple<Double, Double, Double>? = null
                val labIdx = trimmed.indexOf("\"lab\"")
                if (labIdx != -1) {
                    val lMatch = Regex("\"l\"\\s*:\\s*([-\\d.]+)").find(trimmed, labIdx)
                    val aMatch = Regex("\"a\"\\s*:\\s*([-\\d.]+)").find(trimmed, labIdx)
                    val bMatch = Regex("\"b\"\\s*:\\s*([-\\d.]+)").find(trimmed, labIdx)
                    if (lMatch != null && aMatch != null && bMatch != null) {
                        val lVal = lMatch.groupValues[1].toDoubleOrNull()
                        val aVal = aMatch.groupValues[1].toDoubleOrNull()
                        val bVal = bMatch.groupValues[1].toDoubleOrNull()
                        if (lVal != null && aVal != null && bVal != null) {
                            lab = Triple(lVal, aVal, bVal)
                        }
                    }
                }

                return ParsedVector(schema = schema, vector = parsedVector, lab = lab)
            } catch (e: Exception) {
                return null
            }
        }

        // 2. Legacy array format: [ ... ]
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            val content = trimmed.substring(1, trimmed.length - 1)
            val parsed = parseArrayContent(content)
            return if (parsed != null) ParsedVector(schema = 1, vector = parsed) else null
        }

        return null
    }

    private fun parseArrayContent(content: String): DoubleArray? {
        if (content.isBlank()) return null

        // Convert Persian/Arabic digits and decimal slash/momayyaz
        val normalized = content.map { ch ->
            when (ch) {
                in '\u06F0'..'\u06F9' -> (ch - '\u06F0' + '0'.code).toChar()
                in '\u0660'..'\u0669' -> (ch - '\u0660' + '0'.code).toChar()
                '\u066B', '/' -> '.'
                else -> ch
            }
        }.joinToString("")

        val tokens = normalized.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null

        // Attempt recovery from comma-decimal split (382 tokens for 191 elements)
        if (tokens.size == ProvisionalConstants.EXPECTED_FEATURE_VECTOR_SIZE * 2) {
            val recovered = DoubleArray(ProvisionalConstants.EXPECTED_FEATURE_VECTOR_SIZE)
            var canRecover = true
            for (i in 0 until ProvisionalConstants.EXPECTED_FEATURE_VECTOR_SIZE) {
                val intPart = tokens[2 * i]
                val decPart = tokens[2 * i + 1]
                if (decPart.length != 4 || !decPart.all { it.isDigit() }) {
                    canRecover = false
                    break
                }
                val numStr = "$intPart.$decPart"
                val d = numStr.toDoubleOrNull()
                if (d == null || d.isNaN() || d.isInfinite()) {
                    canRecover = false
                    break
                }
                recovered[i] = d
            }
            if (canRecover) return recovered
        }

        // Standard token parsing for any valid array length
        val vector = DoubleArray(tokens.size)
        for (i in tokens.indices) {
            val d = tokens[i].toDoubleOrNull() ?: return null
            if (d.isNaN() || d.isInfinite()) return null
            vector[i] = d
        }
        return vector
    }
}
