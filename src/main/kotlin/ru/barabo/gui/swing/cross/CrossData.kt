package ru.barabo.gui.swing.cross

import ru.barabo.db.service.StoreListener
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

interface CrossData<E> {

    fun getRowCount(): Int

    fun getRowType(rowIndex: Int): RowType

    fun getEntity(rowIndex: Int): E?

    fun addListener(listener : StoreListener<List<E>>)

    fun getCellValue(rowIndex: Int, column: CrossColumn<E>): Any {
        val entity = getEntity(rowIndex) ?: return ""

        return column.prop.get(entity) ?: ""
    }

    fun setValue(value: Any?, rowIndex: Int, propColumn: KMutableProperty1<E, Any?>)

}

data class CrossColumn<E>(val name: ()->String,
                          val prop: KProperty1<E, Any?>,
                          val width: Int = 10)

enum class RowType(val dbValue: Int) {
    SIMPLE(0),
    SUM(2),
    HEADER(1);

    companion object {
        fun rowTypeByDbValue(dbValue: Int) = values().firstOrNull { it.dbValue == dbValue }
            ?: throw Exception("RowType for dbValue = $dbValue not found")
    }
}