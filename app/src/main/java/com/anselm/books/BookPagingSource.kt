package com.anselm.books

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.anselm.books.database.BookRepository
import com.anselm.books.database.Book
import java.lang.Integer.max

private const val START_PAGE = 0

class BookPagingSource(
    private val repository: BookRepository
) : PagingSource<Int, Book>() {
    private var itemCount = -1

    override val jumpingSupported: Boolean
        get() = true

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val page = params.key ?: START_PAGE

        return try {
            val books = repository.getPagedList(params.loadSize, page * params.loadSize)
            books.forEach { repository.decorate(it) }
            if (itemCount < 0) {
                itemCount = repository.getPagedListCount()
            }
            Log.d(TAG, "-> Got ${books.size}/$itemCount results," +
                    " page: $page" +
                    " before: ${page * params.loadSize}" +
                    " after: ${max(0, itemCount - (page + 1) * params.loadSize)}")
            LoadResult.Page(
                data = books,
                prevKey = when (page) {
                    START_PAGE -> null
                    else -> page - 1 },
                nextKey = if (books.isEmpty()) null else page + 1,
                itemsBefore = page * params.loadSize,
                itemsAfter = max(0, itemCount - (page + 1) * params.loadSize)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Book>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

}