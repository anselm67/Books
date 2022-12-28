package com.anselm.books.database

data class Query (
    var query: String? = null,
    var partial: Boolean = false,
    var location: Long = 0,
    var genre: Long = 0,
    var publisher: Long = 0,
    var author: Long = 0,
    var sortBy: Int = BookDao.SortByTitle,
)
