package com.anselm.books

import androidx.paging.PagingSource
import androidx.paging.PagingState

private const val START_PAGE = 0

class BookPagingSource(private val repository: BookRepository) : PagingSource<Int, Book>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val page = params.key ?: START_PAGE

        return try {
            val books = repository.getPagedList(params.loadSize, page * params.loadSize)

            LoadResult.Page(
                data = books,
                prevKey = when (page) {
                    START_PAGE -> null
                    else -> page - 1 },
                nextKey = if (books.isEmpty()) null else page + 1
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