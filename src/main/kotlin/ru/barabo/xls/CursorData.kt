package ru.barabo.xls

import ru.barabo.db.Query
import ru.barabo.db.SessionSetting
import kotlin.concurrent.thread

class CursorData(private val querySession: QuerySession, private val querySelect: String,
                 val params: List<ReturnResult> = emptyList() ) {

    var data: List<Array<Any?>> = emptyList()

    var row: Int = 0
        private set

    var columns: List<String> = emptyList()
        private set

    var sqlColumnType: List<Int> = emptyList()
        private set

    var isOpen: Boolean = false
        private set

    private val columnResult = ArrayList<ColumnResult>()

    private var listeners: MutableList<CursorDateListener>? = null

    private var lastParam: Array<Any?>? = null

    @Volatile var isBusy: Boolean = false
        private set

    @Volatile private var isMustUpdate: Boolean = false

    fun setRowIndex(index: Int): Int? {
        if(index < 0 || index >= data.size) return null

        row = index

        return row
    }

    fun addListener(listener: CursorDateListener) {
        val listenerList = listeners ?: ArrayList<CursorDateListener>().apply { listeners = this }

        listenerList.add(listener)
    }

    fun invalidate() {

        val priorParam = lastParam

        val newParams: Array<Any?>? = if(params.isEmpty()) null else params.map { it.getSqlValue() }.toTypedArray()

        if(priorParam.isEqualParam(newParams)) return

        if(isBusy) {
            isMustUpdate = true
            return
        }

        isBusy = true
        thread {
            run {
                open()
            }
        }
    }

    private fun sendListenerInvalidate() {

        listeners?.forEach { it.changeData() }

        isBusy = false

        if(isMustUpdate) {
            isMustUpdate = false
            invalidate()
        }
    }

    fun getColumnResult(columnName: String): ReturnResult {
        return columnResult.firstOrNull { it.columnName == columnName }
            ?: ColumnResult(this, columnName).apply { columnResult += this }
    }

    fun reInitRow() {
        row = 0
    }

    fun isNext(): Boolean {
        return if(row + 1 < data.size) {
            row++

            true
        } else false
    }

    fun isEmpty(): Boolean {
        if(!isOpen) {
            isOpen = open()
        }
        return data.isEmpty()
    }

    fun toSqlValue(index: Int, funIndex: Int): Any {
        if(!isOpen) {
            isOpen = open()
        }

        if(index < 0 || funIndex != UNINITIALIZE_COLUMN_INDEX) {
            return funCursorValue(index, funIndex)
        }

        if(row >= data.size) throw Exception("cursor position is end")

        return data[row][index] ?: sqlColumnType[index].toSqlValueNull()
    }

    fun getVarResult(index: Int, funIndex: Int = UNINITIALIZE_COLUMN_INDEX): VarResult {
        if(!isOpen) {
            isOpen = open()
        }

        if(index < 0 || funIndex != UNINITIALIZE_COLUMN_INDEX) {
            return funCursorValue(index, funIndex)
        }

        if(row >= data.size) {

            val pars = params.joinToString { "${it.getSqlValue()}" }

            throw Exception("cursor position is end data.size=${data.size} querySelect=${querySelect} param=$pars")
        }

        val value = data[row][index]

        val type = VarType.varTypeBySqlType(sqlColumnType[index])

        return VarResult(type = type, value = value)
    }

    private fun funCursorValue(index: Int, funIndex: Int): VarResult {

        val findIndex = if(index < 0) index else funIndex

        val cursorFun = CursorFun.byIndex(findIndex) ?: throw Exception("cursor fun is not found index=$index")

        return cursorFun.func.invoke(this, index)
    }

    internal fun getColumnIndex(columnName: String): Pair<Int, Int> {
        if(!isOpen) {
            isOpen = open()
        }

        val column = columnName.substringBefore('.')

        val funColumn = columnName.substringAfter('.', "")

        val columnIndex = columnIndexByName(column)

        val funIndex = if(funColumn.isBlank()) UNINITIALIZE_COLUMN_INDEX else columnIndexByName(funColumn)

        return Pair(columnIndex, funIndex)
    }

    private fun columnIndexByName(columnName: String): Int {
        return CursorFun.byColumn(columnName)?.index ?:
        columns.withIndex().firstOrNull { it.value.equals(columnName, true) }?.index ?:
        throw Exception("not found column for cursor .$columnName")
    }

    private fun open(): Boolean {

        lastParam = if(params.isEmpty()) null else params.map { it.getSqlValue() }.toTypedArray()

        val allData = if(isCursor()) {
            querySession.query.selectCursorWithMetaData(querySelect, lastParam, querySession.sessionSetting)
        } else {
            querySession.query.selectWithMetaData(querySelect, lastParam, querySession.sessionSetting)
        }

        data = allData.data
        columns = allData.columns

        sqlColumnType = allData.types
        row = 0

        sendListenerInvalidate()

        return true
    }

    fun findRowByRecord(record: Record): Int? {

        val indexColumnFind = columns.indices.firstOrNull {
            record.columns[it].result.type != VarType.UNDEFINED &&
            record.columns[it].result.value != null
        } ?: return null

        val findValue = record.columns[indexColumnFind].result.value!!
        val findType = record.columns[indexColumnFind].result.type

        return data.indices.firstOrNull {
            data[it][indexColumnFind]  != null && (
                    data[it][indexColumnFind] == findValue ||
                    findType.isEqualVal(findValue, data[it][indexColumnFind]!! )
            )
        }
    }

    fun setRecordByRow(record: Record, rowIndex: Int) {
        if(rowIndex < 0 || rowIndex >= data.size) {
            record.clearData()
            return
        }

        for(index in columns.indices) {
            record.columns[index].result.type = VarType.varTypeBySqlType(sqlColumnType[index])
            record.columns[index].result.value = data[rowIndex][index]
        }
    }

    fun record(@Suppress("UNUSED_PARAMETER") columnIndex: Int): VarResult {

        val columnsRecord = columns.withIndex().map { Var(it.value , getVarResult(it.index)) }.toMutableList()

        return VarResult(VarType.RECORD, Record(columnsRecord))
    }

    fun emptyRecord(@Suppress("UNUSED_PARAMETER") columnIndex: Int) =
        VarResult(VarType.RECORD, Record(columns.map { Var(it, VarResult()) }.toMutableList()) )

    private fun isCursor() = querySelect[0] == '{'

    fun row(@Suppress("UNUSED_PARAMETER") columnIndex: Int) = VarResult(VarType.INT, row + 1)

    fun next(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 1, data.size - 1))

    fun next2(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 2, data.size - 1))

    fun next3(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 3, data.size - 1))

    fun next4(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 4, data.size - 1))

    fun next5(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 5, data.size - 1))

    fun next6(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 6, data.size - 1))

    fun next7(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 7, data.size - 1))

    fun next8(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 8, data.size - 1))

    fun next9(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 9, data.size - 1))

    fun next10(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 10, data.size - 1))

    fun next11(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 11, data.size - 1))

    fun next12(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 12, data.size - 1))

    fun next13(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 13, data.size - 1))

    fun next14(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 14, data.size - 1))

    fun next15(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 15, data.size - 1))

    fun next16(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 16, data.size - 1))

    fun next17(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 17, data.size - 1))

    fun next18(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 18, data.size - 1))

    fun next19(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 19, data.size - 1))

    fun next20(columnIndex: Int) = cellByColumnRow(columnIndex, kotlin.math.min(row + 20, data.size - 1))

    fun reopen(@Suppress("UNUSED_PARAMETER") columnIndex: Int): VarResult {
        this.open()

        return VarResult()
    }


    private fun cellByColumnRow(columnIndex: Int, rowIndex: Int): VarResult {
        val value = data[rowIndex][columnIndex]

        val type = VarType.varTypeBySqlType(sqlColumnType[columnIndex])

        return VarResult(type = type, value = value)
    }

    fun sum(columnIndex: Int) = VarResult(VarType.NUMBER, data.sumOf { (it[columnIndex] as? Number)?.toDouble()?:0.0 })

    fun max(columnIndex: Int) = VarResult(VarType.NUMBER, data.map { (it[columnIndex] as? Number)?.toDouble()?:0.0 }.maxOrNull()?:0.0)

    fun min(columnIndex: Int) = VarResult(VarType.NUMBER,data.map { (it[columnIndex] as? Number)?.toDouble()?:0.0 }.minOrNull()?:0.0)

    fun count(@Suppress("UNUSED_PARAMETER") columnIndex: Int) = VarResult(VarType.INT, data.size)

    fun isEmptyFun(@Suppress("UNUSED_PARAMETER") columnIndex: Int) = VarResult(VarType.INT, if(isEmpty()) 1 else 0)

    fun isNotEmptyFun(@Suppress("UNUSED_PARAMETER") columnIndex: Int) = VarResult(VarType.INT, if(isEmpty()) 0 else 1)
}

interface CursorDateListener {
    fun changeData()
}

private enum class CursorFun(val index: Int, val funName: String, val func: CursorData.(columnIndex: Int) -> VarResult ) {
    ROW(-1, "ROW", CursorData::row),
    SUM(-2, "SUM", CursorData::sum),
    COUNT(-3, "COUNT", CursorData::count),
    ISEMPTY(-4, "ISEMPTY", CursorData::isEmptyFun),
    ISNOTEMPTY(-5, "ISNOTEMPTY", CursorData::isNotEmptyFun),
    MIN(-6, "MIN", CursorData::min),
    MAX(-7, "MAX", CursorData::max),
    EMPTYRECORD(-8, "EMPTYRECORD", CursorData::emptyRecord),
    RECORD(-9, "RECORD", CursorData::record),
    NEXT(-10, "NEXT", CursorData::next),
    NEXT2(-11, "NEXT2", CursorData::next2),
    NEXT3(-12, "NEXT3", CursorData::next3),
    NEXT4(-13, "NEXT4", CursorData::next4),
    NEXT5(-14, "NEXT5", CursorData::next5),
    NEXT6(-15, "NEXT6", CursorData::next6),
    NEXT7(-16, "NEXT7", CursorData::next7),
    NEXT8(-17, "NEXT8", CursorData::next8),
    NEXT9(-18, "NEXT9", CursorData::next9),
    NEXT10(-19, "NEXT10", CursorData::next10),
    NEXT11(-20, "NEXT11", CursorData::next11),
    NEXT12(-21, "NEXT12", CursorData::next12),
    NEXT13(-22, "NEXT13", CursorData::next13),
    NEXT14(-23, "NEXT14", CursorData::next14),
    NEXT15(-24, "NEXT15", CursorData::next15),
    NEXT16(-25, "NEXT16", CursorData::next16),
    NEXT17(-26, "NEXT17", CursorData::next17),
    NEXT18(-27, "NEXT18", CursorData::next18),
    NEXT19(-28, "NEXT19", CursorData::next19),
    NEXT20(-29, "NEXT20", CursorData::next20),
    REOPEN(-30, "REOPEN", CursorData::reopen);


    companion object {
        fun byIndex(index: Int): CursorFun? = values().firstOrNull { it.index == index }

        fun byColumn(funName: String): CursorFun? = values().firstOrNull { it.funName == funName.uppercase() }
    }
}

data class Record(var columns: MutableList<Var> = ArrayList() ) {

    fun setApply(sourceRecord: Record) {

        checkExistsColumns(sourceRecord)

        val columnsRecord = ArrayList<Var>()
        for(sourceColumn in sourceRecord.columns) {

            val columnRecord = columnByName(sourceColumn.name)?.apply { result.setVar( sourceColumn.result ) }
                ?: Var(sourceColumn.name, VarResult(sourceColumn.result.type, sourceColumn.result.value))

            columnsRecord += columnRecord
        }

        this.columns = columnsRecord
    }

    private fun checkExistsColumns(sourceRecord: Record) {
        for(column in columns) {
            sourceRecord.columns.firstOrNull { it.name == column.name }
                ?: throw Exception("Record column <${column.name}> absent assigned record  $sourceRecord")
        }
    }

    fun clearData() {
        for(column in columns) {
            column.result.type = VarType.UNDEFINED
            column.result.value = null
        }
    }

    fun columnByName(columnName: String): Var? = columns.firstOrNull { it.name == columnName }
}

data class QuerySession(val query: Query, val sessionSetting: SessionSetting)

private class ColumnResult(private val cursor: CursorData, val columnName: String,
                           private var index: Int = UNINITIALIZE_COLUMN_INDEX,
                           private var funIndex: Int = UNINITIALIZE_COLUMN_INDEX): ReturnResult {
    override fun getVar(): VarResult {
        checkInitIndexes()

        return cursor.getVarResult(index, funIndex)
    }

    override fun setVar(newVar: VarResult) {}

    override fun getSqlValue(): Any {
        checkInitIndexes()

        return cursor.toSqlValue(index, funIndex)
    }

    private fun checkInitIndexes() {
        if(index == UNINITIALIZE_COLUMN_INDEX) {
            val (newIndex, newFun) =  cursor.getColumnIndex(columnName)
            index = newIndex
            funIndex = newFun
        }
    }
}

fun Array<Any?>?.isEqualParam(param: Array<Any?>?): Boolean {
    if(this === param) return true

    if(param == null || this == null) return false

    if(param.size != this.size) return false

    for( (index, value) in withIndex() ) {
        if(value != param[index]) return false
    }
    return true
}

private const val UNINITIALIZE_COLUMN_INDEX = Int.MAX_VALUE