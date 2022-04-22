package ru.barabo.xls

import jxl.Workbook
import jxl.format.CellFormat
import jxl.write.*
import jxl.write.Number
import org.slf4j.LoggerFactory
import ru.barabo.db.Query
import java.awt.Container
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.JTabbedPane
import kotlin.collections.ArrayList

interface ExcelSql {
    fun buildWithrequestParam(vars: MutableList<Var>, paramContainer: ParamContainer)

    fun initRowData(vars: MutableList<Var>)

    fun buildWithOutParam(vars: MutableList<Var>)

    val newFile: File?
}

class ExcelSqlJxls(private val template: File, query: Query, private val generateNewFile:(File)->File) : ExcelSql {

    private lateinit var newBook: WritableWorkbook

    override var newFile: File? = null
    private set

    private lateinit var sheet: WritableSheet

    private lateinit var rowData: List<Row>

    private lateinit var vars: MutableList<Var>

    private val parser: Parser = Parser(query)

    override fun buildWithOutParam(vars: MutableList<Var>) {
        initRowData(vars)

        processData()
    }

    override fun buildWithrequestParam(vars: MutableList<Var>, paramContainer: ParamContainer) {
        initRowData(vars)

        requestParam(paramContainer)
    }

    private fun requestParam(paramContainer: ParamContainer) {
        checkErrorByRow(0, paramContainer) {
            if(rowData.isEmpty() || rowData[0].tag !is ParamTag) throw Exception("не найдены параметры")

            buildRow(rowData[0], 0)

            val params = rowData[0].tag as ParamTag

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

    private fun processData(startRowIndex: Int = 0) {

        executeData(startRowIndex)

        parser.rollbackAfterExec()

        for (columnIndex in 0 until DATA_COLUMN)  sheet.setColumnView(columnIndex, 0)

        newBook.save()
    }

    private fun initNewBook() {
        newFile = generateNewFile(template)

        newBook = createNewBook(newFile!!, template)

        sheet = newBook.getSheet(0)

        val scale = sheet.settings.scaleFactor
        sheet.settings.scaleFactor = scale
    }

    override fun initRowData(vars: MutableList<Var>) {
        initNewBook()

        this.vars =  vars

        stackFormat.clear()

        val rowData = ArrayList<Row>()

        var tagLoop : LoopTag? = null

        for (rowIndex in 0 until sheet.rows) {

            checkErrorByRow(rowIndex) {
                val expression = getExpression(rowIndex)

                val tag = getTagRow(rowIndex)

                val columns = getColumns(rowIndex)

                val row = Row(tag, rowIndex, expression, columns)

                when(tag) {
                    is LoopTag -> {
                        tagLoop = tag
                        rowData += row
                    }
                    is SubLoopIfTag -> {
                        tagLoop!!.addSubIf(row)
                    }
                    else -> { rowData += row }
                }
            }
        }
        this.rowData = rowData
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

    private fun checkErrorByRow(rowIndex: Int, paramContainer: ParamContainer? = null, process: ()->Unit ) {
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

    private val emptyWritableFormat = WritableCellFormat()

    private fun getColumns(rowIndex: Int): List<Col> {
        val columns = ArrayList<Col>()

        for (colIndex in DATA_COLUMN until sheet.columns) {

            val cell = sheet.getCell(colIndex, rowIndex)

            val format = cell.cellFormat?.let { getAddWriteFormat(it) /*WritableCellFormat(it)*/ } ?: emptyWritableFormat

            val columnContent = parseColumnContent(cell?.contents)

            if(cell is Label) {
                cell.string = ""
            }

            columns += Col(colIndex, format, columnContent)
        }
        return columns
    }

    private fun parseColumnContent(content: String?): ColumnContent {
        if(content == null || content.isBlank()) return EmptyContent

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

    private fun getExpression(rowIndex: Int): Expression {
        val cellContentTag = sheet.getCell(FORMULA_COLUMN, rowIndex)

        val content = cellContentTag.contents?.trim() ?: ""

        if(cellContentTag is Label) {
            cellContentTag.string = ""
        }

        return parser.parseExpression(content, vars)
    }

    private fun getTagRow(rowIndex: Int): Tag {
        val cellContentTag = sheet.getCell(TAG_COLUMN, rowIndex)

        val content = cellContentTag.contents?.trim() ?: ""

        if(cellContentTag is Label) {
            cellContentTag.string = ""
        }

        return if(content.isBlank() ) {
            val cellContentSubTag = sheet.getCell(SUBTAG_COLUMN, rowIndex)
            val subContent = cellContentSubTag.contents?.trim() ?: ""

            getTagByName(subContent, rowIndex, subContent.isNotBlank())
        } else {
            getTagByName(content, rowIndex)
        }
    }

    private fun buildRow(row: Row, diffRow: Int): Int {
        return  when(row.tag) {
            EmptyTag -> buildEmpty(row, diffRow)
            is LoopTag -> buildLoop(row.tag, row, diffRow)
            is IfTag -> buildIf(row.tag.exprIf, row, diffRow)
            is ParamTag -> buildEmpty(row, diffRow)
            is SubLoopIfTag -> buildIf(row.tag.exprIf, row, diffRow)
        }
    }

    private fun buildLoop(loopTag: LoopTag, row: Row, diffRow: Int): Int {

        if(loopTag.cursor.isEmpty()) {
            return removeRowIf(row, diffRow).apply { removeSubIf(loopTag.subIfRows, this) }
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
            isFirst = false

            val isNext = loopTag.cursor.isNext()
            if(isNext) {
                parser.execExpression(row.expr, false)
            }
        } while( isNext )

        return rowIndex - row.index - loopTag.subIfRows.size
    }

    private fun removeSubIf(subIfRows: List<Row>, rowIndex: Int) {
        for(ifRow in subIfRows) {
            sheet.removeRow(rowIndex)
        }
    }

    private fun buildSubIfLoop(subIfRows: List<Row>, rowIndex: Int, isFirstRun: Boolean): Int {
        if(subIfRows.isEmpty()) return rowIndex

        var selectedIndex = rowIndex + 1

        for(ifRow in subIfRows) {

            parser.execExpression(ifRow.expr, false)

            val exprIf = (ifRow.tag as SubLoopIfTag).exprIf
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

    private fun buildIf(exprIf: Expression, row: Row, diffRow: Int): Int {

        val isExec = parser.execExpression(exprIf, false).toBoolean()

        if(!isExec) return removeRowIf(row, diffRow)

        parser.execExpression(row.expr, false)

        return buildEmpty(row, diffRow)
    }

    private fun removeRowIf(row: Row, diffRow: Int): Int {
        sheet.removeRow(row.index + diffRow)

        return diffRow - 1
    }

    private fun removeRow(indexRow: Int) {

        sheet.removeRow(indexRow)
    }

    private fun buildEmpty(row: Row, diffRow: Int): Int {

        parser.execExpression(row.expr, false)

        buildDefaultRow(row, row.index + diffRow)

        return diffRow
    }

    private fun buildDefaultRow(row: Row, rowIndex: Int) {
        for (column in row.columns) {

            val writeCell = column.contentValue(rowIndex)

            sheet.addCell(writeCell)
        }
    }

    private fun getTagByName(name: String, rowIndex: Int, isSubTag: Boolean = false): Tag {
        val tagName = name.substringBefore(' ').trim().uppercase()

        if(tagName.isBlank() || tagName == EmptyTag.nameTag) return EmptyTag

        return when(tagName) {
            LOOP -> LoopTag(findCursor(name), exprIf = loopIfExpr(rowIndex) )
            IF -> if(isSubTag) SubLoopIfTag(parseExpr(name)) else IfTag(parseExpr(name))
            PARAM -> ParamTag( fillParams(rowIndex) )
            else -> throw Exception("TAG not found $name")
        }
    }

    private fun loopIfExpr(rowIndex: Int): Expression? {
        val cellIfExpr = sheet.getCell(SUBTAG_COLUMN, rowIndex)

        val ifExpr = cellIfExpr.contents?.trim() ?: return null

        if(ifExpr.isBlank()) return null

        return parseExpr(ifExpr)
    }

    private fun fillParams(rowIndex: Int): List<Param> {
        val cellContentTag = sheet.getCell(PARAM_COLUMN, rowIndex)

        val content = cellContentTag.contents?.trim() ?: ""

        if(cellContentTag is Label) {
            cellContentTag.string = ""
        }

        if(content.isBlank()) return emptyList()

        return parseParams(content, vars)
    }

    private fun parseExpr(name: String): Expression {
        val expression = name.substringAfter(' ').trim()
        return parser.parseExpression(expression, vars)
    }

    private fun findCursor(name: String): CursorData {
        val cursorName = name.substringAfter(' ').trim().uppercase(Locale.getDefault())

        val cursor = vars.firstOrNull { it.name == cursorName } ?: throw Exception("for tag LOOP cursor not found: $cursorName")

        if(cursor.result.type != VarType.CURSOR) throw Exception("LOOP var is not cursor: $cursorName")

        return cursor.result.value as CursorData
    }
}

const val DATA_COLUMN = 3

const val TAG_COLUMN = 1

const val SUBTAG_COLUMN = 2

const val PARAM_COLUMN = 2

const val FORMULA_COLUMN = 0

private val logger = LoggerFactory.getLogger(ExcelSqlJxls::class.java)

private val stackFormat: ArrayList<WritableCellFormat> = ArrayList()

private fun getAddWriteFormat(cellFormat: CellFormat): WritableCellFormat =
    stackFormat.firstOrNull { cellFormat.isEqualCellFormat(it) }
        ?:  WritableCellFormat(cellFormat).apply { stackFormat += this }

private fun CellFormat.isEqualCellFormat(cellFormat: CellFormat): Boolean {

    return format?.formatString == cellFormat.format?.formatString &&
           font.isEqual(cellFormat.font) &&
           wrap == cellFormat.wrap &&
           alignment?.value == cellFormat.alignment?.value && alignment?.description == cellFormat.alignment?.description &&
           verticalAlignment?.value == cellFormat.verticalAlignment?.value && verticalAlignment?.description == cellFormat.verticalAlignment?.description &&
           orientation?.value == cellFormat.orientation?.value && orientation?.description == cellFormat.orientation?.description &&
           hasBorders()  == cellFormat.hasBorders() &&
           (!hasBorders() || (isEqualColorBorder(cellFormat) &&  isEqualBorder(cellFormat) && isEqualgetBorderLine(cellFormat) ) ) &&
           backgroundColour.isEqual(cellFormat.backgroundColour) &&
           pattern?.value == cellFormat.pattern?.value && pattern?.description == cellFormat.pattern?.description &&
           indentation  == cellFormat.indentation &&
           isShrinkToFit == cellFormat.isShrinkToFit &&
           isLocked == cellFormat.isLocked
}

private fun CellFormat.isEqualBorder(cellFormat: CellFormat): Boolean {
    return getBorder(jxl.format.Border.LEFT).isEqual(cellFormat.getBorder(jxl.format.Border.LEFT) ) &&
            getBorder(jxl.format.Border.TOP).isEqual(cellFormat.getBorder(jxl.format.Border.TOP) ) &&
            getBorder(jxl.format.Border.RIGHT).isEqual(cellFormat.getBorder(jxl.format.Border.RIGHT) ) &&
            getBorder(jxl.format.Border.BOTTOM).isEqual(cellFormat.getBorder(jxl.format.Border.BOTTOM) )
}

private fun CellFormat.isEqualgetBorderLine(cellFormat: CellFormat): Boolean {
    return getBorderLine(jxl.format.Border.LEFT).isEqual(cellFormat.getBorderLine(jxl.format.Border.LEFT) ) &&
            getBorderLine(jxl.format.Border.TOP).isEqual(cellFormat.getBorderLine(jxl.format.Border.TOP) ) &&
            getBorderLine(jxl.format.Border.RIGHT).isEqual(cellFormat.getBorderLine(jxl.format.Border.RIGHT) ) &&
            getBorderLine(jxl.format.Border.BOTTOM).isEqual(cellFormat.getBorderLine(jxl.format.Border.BOTTOM) )
}

private fun jxl.format.BorderLineStyle?.isEqual(border: jxl.format.BorderLineStyle?): Boolean {
    if(this === border) return true

    if(this == null || border == null) return false

    return value == border.value && description == border.description
}

private fun CellFormat.isEqualColorBorder(cellFormat: CellFormat): Boolean {
    return getBorderColour(jxl.format.Border.LEFT).isEqual(cellFormat.getBorderColour(jxl.format.Border.LEFT) ) &&
            getBorderColour(jxl.format.Border.TOP).isEqual(cellFormat.getBorderColour(jxl.format.Border.TOP) ) &&
            getBorderColour(jxl.format.Border.RIGHT).isEqual(cellFormat.getBorderColour(jxl.format.Border.RIGHT) ) &&
            getBorderColour(jxl.format.Border.BOTTOM).isEqual(cellFormat.getBorderColour(jxl.format.Border.BOTTOM) )
}


private fun jxl.format.Colour?.isEqual(color: jxl.format.Colour?): Boolean {
    if(this === color) return true

    if(this == null || color == null) return false

    return value == color.value && defaultRGB?.red == color.defaultRGB?.red &&
            defaultRGB?.green == color.defaultRGB?.green && defaultRGB?.blue == color.defaultRGB?.blue
}

private fun jxl.format.Font?.isEqual(font: jxl.format.Font?): Boolean {
    if(this === font) return true

    if(this == null || font == null) return false

    return name == font.name &&
           pointSize == font.pointSize &&
           boldWeight ==  font.boldWeight &&
           isItalic == font.isItalic &&
           isStruckout == font.isStruckout &&
           underlineStyle?.value == font.underlineStyle?.value &&
           colour.isEqual(font.colour) &&
           scriptStyle?.value == font.scriptStyle?.value && scriptStyle?.description == font.scriptStyle?.description
}

data class Row(val tag: Tag,
               val index: Int,
               val expr: Expression,
               val columns: List<Col> = emptyList())

data class Col(val index: Int,
               val format: WritableCellFormat,
               val value: ColumnContent) : ColumnValue {

    override fun contentValue(rowIndex: Int): WritableCell {
        return when(value) {
            EmptyContent -> Blank(index, rowIndex, format)
            is StringContent -> if(value.content.isEmpty() )Blank(index, rowIndex, format) else Label(index, rowIndex, value.content, format)
            is NumberContent -> Number(index, rowIndex, value.number, format)
            is VarContent -> varByType(value.varResult, rowIndex)
            is ComplexContent -> complexType(value.varList, rowIndex)
        }
    }

    private fun complexType(varList: List<ReturnResult>, rowIndex: Int): WritableCell {
        if(varList.isEmpty()) return Blank(index, rowIndex, format)

        val text = varList.joinToString(separator = "") {it.getVar().value?.toString()?:"" }

        return Label(index, rowIndex, text, format)
    }

    private fun varByType(varResult: ReturnResult, rowIndex: Int): WritableCell {
        if(varResult.getVar().value == null) return Blank(index, rowIndex, format)

        return when(varResult.getVar().type) {
            VarType.UNDEFINED -> Blank(index, rowIndex, format)
            VarType.INT,
            VarType.NUMBER -> Number(index, rowIndex, (varResult.getVar().value as kotlin.Number).toDouble() , format)
            VarType.VARCHAR -> Label(index, rowIndex, (varResult.getVar().value).toString(), format)

            VarType.DATE -> DateTime(index, rowIndex, varResult.getVar().value as? Date, format) // Label(index, rowIndex, byFormatDate(varResult.getVar().value as Date), format)

            else -> Blank(index, rowIndex, format)
        }
    }
}

private val PATTERN_DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

private val PATTERN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

sealed class Tag(val nameTag: String)

object EmptyTag : Tag(EMPTY)

data class LoopTag(val cursor: CursorData, var subIfRows: List<Row> = emptyList(), val exprIf: Expression?) : Tag(LOOP) {

    fun addSubIf(subIfRow: Row) {
        if(subIfRows.isEmpty()) {
            subIfRows = ArrayList()
        }
        subIfRows += subIfRow
    }
}

data class IfTag(val exprIf: Expression) : Tag(IF)

data class SubLoopIfTag(val exprIf: Expression) : Tag(SUB_IF)

data class ParamTag(val params: List<Param>) : Tag(PARAM)

internal const val EMPTY = "EMPTY"

internal const val LOOP = "LOOP"

internal const val IF = "IF"

internal const val SUB_IF = "SUBIF"

internal const val SUB_LOOP = "SUBLOOP"

internal const val PARAM = "PARAM"

interface ColumnValue {

    fun contentValue(rowIndex: Int): WritableCell
}

sealed class ColumnContent

object EmptyContent : ColumnContent()

data class StringContent(val content: String) : ColumnContent()

data class NumberContent(val number: Double) : ColumnContent()

data class VarContent(val varResult: ReturnResult) : ColumnContent()

data class ComplexContent(val varList: List<ReturnResult>) : ColumnContent()

/**
 * вставляет новую строку - строка вставляется вверх, а не вниз,
 * поэтому с нижней строки (там были осходные данные копируем их наверх
 * если нужно потом в нижней строке зачищаем данные
 */
private fun WritableSheet.newRowFromSource(srcRowIndex: Int, isClearCopyData: Boolean = false) {

    this.insertRow(srcRowIndex)

    val newSourceIndex = srcRowIndex + 1

    for (col in 1 until columns) {
        val readCell = getWritableCell(col, newSourceIndex)

        val newCell = readCell.copyTo(col, srcRowIndex)

        readCell.cellFormat?.let {

            newCell.cellFormat = getAddWriteFormat(it) // WritableCellFormat(it)
        }

        if(isClearCopyData) {
            if(readCell is Label) {
                readCell.string = ""
            }
        }

        this.addCell(newCell)
    }
}

interface ParamContainer {
    val container: Container

    val bookForTableBox: JTabbedPane?

    fun afterParamCreate() {}

    fun afterReportCreated(reportFile: File)

    fun reportError(error: String, reportFile: File?) {}

    fun checkRunReport() {}
}

fun byFormatDate(date: Date): String {

    val localDateTime = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

    val pattern = if(localDateTime.hour == 0 && localDateTime.minute == 0) PATTERN_DATE else PATTERN_DATE_TIME

    return pattern.format(localDateTime)
}

fun createNewBook(newFile: File, templateFile: File): WritableWorkbook {

    var templateBook: Workbook? = null

    try {
        templateBook = Workbook.getWorkbook(templateFile)

        val newBook = Workbook.createWorkbook(newFile, templateBook)

        templateBook.close()

        return newBook
    } catch (e: Exception) {

        logger.error("createNewBook", e)

        templateBook?.close()

        throw Exception(e.message)
    }
}

fun WritableWorkbook.save() {
    write()

    close()
}