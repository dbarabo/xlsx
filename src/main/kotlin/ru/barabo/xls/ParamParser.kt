package ru.barabo.xls

import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

fun parseParams(paramsExpression: String, vars: List<Var>): List<Param> {

    val expressions = paramsExpression.split(END_COMMAND)

    val params = ArrayList<Param>()

    for(expr in expressions) {
        val param = parseParam(expr.trim(), vars) ?: continue

        params += param
    }

    return params
}

private fun parseParam(expr: String, vars: List<Var>): Param? {
    if(expr.isBlank()) return null

    val openBracket = expr.indexOf('(')
    val closeBracket = expr.indexOf(')')

    if(openBracket <= 0 || closeBracket <= 0) throw Exception("Не найдены скобки () в параметре $expr")

    val funName = expr.substring(0 until openBracket).trim().uppercase(Locale.getDefault())

    val funParam = paramFunByName(funName)  ?: throw Exception("функция-параметр с заданным именем не найдена $funName")

    val (varParam, nextIndex) = findVarBracket(expr, vars, funParam, 0)

    if(funParam.countParam == 1) return Param(funParam, varParam)

    val (cursorVar, _) = findVarBracket(expr, vars, funParam, nextIndex)

    val cursorData = cursorVar.result.value as? CursorData
        ?: throw Exception("переменная $cursorVar должна быть курсором в параметре $expr")

    return Param(funParam, varParam, cursorData)
}

private fun findVarBracket(expr: String, vars: List<Var>, funParam: ComponentType, startIndex: Int = 0): Pair<Var, Int> {
    val openVar = expr.indexOf('[', startIndex)
    val closeVar = expr.indexOf(']', startIndex)

    if(openVar <= 0 || closeVar <= 0) throw Exception("Не найдены скобки [] для функции $funParam в параметре $expr")

    val varParamName = expr.substring(openVar + 1 until closeVar).trim().uppercase(Locale.getDefault())

    val varParam = vars.firstOrNull { it.name == varParamName }
        ?: throw Exception("переменная параметра с именем $varParamName не найдена в параметре $expr")

    return Pair(varParam, closeVar + 1)
}