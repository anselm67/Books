package com.anselm.books.database

import androidx.room.*

private class Converters {
    @TypeConverter
    fun toType(value: Int) = enumValues<Label.Type>()[value]

    @TypeConverter
    fun fromType(value: Label.Type) = value.ordinal
}

@Entity(
    tableName = "label_table",
    indices = [
        Index(value = [ "type", "name" ], unique = true)
    ]
)
@TypeConverters(Converters::class)
data class Label(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    var type: Type,
    var name: String
) {
    constructor(type: Type, name: String) : this(0, type, name) {
        this.type = type
        this.name = name
    }

    enum class Type(val type: Int) {
        Authors(1),
        Genres(2),
        Location(3),
        Publisher(4)
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
