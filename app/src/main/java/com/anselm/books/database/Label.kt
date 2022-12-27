package com.anselm.books.database

import androidx.room.*

@Entity(
    tableName = "label_table",
    indices = [
        Index(value = [ "type", "name" ], unique = true)
    ]
)
data class Label(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    var type: Int,
    var name: String
) {
    constructor(type: Int, name: String) : this(0, type, name) {
        this.type = type
        this.name = name
    }

    companion object {
        const val Authors = 1
        const val Genres = 2
        const val PhysicalLocation = 3
        const val Publisher = 4
    }
}

@Entity(tableName = "label_fts")
@Fts4(
    contentEntity = Label::class
)
data class LabelFTS(
    @ColumnInfo(name = "name")
    val name: String,
)
