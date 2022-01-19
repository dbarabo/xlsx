package ru.barabo.gui.swing.table

import kotlin.reflect.KMutableProperty1

data class ColumnTableModel<T, S>(val title: String,
                                  val width: Int,
                                  val prop: KMutableProperty1<T, S>,
                                  val isEditable: Boolean = false)

