package ru.barabo.gui.swing

import org.slf4j.LoggerFactory
import java.awt.*
import java.io.File
import java.text.NumberFormat
import java.util.*
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.text.NumberFormatter

private val logger = LoggerFactory.getLogger("CreateComponents")

fun Container.maxSpaceYConstraint(gridY: Int): JLabel {
    return JLabel("").apply {
        this@maxSpaceYConstraint.add(this,
            GridBagConstraints(0, gridY, 1, 1, 1.0, 10.0,
                GridBagConstraints.PAGE_END, GridBagConstraints.HORIZONTAL,
                Insets(0, 0, 0, 0), 0, 0)
        )
    }
}
fun Container.maxSpaceXConstraint(gridX: Int): JLabel {
    return JLabel("").apply {
        this@maxSpaceXConstraint.add(this,
            GridBagConstraints(gridX, 0, 1, 1, 1.0, 10.0,
                GridBagConstraints.PAGE_END, GridBagConstraints.HORIZONTAL,
                Insets(0, 0, 0, 0), 0, 0)
        )
    }
}

fun <T> Container.comboBox(label: String, gridY: Int, list: List<T>? = null, gridX: Int = 0): JComboBox<T> {

    add( JLabel(label), labelConstraint(gridY, gridX) )

    val items = list?.let { Vector(it) }

    val combo = items?.let { JComboBox(it) } ?: JComboBox()

    add(combo, textConstraint(gridY = gridY, gridX = gridX + 1) )

    return combo
}

fun <T> Container.comboBoxWithItems(label: String, gridY: Int, list: List<T>? = null, gridX: Int = 0): Pair<JComboBox<T>, Vector<T>?> {

    add( JLabel(label), labelConstraint(gridY, gridX) )

    val items = list?.let { Vector(it) }

    val combo = items?.let { JComboBox(it) } ?: JComboBox()

    add(combo, textConstraint(gridY = gridY, gridX = gridX + 1) )

    return Pair(combo, items)
}

fun Container.textFieldVertical(label: String, gridY: Int): JTextField {

    add( JLabel(label), labelConstraint(gridY) )

    return JTextField().apply {

        this@textFieldVertical.add(this, textConstraint(gridY + 1) )
    }
}

fun Container.textFieldHorizontal(label: String, gridY: Int, gridX: Int = 0, width: Int = 1): JTextField {

    add( JLabel(label), labelConstraint(gridY, gridX) )

    return JTextField().apply {


        this@textFieldHorizontal.add(this, textConstraint(gridY = gridY, gridX = gridX + 1, width = width) )
    }
}

fun Container.textArea(label: String, gridY: Int, height: Int = 2): JTextArea {

    add( JLabel(label), labelConstraint(gridY) )

    return JTextArea().apply {

        this.rows = height

        this.isEditable = false

        this@textArea.add(this, textConstraint(gridY + 1, height) )
    }
}

fun Container.textAreaHorizontal(label: String, gridY: Int, height: Int = 2): JTextArea {

    add( JLabel(label), labelConstraint(gridY) )

    return JTextArea().apply {

        this.rows = height

        this.isEditable = false

        this@textAreaHorizontal.add(this, textConstraint(gridY, height, 1) )
    }
}

fun Container.onlyButton(title: String, gridY: Int, gridX: Int = 0, ico: String? = null, clickListener: ()->Unit): JButton =
    JButton(title).apply {
        ico?.let { this.icon = ResourcesManager.getIcon(it) }

        addActionListener { clickListener() }

        this@onlyButton.add(this, textConstraint(gridY, 1, gridX) )
    }

fun Container.button(label: String, title: String, gridY: Int, width: Int = 1, clickListener: ()->Unit): JButton {

    var buttonY = gridY
    if(label.isNotEmpty()) {
        add( JLabel(label), labelConstraint(gridY) )
        buttonY++
    }

    return JButton(title).apply {
        addActionListener { clickListener() }

        this@button.add(this, textConstraint(gridY = buttonY, width = width) )
    }
}

fun Container.buttonHorisontal(label: String, title: String, gridY: Int, clickListener: ()->Unit): JButton {

    add( JLabel(label), labelConstraint(gridY) )

    return JButton(title).apply {
        addActionListener { clickListener() }

        this@buttonHorisontal.add(this, textConstraint(gridY, gridX = 1) )
    }
}

fun Container.onOffButton(title: String, isSelected: Boolean = false, clickListener: ()->Unit): JCheckBox {
    return JCheckBox(title, ResourcesManager.getIcon("off"), isSelected).apply {
        selectedIcon = ResourcesManager.getIcon("on")

        addActionListener { clickListener() }

        this@onOffButton.add(this)
    }
}

fun Container.liteGroup(title: String, gridY: Int, gridX: Int = 0, width: Int = 1): JPanel = JPanel().apply {
    border = TitledBorder(title)

    layout = GridBagLayout()

    this@liteGroup.add(this, labelConstraint(gridY, gridX, width))
}

fun JPopupMenu.menuItem(name: String? = null, icon: String = "",  action: ()->Unit = {} ): JMenuItem? {
    if(name == null) {
        addSeparator()
        return null
    }

    return JMenuItem(name, ResourcesManager.getIcon(icon)).apply {
        this.addActionListener{ action() }
        this@menuItem.add(this)
    }
}

fun Container.popupButton(name: String? = null, icon: String = "", op: JPopupMenu.()->Unit = {}): JButton /*JPopupMenu*/ {

    return popup(name, icon, op).apply { this@popupButton.add(this) }
}

fun popup(name: String? = null, icon: String = "", op: JPopupMenu.()->Unit = {}): JButton {
    val button = JButton( ResourcesManager.getIcon(icon) )
    button.text = name
    button.toolTipText = name

    val popupMenu = JPopupMenu()

    button.addActionListener { popupMenu.show(button, 1, button.height + 1) }

    op(popupMenu)

    return button
}

fun Container.groupPanel(title: String, gridY: Int, height: Int = 1, gridX: Int = 0, width: Int = 1): JPanel = JPanel().apply {
    border = TitledBorder(title)

    layout = GridBagLayout()

    this@groupPanel.add(this, textConstraint(gridY, height, gridX, width))
}

fun Container.toolButton(icon: String, name: String?, groupIndex: Int? = null,
                         buttonGroupList: MutableList<ButtonGroup>? = null, action: ()->Unit): AbstractButton {

    val button: AbstractButton = groupIndex?.let {
        JToggleButton( ResourcesManager.getIcon(icon) ).apply { buttonGroupList?.addGroup(this, it) }
    } ?: JButton(ResourcesManager.getIcon(icon) )

    button.text = name
    button.toolTipText = name
    button.addActionListener { action() }
    button.horizontalAlignment = SwingConstants.LEFT

    return button.apply { this@toolButton.add(this) }
}

private fun MutableList<ButtonGroup>.addGroup(button: AbstractButton?, index: Int) {
    while (size <= index) {
        add(ButtonGroup())
    }
    this[index].add(button)
}

fun textConstraint(gridY: Int, height: Int = 1, gridX: Int = 0, width: Int = 1) =
    GridBagConstraints(gridX, gridY, width, height, 1.0, 0.6,
        GridBagConstraints.PAGE_START, GridBagConstraints.HORIZONTAL,
        Insets(5, 2, 5, 2), 0, 0)

fun labelConstraint(gridY: Int, gridX: Int = 0, width: Int = 1) =
    GridBagConstraints(gridX, gridY, width, 1, 0.0, 0.0,
        GridBagConstraints.PAGE_START, GridBagConstraints.HORIZONTAL,
        Insets(5, 2, 5, 2), 0, 0)

fun processShowError(process: ()->Unit) {
    try {
        process()
    } catch (e: Exception) {
        logger.error("processShowError", e)

        errorMessage(e.message)
    }
}

fun errorMessage(message: String?): Boolean {
    JOptionPane.showMessageDialog(null, message, null, JOptionPane.ERROR_MESSAGE)

    return false
}

fun showMessage(message: String?): Boolean {
    JOptionPane.showMessageDialog(null, message, null, JOptionPane.INFORMATION_MESSAGE)

    return true
}

fun getDefaultToDirectory(): File = JFileChooser().fileSystemView.defaultDirectory

fun intoSwingThread(process: ()-> Unit) {
    if(SwingUtilities.isEventDispatchThread() ) {
        process()
    } else {
        SwingUtilities.invokeLater { process() }
    }
}

fun integerFormat(): NumberFormatter {
    val format = NumberFormat.getInstance()
    format.isGroupingUsed = false //Remove comma from number greater than 4 digit

    return NumberFormatter(format).apply {
        minimum = Int.MIN_VALUE
        maximum = Int.MAX_VALUE
        allowsInvalid = true
        commitsOnValidEdit = true
    }
}