package ru.barabo.afina

import org.slf4j.LoggerFactory
import ru.barabo.db.Query
import java.sql.Clob

data class UserDepartment(val userName: String?,
                          val departmentName: String?,
                          val workPlace: String?,
                          val accessMode: AccessMode,
                          val userId: String,
                          val workPlaceId: Long,
                          val departmentId: Long? = null,
                          val accountCode: String = "",
                          val accountId: Long? = null)

enum class AccessMode {
    None,
    FullAccess,
    DelbAccess,
    CreditAccess,
    CardMoveOutOnly,
    CashBox;

    companion object {
        private const val SUPERVISOR = "СУПЕРВИЗОР"

        private const val DELB = "ДЭЛБ"

        private const val CREDIT = "КРЕДИТ"

        private const val CASH_BOX = "КАСС"

        fun byWorkPlace(workPlace: String): AccessMode {
            return when {
                workPlace.uppercase().indexOf(SUPERVISOR) >= 0 -> FullAccess
                workPlace.uppercase().indexOf(DELB) >= 0 ->  DelbAccess
                workPlace.uppercase().indexOf(CREDIT) >= 0 ->  CreditAccess
                workPlace.uppercase().indexOf(CASH_BOX) >= 0 ->  CashBox

                else -> CardMoveOutOnly
            }
        }
    }
}

object AfinaQuery : Query(AfinaConnect) {

    private val logger = LoggerFactory.getLogger(AfinaQuery::class.java)

    @JvmStatic
    @Synchronized
    fun nextSequence(): Number = selectValue(query = SEQ_CLASSIFIED) as Number

    private lateinit var userDepartment: UserDepartment

    @JvmStatic
    fun getUserDepartment(): UserDepartment {
        if (!::userDepartment.isInitialized) {
            userDepartment = initUserDepartment()
        }

        return userDepartment
    }

    fun setUserDepartmentData(userDepartmentData: UserDepartment) {
        userDepartment = userDepartmentData
    }

    private fun initUserDepartment(): UserDepartment {
        val data = selectCursor(query = SEL_CURSOR_USER_DEPARTMENT)

        val row = if(data.isEmpty()) throw Exception("Юзер не зареган :(") else data[0]

        val userName = row[0] as? String

        val departmentName = row[1] as? String

        val workPlace = row[2] as? String ?: throw Exception("Не определено рабочее место :(")

        val userId = row[3] as? String ?: throw Exception("Где юзер? Что это вообще такое???")

        val workPlaceId =  (row[4] as? Number)?.toLong() ?: throw Exception("workPlaceId куда-то деляся :(")

        return UserDepartment(userName, departmentName, workPlace, AccessMode.byWorkPlace(workPlace), userId, workPlaceId)
    }

    @JvmStatic
    fun getUser(): String = selectValue(query = SEL_USER) as String

    @JvmStatic
    fun isTestBaseConnect() = dbConnection.isTestBase()
}

fun Clob.clobToString() = this.getSubString(1, this.length().toInt())

const val SEQ_CLASSIFIED = "select classified.nextval from dual"

private const val SEL_USER = "select user from dual"

private const val SEL_CURSOR_USER_DEPARTMENT = "{ ? = call od.ptkb_plastic_auto.getUserAndDepartment }"