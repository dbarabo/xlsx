package ru.barabo.gui.swing.table

import ru.barabo.db.EditType
import ru.barabo.db.service.StoreFilterService
import ru.barabo.db.service.StoreListener
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel

open class EntityTable<T: Any>(private val columns: List<ColumnTableModel<T, *>>, private val store: StoreFilterService<T>) : JTable(),
    StoreListener<List<T>> {

    private var isFirstRefresh = true

    private val columnSum: Int

    init {
        model = DefaultTableModel(columns, store)

        setColumnsSize(columns)

        selectionModel.addListSelectionListener(::selectListener)

        componentPopupMenu = getPopupMenu()

        store.addListener(this)

        columnSum = columns.sumOf { it.width }
    }

    var isReadOnly: Boolean
    get() = (model as DefaultTableModel<*>).isReadOnly
    set(value) {
        (model as DefaultTableModel<*>).isReadOnly = value
    }

    private fun selectListener(e: ListSelectionEvent) {
        if (e.valueIsAdjusting) return

        val selModel = e.source as? ListSelectionModel ?: return

        if (selModel.isSelectionEmpty) return

        store.selectedRowIndex = selModel.minSelectionIndex
    }

    private fun setColumnsSize(columns: List<ColumnTableModel<T, *>>) {

        val delimetr = width.toDouble() / columnSum

        for((index, column) in columns.withIndex()) {

            columnModel.getColumn(index).preferredWidth = (column.width * delimetr).toInt()

            columnModel.getColumn(index).width = (column.width * delimetr).toInt()
        }
    }

    override fun refreshAll(elemRoot: List<T>, refreshType: EditType) {

        if(refreshType == EditType.CHANGE_CURSOR &&
           selectionModel.minSelectionIndex == store.selectedRowIndex) return

        val tableModel = model as? AbstractTableModel ?: return

        if(isFirstRefresh && elemRoot.isNotEmpty()) {
            isFirstRefresh = false
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            tableModel.fireTableStructureChanged()
            setColumnsSize(columns)

            return
        }

        tableModel.fireTableDataChanged()
    }

    override fun getColumnClass(column: Int): Class<*>  = model.getColumnClass(column)

    private fun getPopupMenu(): JPopupMenu =
        JPopupMenu().apply {

            add( JMenuItem("Копировать ячейку").apply {
                addActionListener { copyCell()}
            })

            add( JMenuItem("Копировать строку").apply {
                addActionListener { copyRow()}
            })

            add( JMenuItem("Копировать всю таблицу").apply {
                addActionListener { copyTable()}
            })
        }

    private fun copyTable() {

        val data = store.elemRoot()

        val tableData = StringBuilder()

        for (row in data) {
            tableData.append(entityToString(row)).append("\n")
        }

        val selection = StringSelection(tableData.toString())

        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    private fun copyRow() {
        val row = store.selectedEntity()?.let { entityToString(it) } ?: return

        val selection = StringSelection(row)

        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    private fun copyCell() {
        val row = store.selectedEntity()?.let { entityToString(it) } ?: return

        val cell = if(this.selectedColumn >= 0) {

            var count = 0
            var start = -1
            while(count < this.selectedColumn) {
                start = row.indexOf('\t', start + 1)
                if(start >= 0) {
                    count++
                }
            }

            var end = row.indexOf('\t', start + 1)
            end = if(end < 0) row.length else end

            row.substring(start + 1, end)
        } else {
            row
        }
        val selection = StringSelection(cell)

        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
    }

    private fun entityToString(entity: T): String {
        return if(model is DefaultTableModel<*>) {
            (model as? DefaultTableModel<T>)?.getEntityByString(entity)?:""
        } else {
            entity.toString()
        }
    }

}

fun JTable.doubleClickEvent(process: ()->Unit) {
    addMouseListener(
        object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {

                if(e?.clickCount == 2 && SwingUtilities.isLeftMouseButton(e) ) {
                    process()
                }
            }
        })
}
