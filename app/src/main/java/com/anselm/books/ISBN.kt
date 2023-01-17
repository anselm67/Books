package com.anselm.books

class ISBN {

    companion object {
        private fun digit(c: Char): Int {
            return c.digitToInt()
        }

        fun isValidEAN13(isbn: String): Boolean {
            // Quick checks: empty is fine.
            if (isbn.isEmpty()) {
                return true
            } else if (isbn.length != 13) {
                return false
            }
            // Computes the expected checksum / last digit.
            val sum1 = arrayListOf(0, 2, 4, 6, 8, 10).sumOf { it -> digit(isbn[it]) }
            val sum2 = 3 * arrayListOf(1, 3, 5, 7, 9, 11).sumOf { it -> digit(isbn[it]) }
            val checksum = (sum1 + sum2) % 10
            val expected = if (checksum == 0) '0' else ('0' + 10 - checksum)
            return expected == isbn[12]
        }
    }
}