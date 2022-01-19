package ru.barabo.gui.swing

import java.awt.event.ActionListener
import javax.swing.*

open class ButtonKarkas(val ico: String? = null,
                        val name: String? = null,
                        private val groupIndex: Int? = null,
                        var listener: ActionListener? = null) {

    private var button: AbstractButton? = null

    val imageIcon: ImageIcon?
    get() = ico?.let { ResourcesManager.getIcon(it) }

    fun createButton(buttonGroupList: MutableList<ButtonGroup>): AbstractButton? {
        val karkas = this

        val name = karkas.name ?: return null

        val icon = karkas.imageIcon

        val button: AbstractButton = karkas.groupIndex?.let {
            JToggleButton(icon).apply {
                addGroup(buttonGroupList, this, it)
            }
        }  ?: JButton(icon)

        button.text = name

        button.toolTipText = name

        karkas.listener?.let { button.addActionListener(it) }
        karkas.button = button

        return button
    }
}

fun addGroup(buttonGroupList: MutableList<ButtonGroup>, button: AbstractButton, index: Int) {

    if(buttonGroupList.size <= index) {
        buttonGroupList.add( ButtonGroup() )
    }
    buttonGroupList[index].add(button)
}


