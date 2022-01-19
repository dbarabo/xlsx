package ru.barabo.gui.swing.cross

import ru.barabo.db.EditType
import ru.barabo.db.service.StoreListener
import ru.barabo.gui.swing.processShowError
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.font.TextAttribute
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import kotlin.reflect.KMutableProperty1

data class CrossColumns<E>(val fixedCount: Int, var isReadOnly : Boolean = true, val columns: Array<CrossColumn<E>>)

open class CrossTable<E>(val crossColumns: CrossColumns<E>, crossData: CrossData<E>,
                         private var renderer: TableCellRenderer? = null) : JTable(), StoreListener<List<E>> {

    private val columnSum: Int

    private val integerEditor = IntegerTextEditor()

    init {
        model = CrossTableModel(crossColumns, crossData)

        if(renderer == null) {
            renderer = CrossTableRenderer(crossColumns, crossData)
        }

        columnSum = crossColumns.columns.sumOf { it.width }

        crossData.addListener(this)

        setColumnsSize(crossColumns.columns)
    }

    private fun setColumnsSize(columns: Array<CrossColumn<E>>) {

        if( getSelectionModel()?.selectionMode != ListSelectionModel.SINGLE_SELECTION) {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        }

        val delimetr = width.toDouble() / columnSum

        for((index, column) in columns.withIndex()) {

            columnModel.getColumn(index).preferredWidth = (column.width * delimetr).toInt()

            columnModel.getColumn(index).width = (column.width * delimetr).toInt()
        }
    }

    override fun getCellRenderer(row: Int, column: Int): TableCellRenderer? = renderer

    override fun getCellEditor(row: Int, column: Int): TableCellEditor = integerEditor

    override fun refreshAll(elemRoot: List<E>, refreshType: EditType) {
        if(refreshType == EditType.CHANGE_CURSOR) return

        val crossModel = model as? CrossTableModel<*> ?: return

        if(refreshType == EditType.INIT) {
            crossModel.fireTableStructureChanged()

            setColumnsSize(crossColumns.columns)
        } else {
            crossModel.fireTableDataChanged()
        }
    }
}

private class IntegerTextEditor : AbstractCellEditor(), TableCellEditor {

    private val textInteger =  JTextField()

    override fun getTableCellEditorComponent(table: JTable?, value: Any?,
                                             isSelected: Boolean, row: Int, column: Int): Component {
        textInteger.text = value?.toString()

        return textInteger
    }

    override fun getCellEditorValue(): Any = textInteger.text
}

class CrossTableModel<E>(private val crossColumns: CrossColumns<E>, private val crossData: CrossData<E>) : AbstractTableModel() {

    override fun getRowCount(): Int = crossData.getRowCount()

    override fun getColumnCount(): Int = crossColumns.columns.size

    override fun getColumnName(column: Int): String = crossColumns.columns[column].name()

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = crossData.getCellValue(rowIndex, crossColumns.columns[columnIndex])

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
        if(crossColumns.isReadOnly) return false

        if(crossColumns.fixedCount > columnIndex) return false

        if(crossData.getRowType(rowIndex) != RowType.SIMPLE) return false

        return true
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {

        processShowError {
            val propColumn: KMutableProperty1<E, Any?> = crossColumns.columns[columnIndex].prop as KMutableProperty1<E, Any?>

            crossData.setValue(aValue, rowIndex, propColumn)
        }
    }
}

val MORE_LIGHT_GRAY = Color(243, 243, 243)

val MORE_GRAY_BACK = Color(238, 238, 238)

private class CrossTableRenderer<E>(private val crossColumns: CrossColumns<E>, private val crossData: CrossData<E>)
    : JLabel(), TableCellRenderer {

    override fun getTableCellRendererComponent( table: JTable, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {

        isOpaque = true

        if (hasFocus) {
            border = UIManager.getBorder("Table.focusCellHighlightBorder")
            if (table.isCellEditable(row, column)) {
                foreground = UIManager.getColor("Table.focusCellForeground")
                background = UIManager.getColor("Table.focusCellBackground")
            }
        } else {
            border = UIManager.getBorder("TableHeader.cellBorder")
        }

        if (isSelected) {
            background = table.selectionBackground
            foreground = table.selectionForeground
        } else {
            background = table.background
            foreground = table.foreground
        }

        when(crossData.getRowType(row) ) {
            RowType.SIMPLE -> {
                background = when {
                    crossColumns.fixedCount > column -> MORE_LIGHT_GRAY
                    (table.columnCount - 1 == column) || (column == crossColumns.fixedCount) -> MORE_GRAY_BACK
                    else -> table.background
                }

                    if(crossColumns.fixedCount > column) MORE_LIGHT_GRAY else table.background

                font = table.font.deriveFont(table.font.style)

                horizontalAlignment = RIGHT

                isOpaque = true
            }

            RowType.SUM -> {
                background = when {
                    crossColumns.fixedCount > column -> MORE_LIGHT_GRAY
                    (table.columnCount - 1 == column) || (column == crossColumns.fixedCount) -> MORE_GRAY_BACK
                    else -> table.background
                }

                font = table.font.deriveFont(table.font.style or Font.ITALIC or Font.BOLD)

                horizontalAlignment = RIGHT

                isOpaque = true
            }

            RowType.HEADER -> {
                font = table.font.deriveFont(table.font.style or Font.BOLD).apply {
                    deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON))
                }

                background = Color.LIGHT_GRAY

                horizontalAlignment = CENTER
            }
        }

        text = value?.toString() ?: ""

        return this
    }
}