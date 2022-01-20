package ru.barabo.afina

import java.io.File
import java.net.InetAddress
import javax.swing.JOptionPane
import kotlin.concurrent.thread
import kotlin.concurrent.timer
import kotlin.system.exitProcess

object VersionChecker {

    @Volatile
    private var programName = ""

    @Volatile
    private var versionJar = 0

    private const val STATE_RUN = 0

    private const val STATE_EXIT = 2

    private val timer = timer(name = this.javaClass.simpleName, initialDelay = 5_000, daemon = false, period = 20_000) { checkVersionRun() }

    @JvmStatic
    fun runCheckVersion(programName: String, versionJar: Int) {

        this.programName = programName

        this.versionJar = versionJar

        timer.apply {  }

        updateActualVersion(STATE_RUN)
    }

    @JvmStatic
    fun exitCheckVersion() {

        updateActualVersion(STATE_EXIT)

        timer.cancel()
        timer.purge()
    }

    private fun updateActualVersion(state: Int) {

        val params = arrayOf<Any?>(programName,
            versionJar,
            InetAddress.getLocalHost().hostAddress,
            File(VersionChecker::class.java.protectionDomain.codeSource.location.path).toURI().path,
            InetAddress.getLocalHost().hostName,
            state)

        AfinaQuery.execute(UPDATE_VERSION_INFO, params)
    }

    private fun checkVersionRun() {

        val minVersion = AfinaQuery.selectValueType<Number>(selectVersion(programName) ) ?: return

        if(minVersion.toInt() <= versionJar) return

        terminateApplication()
    }

    private fun terminateApplication() {
        showMessageTerminate()

        Thread.sleep(20_000)

        exitCheckVersion()

        exitProcess(0)
    }

    private fun showMessageTerminate() {
        thread {
            JOptionPane.showMessageDialog(null, NEED_TO_UPDATE, null, JOptionPane.INFORMATION_MESSAGE)
        }
    }

    private const val UPDATE_VERSION_INFO = "{ call od.PTKB_PLASTIC_AUTO.upsertVersionInfo(?, ?, ?, ?, ?, ?) }"

    private const val NEED_TO_UPDATE =
        "Ваша версия программы безнадежно устарела\n Пожалуйста, закройте ее и в течении 3-х минут она автоматически обновится"

    private fun selectVersion(programName: String) =
"""
select coalesce(min(j.VERSION_MIN), 0)
    from od.PTKB_VERSION_JAR j
   where j.PROGRAM = '$programName'
     and j.STATE = 0
  """
}
