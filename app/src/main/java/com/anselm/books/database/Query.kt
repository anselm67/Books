package com.anselm.books.database

data class Query (
    var query: String? = null,
    var partial: Boolean = false,
    var location: String? = null,
    var genre: String? = null,
    var publisher: String? = null,
    var author: String? = null,
    var sortBy: Int = BookDao.SortByTitle,
)
