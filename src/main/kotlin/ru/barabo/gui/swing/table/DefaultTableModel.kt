package ru.barabo.gui.swing.table

import ru.barabo.db.service.StoreService
import javax.swing.JOptionPane
import javax.swing.table.AbstractTableModel
import kotlin.reflect.KMutableProperty1

class DefaultTableModel<T: Any>(private val columns: List<ColumnTableModel<T, *>>, val store: StoreService<T, *>) : AbstractTableModel() {

    var isReadOnly = false

    override fun getRowCount(): Int = store.dataListCount()

    override fun getColumnCount(): Int = columns.size

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {

        val entity = store.getEntity(rowIndex) ?: return null

        val prop = columns[columnIndex].prop

        return prop.get(entity)
    }

    fun getEntityByString(entity: T): String = columns.map { it.prop.get(entity) }.joinToString("\t")

    override fun getColumnName(column: Int): String = columns[column].title

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        if(isReadOnly) return false

        return if(store.dataListCount() == 0) super.isCellEditable(rowIndex, columnIndex)
        else columns[columnIndex].isEditable
    }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if(store.dataListCount() == 0) super.getColumnClass(columnIndex)
        else columns[columnIndex].prop.get(store.getEntity(0)!! )?.let { it::class.java }
            ?: super.getColumnClass(columnIndex)

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        val entity = store.setEntityShowError(rowIndex, columns[columnIndex].prop, aValue) ?: return

        store.saveEntityShowError(entity)
    }
}

fun <T: Any> StoreService<T, *>.saveEntityShowError(entity: T) {
    try {
        save(entity)
    } catch (e: Exception) {
        errorMessage(e.message)
    }
}

fun <T: Any> StoreService<T, *>.setEntityShowError(rowIndex: Int, propSet: KMutableProperty1<T, *>, value: Any?): T? {
    return try {
        getEntity(rowIndex).apply {
            propSet.setter.call(this, value)
        }
    } catch (e: Exception) {
        errorMessage(e.message).let { null }
    }
}

fun errorMessage(message: String?): Boolean {
    JOptionPane.showMessageDialog(null, message, null, JOptionPane.ERROR_MESSAGE)

    return false
}