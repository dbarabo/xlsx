package ru.barabo.gui.swing

import java.net.URL
import javax.swing.ImageIcon

object ResourcesManager {

    private val icoHash: HashMap<String, ImageIcon> = HashMap()

    var icoPath = "/ico/"

    @JvmStatic
    fun getIcon(icoName: String): ImageIcon? =
        icoHash[icoName] ?:  loadIcon(icoName)?.apply { icoHash[icoName] = this }

    private fun loadIcon(icoName :String): ImageIcon? = pathResource("$icoPath$icoName.png")?.let { ImageIcon(it) }

    @JvmStatic
    fun pathResource(fullPath: String): URL? {

        val path = ResourcesManager::class.java.getResource(fullPath)?.toExternalForm()

        return path?.let{ URL(it) }
    }
}