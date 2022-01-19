package ru.barabo.afina.gui

import ru.barabo.afina.AfinaConnect
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyListener
import java.util.*
import java.util.prefs.Preferences
import javax.swing.*

class ModalConnect private constructor(mainWin: JFrame) : JDialog(mainWin, CONNECT_TEXT, true) {

    private lateinit var cb: JComboBox<String>

    private lateinit var tf: JPasswordField

    private lateinit var servers: JComboBox<String>

    private val userPrefs: Preferences = Preferences.userRoot().node("plan")

    init {

        isConnected = false

        modalConnect = this

        buildUI()
    }

    private fun buildUI() {
        minimumSize = Dimension(
            Toolkit.getDefaultToolkit().screenSize.width / 5,
            Toolkit.getDefaultToolkit().screenSize.height / 20
        )

        setLocationRelativeTo(null)

        contentPane.layout = GridLayout(4, 2, 20, 10)

        tf = ModPasswordField().apply {
            requestFocus()

            grabFocus()
        }

        var label = JLabel("Логин:")
        label.horizontalAlignment = SwingConstants.RIGHT
        contentPane.add(label)
        cb = JComboBox(items)
        cb.isEditable = true
        cb.selectedItem = userPrefs["login", ""]
        contentPane.add(cb)
        label = JLabel("Пароль:")
        label.horizontalAlignment = SwingConstants.RIGHT
        contentPane.add(label)
        contentPane.add(tf)
        servers = JComboBox(arrayOf("AFINA", "ORATEST", "TEST_DEMO"))
        servers.selectedIndex = userPrefs.getInt("server", 0)
        label = JLabel("Вход в:")
        label.horizontalAlignment = SwingConstants.RIGHT
        contentPane.add(label)
        contentPane.add(servers)
        val buttonOk = JButton(ButtonOk("Ok"))
        val buttonCancel = JButton(ButtonCancel("Отмена"))
        val panelButton = JPanel()
        panelButton.layout = GridLayout(1, 2, 10, 0)
        panelButton.add(buttonOk)
        panelButton.add(buttonCancel)
        contentPane.add(JLabel())
        contentPane.add(panelButton)
        pack()
        tf.requestFocusInWindow()
        isVisible = true
    }

    private fun setConnected() {
        isConnected = AfinaConnect.init(
            SERVER_NAME[servers.selectedIndex],
            (Objects.requireNonNull(cb.selectedItem) as String),
            String(tf.password)
        )
        if (!isConnected) {
            JOptionPane.showMessageDialog(
                null,
                "Неправильно набран пароль или логин!",
                null, JOptionPane.ERROR_MESSAGE
            )
        } else {
            saveParam()
            isVisible = false
        }
    }

    internal inner class ModPasswordField : JPasswordField() {
        init {
            this.addKeyListener(object : KeyListener {
                override fun keyPressed(e: KeyEvent?) {}
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyChar == VK_ENTER.toChar()) {

                        setConnected()
                    }
                }

                override fun keyTyped(e: KeyEvent?) {}
            }
            )
        }
    }

    internal inner class ButtonOk(name: String?) : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            setConnected()
        }

        init {
            putValue(Action.NAME, name)
        }
    }

    internal inner class ButtonCancel(name: String?) : AbstractAction() {
        override fun actionPerformed(e: ActionEvent?) {
            isConnected = false
            this@ModalConnect.isVisible = false
        }

        init {
            putValue(Action.NAME, name)
        }
    }

    private fun saveParam() {
        userPrefs.put("login", modalConnect.cb.selectedItem?.toString() )

        userPrefs.putInt("server", modalConnect.servers.selectedIndex )
    }

    companion object {
        //final static transient private Logger logger = Logger.getLogger(ModalConnect.class.getName());
        private const val CONNECT_TEXT = "соединение с сервером"
        private val items = arrayOf("BARDV", "NEGMA", "KOLSV")
        private val SERVER_NAME = arrayOf(
            "jdbc:oracle:thin:@192.168.0.43:1521:AFINA",
            "jdbc:oracle:thin:@192.168.0.42:1521:AFINA",
            "jdbc:oracle:thin:@192.168.0.47:1521:AFINA"
        )

        private lateinit var modalConnect: ModalConnect

        @Volatile private var isConnected = false

        fun initConnect(mainWin: JFrame): Boolean {
            modalConnect = ModalConnect(mainWin)
            return isConnected
        }
    }
}