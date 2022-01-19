package ru.barabo.gui.swing

import java.awt.Component
import java.awt.Container
import java.awt.Frame
import java.awt.GridBagLayout
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.SwingUtilities

abstract class AbstractDialog(component: Component, title: String) :
    JDialog(parentWindow(component), title, true) {

    @Volatile private var resultOk = true

    abstract fun okProcess()

    init {
        layout = GridBagLayout()
    }

    fun packWithLocation() {
        pack()

        setLocationRelativeTo(owner)
    }

    fun Container.createOkCancelButton(gridY: Int = 1, gridX: Int = 0): JPanel =
        groupPanel("", gridY, 2, gridX).apply {
            onlyButton("Сохранить", 0, 0, "saveDB"){ ok() }

            onlyButton("Отменить", 0, 1, "deleteDB"){ cancel() }
        }

    private fun ok() {
        resultOk = true

        processShowError {
            okProcess()

            dispose()
        }
    }

    protected fun cancel() {

        resultOk = false

        dispose()
    }

    fun showDialogResultOk(): Boolean {
        resultOk = false

        isVisible = true

        return resultOk
    }
}

fun parentWindow(component: Component): Frame? = SwingUtilities.getWindowAncestor(component) as? Frame