package ru.barabo.xls

import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.slf4j.LoggerFactory
import ru.barabo.db.Query
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList

private val logger = LoggerFactory.getLogger(PoiXlsx::class.java)

class PoiXlsx(private val template: File, query: Query, private val generateNewFile: (File) -> File): ExcelSql {

    private lateinit var newBook: Workbook

    private lateinit var sheet: Sheet

    private lateinit var rowData: List<RowXlsx>

    private lateinit var vars: MutableList<Var>

    private val parser: Parser = Parser(query)

    override var newFile: File? = null
        private set

    override fun buildWithOutParam(vars: MutableList<Var>) {
        initRowData(vars)

        processData()
    }

    override fun buildWithrequestParam(vars: MutableList<Var>, paramContainer: ParamContainer) {
        initRowData(vars)

        requestParam(paramContainer)
    }

    override fun initRowData(vars: MutableList<Var>) {
        initNewBook()

        this.vars =  vars

        val rowData = ArrayList<RowXlsx>()

        var tagLoop : LoopTagXlsx? = null

        val rows = sheet.iterator()

        while (rows.hasNext()) {
            val rowXls: Row = rows.next()

            checkErrorByRow(rowXls.rowNum) {
                val expression = getExpression(rowXls)

                val tag = getTagRow(rowXls)

                val columns = getColumns(rowXls)

                val row = RowXlsx(tag, rowXls.rowNum, expression, columns)

                when(tag) {
                    is LoopTagXlsx -> {
                        tagLoop = tag
                        rowData += row
                    }
                    is SubLoopIfTagXlsx -> {

                        if(rowXls.isCellsMerged() ) {
                            row.height = rowXls.height
                        }

                        tagLoop!!.addSubIf(row)
                    }
                    is SubLoopLoopTagXlsx -> {
                        tagLoop!!.subLoop = row
                    }
                    else -> { rowData += row }
                }
            }
        }

        this.rowData = rowData
    }

    private fun requestParam(paramContainer: ParamContainer) {
        checkErrorByRow(0, paramContainer) {
            if(rowData.isEmpty() || rowData[0].tag !is ParamTagXlsx) throw Exception("не найдены параметры")

            buildRow(rowData[0], 0)

            val params = rowData[0].tag as ParamTagXlsx

            buildParams(paramContainer, params.params, vars) {
                if(newFile == null) {
                    resetAllBuild(params.params)
                }

                processData(1)

                paramContainer.afterReportCreated(newFile!!)
                newFile = null
            }
        }
    }

    fun requestParamAutoTest(paramContainer: ParamContainer) {
        checkErrorByRow(0, paramContainer) {
            if(rowData.isEmpty() || rowData[0].tag !is ParamTagXlsx) throw Exception("не найдены параметры")

            buildRow(rowData[0], 0)

            val params = rowData[0].tag as ParamTagXlsx

            buildParams(paramContainer, params.params, vars) {}

            if(newFile == null) {
                resetAllBuild(params.params)
            }
            processData(1)

            paramContainer.afterReportCreated(newFile!!)
            newFile = null
        }
    }

    private fun resetAllBuild(params: List<Param>) {
        initNewBook()

        val cursorVars = findCursorParams(params, vars)
        vars.clear()

        vars.addAll(cursorVars)

        for(param in params) {
            vars.add(param.varParam)
        }

        initRowData(vars)
    }

    private fun findCursorParams(params: List<Param>, vars: MutableList<Var>): List<Var> {

        val cursorVarList: ArrayList<Var> = ArrayList()

        for(param in params) {
            if(param.cursor == null) continue
            param.cursor.reInitRow()
            val cursorVar = vars.firstOrNull { it.result.value === param.cursor } ?: continue

            cursorVarList += cursorVar
        }
        return cursorVarList
    }

    private fun initNewBook() {
        newFile = generateNewFile(template)

        newBook = createNewBook(template)

        sheet = newBook.getSheetAt(0)
    }

    private fun processData(startRowIndex: Int = 0) {

        executeData(startRowIndex)

        parser.rollbackAfterExec()

        for (columnIndex in 0 until DATA_COLUMN)  sheet.setColumnHidden(columnIndex, true)

        sheet.getRow(0)?.height = 15

        saveBook()
    }

    private fun executeData(startRowIndex: Int = 0, paramContainer: ParamContainer? = null) {
        if(startRowIndex >= rowData.size) return

        var diffRow = 0

        for(index in startRowIndex until rowData.size) {
            checkErrorByRow(index, paramContainer) {
                val row =  rowData[index]
                diffRow = buildRow(row, diffRow)
            }
        }
    }

    private fun buildRow(row: RowXlsx, diffRow: Int): Int {
        return  when(row.tag) {
            EmptyTagXlsx -> buildEmpty(row, diffRow)
            is LoopTagXlsx -> buildLoop(row.tag, row, diffRow)
            is IfTagXlsx -> buildIf(row.tag.exprIf, row, diffRow)
            is ParamTagXlsx -> buildEmpty(row, diffRow)
            is SubLoopIfTagXlsx -> buildIf(row.tag.exprIf, row, diffRow)
            is SubLoopLoopTagXlsx -> buildSubLoop(row, diffRow)
        }
    }

    private fun buildSubLoop(row: RowXlsx, diffRow: Int): Int {
        return buildEmpty(row, diffRow)
    }

    private fun buildIf(exprIf: Expression, row: RowXlsx, diffRow: Int): Int {

        val isExec = parser.execExpression(exprIf, false).toBoolean()

        if(!isExec) return removeRowIf(row, diffRow)

        parser.execExpression(row.expr, false)

        return buildEmpty(row, diffRow)
    }

    private fun buildLoop(loopTag: LoopTagXlsx, row: RowXlsx, diffRow: Int): Int {

        if(loopTag.cursor.isEmpty()) {
            return removeRowIf(row, diffRow).apply {

                loopTag.subLoop?.let { removeRow(this) }

                removeSubIf(loopTag.subIfRows, this)
            }
        }

        var rowIndex = row.index + diffRow

        parser.execExpression(row.expr, false)

        var isFirst = true
        do {
            val isDrawMainLoop = loopTag.exprIf?.let {
                if(it.isNotEmpty() ) parser.execExpression(it, false).toBoolean() else true
            } ?: true

            if(isDrawMainLoop) {
                if(!isFirst) {
                    sheet.newRowFromSource(rowIndex)
                    rowIndex++
                }
                buildDefaultRow(row, rowIndex)
            }

            rowIndex = buildSubIfLoop(loopTag.subIfRows, rowIndex, isFirst)

            rowIndex = buildSubLoopLoop(loopTag.subLoop, rowIndex, isFirst)

            isFirst = false

            val isNext = loopTag.cursor.isNext()
            if(isNext) {
                parser.execExpression(row.expr, false)
            }
        } while( isNext )

        return rowIndex - row.index - loopTag.subIfRows.size
    }

    private fun buildSubLoopLoop(subLoopRow: RowXlsx?, rowIndex: Int, isFirstRun: Boolean): Int {

        if(subLoopRow == null) return rowIndex

        var selectedIndex = rowIndex

        val cursorSubLoop = (subLoopRow.tag as SubLoopLoopTagXlsx).cursor

        if(isFirstRun && (cursorSubLoop.isEmpty() || cursorSubLoop.data.size < 2)) {
            removeRow(selectedIndex + 1)
            return rowIndex
        }

        var isFirst = isFirstRun

        var isNext = cursorSubLoop.isNext()
        while(isNext) {
            selectedIndex++

            if(!isFirst) {
                sheet.newRowFromSource(selectedIndex-1)
             }
            buildDefaultRow(subLoopRow, selectedIndex)

            isFirst = false

            isNext = cursorSubLoop.isNext()
        }

        return selectedIndex
    }

    private fun buildSubIfLoop(subIfRows: List<RowXlsx>, rowIndex: Int, isFirstRun: Boolean): Int {
        if(subIfRows.isEmpty()) return rowIndex

        var selectedIndex = rowIndex + 1

        for(ifRow in subIfRows) {

            parser.execExpression(ifRow.expr, false)

            val exprIf = (ifRow.tag as SubLoopIfTagXlsx).exprIf
            val isExec = parser.execExpression(exprIf, false).toBoolean()
            if(!isExec) {
                if(isFirstRun) {
                    removeRow(selectedIndex)
                }
                continue
            }

            if(!isFirstRun) {
                sheet.newRowFromSource(selectedIndex - 1)
            }

            buildDefaultRow(ifRow, selectedIndex)
            selectedIndex++
        }

        return selectedIndex - 1
    }

    private fun removeSubIf(subIfRows: List<RowXlsx>, rowIndex: Int) {
        for(ifRow in subIfRows) {
            removeRow(rowIndex)
        }
    }

    private fun removeRowIf(row: RowXlsx, diffRow: Int): Int {
        removeRow(row.index + diffRow)

        return diffRow - 1
    }

    private fun removeRow(indexRow: Int) {

        val row = sheet.getRow(indexRow) ?: return

        sheet.removeRow(row)
    }

    private fun buildEmpty(row: RowXlsx, diffRow: Int): Int {

        parser.execExpression(row.expr, false)

        buildDefaultRow(row, row.index + diffRow)

        return diffRow
    }

    private fun saveBook() {
        FileOutputStream(newFile!!).use {
            newBook.write(it)
        }
        newBook.close()
    }

    private fun getColumns(row: Row): List<ColXlsx> {
        val columns = ArrayList<ColXlsx>()

        val cols = row.iterator()

        while (cols.hasNext()) {
            val colCell = cols.next()

            if (colCell.columnIndex < DATA_COLUMN) continue

            val format = colCell.cellStyle

            val columnContent = parseColumnContent(colCell)
            if(colCell.cellType == CellType.STRING) {
                colCell.setCellValue("")
            }

            columns += ColXlsx(colCell.columnIndex, format, columnContent)
        }

        return columns
    }

    private fun parseColumnContent(cell: Cell): ColumnContent {
        if(cell.isBlankOrEmpty()) return EmptyContent

        if(cell.cellType == CellType.NUMERIC) return NumberContent(cell.numericCellValue)

        val content = cell.stringCellValue ?: return EmptyContent

        var openVar = content.indexOf(OPEN_VAR)

        if(openVar < 0) return StringContent(content)

        val varList = ArrayList<ReturnResult>()

        var index = 0

        while(openVar >= 0) {
            if(openVar > index) {
                val text = content.substring(index until openVar)
                varList += VarResult(VarType.VARCHAR, text)
            }

            val closeVar = content.indexOf(CLOSE_VAR, openVar)
            if(closeVar < 0) throw Exception("symbol ']' not found for content:$content index:$openVar")

            val varName = content.substring(openVar..closeVar)

            val expr = parser.parseExpression(varName, vars)

            if(expr.isNotEmpty() ) {
                varList += expr[expr.lastIndex]
            }
            index = closeVar + 1
            openVar = content.indexOf(OPEN_VAR, index)
        }

        if(index < content.length) {
            val text = content.substring(index)
            varList += VarResult(VarType.VARCHAR, text)
        }

        return if(varList.size > 1) {
            ComplexContent(varList)
        } else {
            VarContent(varList[0])
        }
    }

    private fun getExpression(row: Row): Expression {

        val formulaCell = row.getCell(FORMULA_COLUMN) ?: return emptyList()

        if(formulaCell.cellType == CellType.STRING) {

            val content = formulaCell.stringCellValue

            formulaCell.setCellValue("")

            return parser.parseExpression(content, vars)
        }
        return emptyList()
    }

    private fun getTagRow(row: Row): TagXlsx {

        val tagCell = row.getCell(TAG_COLUMN)

        val tagValue = tagCell?.takeIf { it.cellType == CellType.STRING }?.stringCellValue ?: ""

        if(tagCell?.cellType == CellType.STRING) {
            tagCell.setCellValue("")
        }

        return if(tagValue.isBlank() ) {
            val subTagCell = row.getCell(SUBTAG_COLUMN)

            val subContent = subTagCell?.takeIf { it.cellType == CellType.STRING }?.stringCellValue ?: ""

            getTagByName(subContent, row, subContent.isNotBlank())
        } else {
            getTagByName(tagValue, row)
        }
    }

    private fun getTagByName(name: String, row: Row, isSubTag: Boolean = false): TagXlsx {
        val tagName = name.substringBefore(' ').trim().uppercase(Locale.getDefault())

        if(tagName.isBlank() || tagName == EmptyTagXlsx.nameTag) return EmptyTagXlsx

        return when(tagName) {
            LOOP -> if (isSubTag) SubLoopLoopTagXlsx(findCursor(name)) else LoopTagXlsx(findCursor(name), exprIf = loopIfExpr(row))
            IF -> if (isSubTag) SubLoopIfTagXlsx(parseExpr(name)) else IfTagXlsx(parseExpr(name))
            PARAM -> ParamTagXlsx(fillParams(row))
            else -> throw Exception("TAG not found $name")
        }
    }

    private fun fillParams(row: Row): List<Param> {

        val paramCell = row.getCell(PARAM_COLUMN)

        val paramValue = paramCell?.takeIf { it.cellType == CellType.STRING }?.stringCellValue ?: ""

        if(paramValue.isNotBlank()) {
            paramCell?.setCellValue("")
        }

        if(paramValue.isBlank()) return emptyList()

        return parseParams(paramValue, vars)
    }

    private fun findCursor(name: String): CursorData {
        val cursorName = name.substringAfter(' ').trim().uppercase(Locale.getDefault())

        val cursor = vars.firstOrNull { it.name == cursorName } ?: throw Exception("for tag LOOP cursor not found: $cursorName")

        if(cursor.result.type != VarType.CURSOR) throw Exception("LOOP var is not cursor: $cursorName")

        return cursor.result.value as CursorData
    }

    private fun loopIfExpr(row: Row): Expression? {

        val ifExprCell = row.getCell(SUBTAG_COLUMN) ?: return null

        val ifExprCellValue = ifExprCell.takeIf { it.cellType == CellType.STRING  && it.stringCellValue.isNotBlank()}
            ?.stringCellValue ?: return null

        return parseExpr(ifExprCellValue)
    }

    private fun parseExpr(name: String): Expression {
        val expression = name.substringAfter(' ').trim()
        return parser.parseExpression(expression, vars)
    }

    private fun checkErrorByRow(rowIndex: Int, paramContainer: ParamContainer? = null, process: () -> Unit) {
        try {
            process()
        } catch (e: Exception) {
            logger.error("rowIndex=$rowIndex", e)

            parser.rollbackAfterExec()

            val error = "Ошибка в строке = ${rowIndex + 1}\n ${e.message}"

            paramContainer?.reportError(error, newFile)

            throw Exception(error)
        }
    }

    private fun buildDefaultRow(row: RowXlsx, rowIndex: Int) {

        var isFirst = true

        for (column in row.columns) {

            val rowCells = sheet.getRow(rowIndex) ?: sheet.createRow(rowIndex)
            if(isFirst && row.height != null) {
                isFirst = false
                rowCells.height = row.height!!
            }

            column.setContentByRow(rowCells)
        }
    }
}

private fun Cell.isBlankOrEmpty(): Boolean = when(cellType) {
    CellType.BLANK -> true
    CellType.STRING -> stringCellValue?.isBlank() ?: true
    else -> false
}

private fun createNewBook(templateFile: File): Workbook {

    return try {

        FileInputStream(templateFile).use {
            XSSFWorkbook(it)
        }
    } catch (e: Exception) {

        logger.error("createNewBook", e)

        throw Exception(e.message)
    }
}

data class RowXlsx(
    val tag: TagXlsx,
    val index: Int,
    val expr: Expression,
    val columns: List<ColXlsx> = emptyList(),
    var height: Short? = null
)

data class ColXlsx(
    val index: Int,
    val format: CellStyle,
    val value: ColumnContent
) {

    fun setContentByRow(rowCell: Row) {

        val cell = rowCell.getCell(index) ?: rowCell.createCell(index)

        cell.cellStyle = format

        when(value) {
            EmptyContent -> cell.setBlank()
            is StringContent -> if (value.content.isEmpty()) cell.setBlank() else cell.setCellValue(value.content)
            is NumberContent -> cell.setCellValue(value.number)
            is VarContent -> cell.setVarByType(value.varResult)
            is ComplexContent -> cell.setComplexType(value.varList)
        }
    }
}

sealed class TagXlsx(val nameTag: String)

object EmptyTagXlsx : TagXlsx(EMPTY)

data class IfTagXlsx(val exprIf: Expression) : TagXlsx(IF)

data class SubLoopIfTagXlsx(val exprIf: Expression) : TagXlsx(SUB_IF)

data class SubLoopLoopTagXlsx(val cursor: CursorData) : TagXlsx(SUB_LOOP)

data class ParamTagXlsx(val params: List<Param>) : TagXlsx(PARAM)

data class LoopTagXlsx(val cursor: CursorData, var subIfRows: List<RowXlsx> = emptyList(),
                       val exprIf: Expression?, var subLoop: RowXlsx? = null) : TagXlsx(LOOP) {

    fun addSubIf(subIfRow: RowXlsx) {
        if(subIfRows.isEmpty()) {
            subIfRows = ArrayList()
        }
        subIfRows += subIfRow
    }
}

private fun Cell.setComplexType(varList: List<ReturnResult>) {
    if(varList.isEmpty() ) {
        this.setBlank()
        return
    }

    val text = varList.joinToString(separator = "") {it.getVar().value?.toString()?:"" }

    this.setCellValue(text)
}

private fun Cell.setVarByType(varResult: ReturnResult) {
    if(varResult.getVar().value == null) {
        this.setBlank()
        return
    }

    when(varResult.getVar().type) {
        VarType.UNDEFINED -> setBlank()
        VarType.INT,
        VarType.NUMBER -> this.setCellValue((varResult.getVar().value as Number).toDouble())
        VarType.VARCHAR -> this.setCellValue((varResult.getVar().value).toString())
        VarType.DATE -> this.setCellValue(/*byFormatDate(*/varResult.getVar().value as Date/*)*/)
        else -> this.setBlank()
    }
}

/**
 * вставляет новую строку - строка вставляется вверх, а не вниз,
 * поэтому с нижней строки (там были осходные данные копируем их наверх
 * если нужно потом в нижней строке зачищаем данные
 */
private fun Sheet.newRowFromSource(srcRowIndex: Int) {

    val mergedRow = mergedRegionsByRow(srcRowIndex)

    val last = this.lastRowNum

    try {

        if(srcRowIndex < this.lastRowNum) {
            this.shiftRows(srcRowIndex+1, this.lastRowNum, 1)
        }/* else {
             logger.error("FAIL shiftRows srcRowIndex=$srcRowIndex lastRowNum= $last")
        }*/

        this.createRow(srcRowIndex+1)

        val newSourceIndex = srcRowIndex + 1

        val readRow = this.getRow(srcRowIndex) ?: return

        val newRow = this.getRow(newSourceIndex) ?: return

        for(readCell in readRow.cellIterator() ) {

            val newCel = newRow.createCell(readCell.columnIndex, readCell.cellType)

            newCel.cellStyle = readCell.cellStyle
        }

        if(mergedRow.isEmpty() ) return

        for(readCell in readRow.cellIterator() ) {
            mergedRow.firstOrNull { it.firstColumn == readCell.columnIndex }?.let {
                this.addMergedRegion(CellRangeAddress(newSourceIndex, newSourceIndex, it.firstColumn, it.lastColumn) )
            }
        }
    } catch (e: Exception) {

        logger.error("shiftRows srcRowIndex=$srcRowIndex lastRowNum= $last")

        throw IllegalArgumentException(e)
    }
}

private fun Sheet.mergedRegionsByRow(rowIndex: Int): List<CellRangeAddress> =
    mergedRegions.filter { it.firstRow == rowIndex && rowIndex == it.lastRow }

private fun Row.isCellsMerged(): Boolean {

    return sheet.mergedRegions.firstOrNull { it.firstRow == this.rowNum || it.lastRow == this.rowNum } != null
}
