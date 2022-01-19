package ru.barabo.gui.swing

import java.awt.Component
import javax.swing.JTabbedPane
import javax.swing.plaf.basic.BasicTabbedPaneUI



class HideTabbedPane : JTabbedPane(TOP, SCROLL_TAB_LAYOUT) {

    fun addTab(panel: Component) = addTab("", panel)

    init {
        setUI(object : BasicTabbedPaneUI() {
            override fun calculateTabAreaHeight(tab_placement: Int, run_count: Int, max_tab_height: Int): Int {
                return 0
            }
        })
    }
}