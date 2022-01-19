package ru.barabo.afina

import ru.barabo.db.DbConnection
import ru.barabo.db.DbSetting

object AfinaConnect: DbConnection(
        DbSetting("oracle.jdbc.driver.OracleDriver",
                "",
                "",
                "",
                "select 1 from dual") ) {

    @JvmStatic
    fun init(url: String, user: String, password: String): Boolean {
        dbSetting.url = url
        dbSetting.user = user
        dbSetting.password = password

        return checkBase()
    }
}
