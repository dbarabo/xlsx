package ru.barabo.xls

import org.jdesktop.swingx.JXHyperlink
import ru.barabo.gui.swing.*
import java.awt.BorderLayout
import java.awt.Container
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import kotlin.collections.ArrayList

fun Container.tableBox(varParam: Var, cursor: CursorData, gridY: Int, vars: List<Var>, paramContainer: ParamContainer): JXHyperlink {
    val label = varParam.name.replace('_', ' ').lowercase(Locale.getDefault())
    add( JLabel(label), labelConstraint(gridY) )

    val hyperTable = JXHyperlink()

    val record = varParam.result.value as Record

    val findIndex = cursor.findRowByRecord(record)

    cursor.setRecordFromCursor(record, hyperTable, findIndex)

    hyperTable.addActionListener {
        cursor.clickTableBox(label, paramContainer.bookForTableBox!!, vars, varParam.result.value as Record, hyperTable)
    }

    add(hyperTable, textConstraint(gridY = gridY, gridX = 1) )

    return hyperTable
}

private fun CursorData.setRecordFromCursor(record: Record, hyperText: JButton, rowIndex: Int?) {

    val newIndex = rowIndex?.let { setRowIndex(it) }

    hyperText.text = newIndex?.let {

        setRecordByRow(record, it)

        data[it][0].toString()
    } ?: "Нажмите для выбора..."
}

private fun CursorData.clickTableBox(label: String, book: JTabbedPane, vars: List<Var>, record: Record, hyperText: JButton) {

    val result = book.saveTabs()

    val tabTable = createTabTable(vars, result, record, hyperText)

    book.addTab(label, tabTable)
}

private fun CursorData.createTabTable(vars: List<Var>, storeStateBook: TabsInBook, record: Record, hyperText: JButton): Container {

    val findButton = findButton(this)

    val table = TableCursorData(this, findButton, storeStateBook, record, hyperText)

    val topFilterPanel = createFilterPanel(vars, storeStateBook, this, findButton, record, hyperText)

    return JPanel().apply {
        layout = BorderLayout()

        add(topFilterPanel, BorderLayout.NORTH)

        add( JScrollPane(table), BorderLayout.CENTER )
    }
}

private fun createFilterPanel(vars: List<Var>, storeStateBook: TabsInBook, cursorData: CursorData,
                              findButton: JButton, record: Record, hyperText: JButton): Container {

    val varParams = cursorData.paramsByVars(vars)

    return JPanel().apply {

        layout = GridBagLayout()

        for( (index, param) in varParams.withIndex() ) {
            textField(param, index) { result, field ->
                val text = field?.replace('*', '%')

                varResultTextFieldListener(result, text)

                cursorData.invalidate()
                findButton.isEnabled = !cursorData.isBusy
            }
        }

        liteGroup("", varParams.size, 0, 1).apply {

            onlyButton("Выбрать", 0, 0, "") {
                okSelected(storeStateBook, record, hyperText, cursorData)
            }

            onlyButton("Отменить", 0, 1, "") {
                storeStateBook.restoreTabs()
            }

            add(findButton, textConstraint(0, 1, 2) )
            maxSpaceXConstraint(3)
        }

        maxSpaceYConstraint(varParams.size + 1)
    }
}

private fun okSelected(storeStateBook: TabsInBook, record: Record, hyperText: JButton, cursorData: CursorData) {
    storeStateBook.restoreTabs()

    cursorData.setRecordFromCursor(record, hyperText, cursorData.row)
}

private fun findButton(cursorData: CursorData): JButton {
    return JButton("Найти").apply {
        addActionListener {
            cursorData.invalidate()

            this.isEnabled = !cursorData.isBusy
        }
    }
}

fun CursorData.paramsByVars(vars: List<Var>): List<Var> {
    if(params.isEmpty()) return emptyList()

    val paramsList = params.mapNotNull { par -> vars.firstOrNull { it.result === par } }

    if(paramsList.size == params.size) return paramsList

    val minusParams = params.minus(paramsList.toSet())

    val records = vars.filter { it.result.type == VarType.RECORD }

    val recordParams = minusParams.mapNotNull {
        par -> records.firstOrNull {
            (it.result.value as Record).columns.firstOrNull { col ->
                col.result === par
            } != null
        }
    }

    return paramsList.plus(recordParams)
}

class TableCursorData(private val cursor: CursorData, private val findButton: JButton,
                      storeStateBook: TabsInBook, record: Record, hyperText: JButton) : JTable(), CursorDateListener {

    private val columnSum: Int

    init {
        model = CursorDataTableModel(cursor).apply {

            columnSum = columns.sumOf { it.width }
        }

        setColumnsSize((model as CursorDataTableModel).columns)

        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)

        if(model.rowCount > cursor.row) {
            selectionModel.setSelectionInterval(cursor.row, cursor.row)
        }

        selectionModel.addListSelectionListener (::selectListener)

        addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {

                    if(e?.clickCount == 2 && SwingUtilities.isLeftMouseButton(e) ) {
                        okSelected(storeStateBook, record, hyperText, cursor)
                    }
                }
            })

        cursor.addListener(this)
    }

    private fun selectListener(e: ListSelectionEvent) {
        if (e.valueIsAdjusting) return

        val selModel = e.source as? ListSelectionModel ?: return
        // Номер текущей строки таблицы
        if (selModel.isSelectionEmpty) return

        cursor.setRowIndex(selModel.minSelectionIndex)
    }

    override fun changeData() {
        intoSwingThread {

            val tableModel = model as CursorDataTableModel

            tableModel.fireTableStructureChanged()

            setColumnsSize(tableModel.columns)

            findButton.isEnabled = !cursor.isBusy
        }
    }

    private fun setColumnsSize(columns: List<ColumnCursorData>) {

        val delimetr = width.toDouble() / columnSum

        for((index, column) in columns.withIndex()) {

            columnModel.getColumn(index).preferredWidth = (column.width * delimetr).toInt()

            columnModel.getColumn(index).width = (column.width * delimetr).toInt()
        }
    }
}

private class CursorDataTableModel(private val cursor: CursorData) : AbstractTableModel() {

    val columns: List<ColumnCursorData> = initColumns(cursor)

    override fun getRowCount(): Int = cursor.data.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column].label

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        if(rowIndex >= cursor.data.size || columnIndex >= columns.size) return ""

        val column = columns[columnIndex]

        val value = cursor.data[rowIndex][column.index] ?: return ""

        return column.converter.convert(value)
    }

    private fun initColumns(cursor: CursorData): List<ColumnCursorData> {

        if(!cursor.isOpen) {
            cursor.invalidate()
        }

        val columns = ArrayList<ColumnCursorData>()

        for (ColIndex in cursor.columns.withIndex() ) {
            if(ColIndex.value.lastIndexOf('_') == ColIndex.value.lastIndex) continue

            val defaultWidthConverter = defaultWidthAndType(cursor.sqlColumnType[ColIndex.index].toSqlValueNull())

            columns.add(
                ColumnCursorData(index = ColIndex.index, label = ColIndex.value,
                    width = defaultWidthConverter.width * if(columns.isEmpty()) 2 else 1,
                converter = defaultWidthConverter.converter) )
        }

        return columns
    }

    private fun defaultWidthAndType(defType: Any): ConverterWidth {

        return when (defType) {
            Long::class.javaObjectType -> ConverterWidth.INT

            Double::class.javaObjectType -> ConverterWidth.DOUBLE

            java.time.LocalDateTime::class.javaObjectType -> ConverterWidth.DATE

            else -> ConverterWidth.STRING
        }
    }
}

private data class ColumnCursorData(val index: Int, val label: String, val width: Int, val converter: ConverterValue )

interface ConverterValue {
    fun  convert(value: Any): String
}

enum class ConverterWidth(val width: Int, val converter: ConverterValue) {
    STRING(18, object: ConverterValue {
        override fun convert(value: Any): String = value.toString()
    }),

    INT(6, object: ConverterValue {
        override fun convert(value: Any): String = (value as Number).toString()
    }),

    DOUBLE(9, object: ConverterValue {
        override fun convert(value: Any): String = (value as Number).toString()
    }),

    DATE(9, object: ConverterValue {
        override fun convert(value: Any): String = byFormatDate(value as Date)
    })
}