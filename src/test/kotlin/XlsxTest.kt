import org.junit.Test
import org.slf4j.LoggerFactory
import ru.barabo.afina.AfinaConnect
import ru.barabo.afina.AfinaQuery
import ru.barabo.gui.swing.comboBoxWithItems
import ru.barabo.gui.swing.getDefaultToDirectory
import ru.barabo.xls.*
import java.awt.Container
import java.io.File
import java.util.*
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.collections.ArrayList

class XlsxTest {

    private val logger = LoggerFactory.getLogger(XlsxTest::class.java)

    @Test
    fun testComboBoxWithItems() {

        val list = ArrayList<String>()

        JPanel().apply {
            comboBoxWithItems("Плательщик", 0, list, 0)
        }
    }


        //@Test
    fun testSimpleParserNumber() {

        val parser: Parser = Parser(AfinaQuery)

        val expr = parser.parseExpression("1", ArrayList<Var>())

        logger.error("expr.size=${expr.size}")
        logger.error("expr=$expr")
    }

    //@Test
    fun testBuildWithOutParam() {
        val poi = generatePoi(1210205169L)
        poi.buildWithOutParam(ArrayList())
        logger.info("${poi.newFile}")

        poi.buildWithOutParam(ArrayList())

        val poi2 = generatePoi(1210205169L)
        poi2.buildWithOutParam(ArrayList())
    }

    //@Test
    fun testSimpleBuildWithrequestParam() {

        val poi = generatePoi(1210205169L)

        poi.initRowData(ArrayList())
        poi.requestParamAutoTest(ParamContainerTest() )
    }

   // @Test
    fun testTwoClickOkBuildWithrequestParam() {

        val poi = generatePoi(1210205169L)

        poi.initRowData(ArrayList())
        poi.requestParamAutoTest(ParamContainerTest() )

        poi.requestParamAutoTest(ParamContainerTest() )
    }

    //@Test
    fun testReportm() {
        val poi = generatePoi(1276713842L)

        val vr: MutableList<Var> = ArrayList()

        vr += Var("Период_с", VarResult(VarType.DATE, java.sql.Date(2023, 3, 1)))
        vr += Var("Период_по", VarResult(VarType.DATE, java.sql.Date(2023, 3, 9)))

        val cols: MutableList<Var> = ArrayList()
        cols += Var("Classified", VarResult(VarType.INT, null) )
        vr += Var("Отделение", VarResult(VarType.RECORD, Record(cols) ))

        val cols2: MutableList<Var> = ArrayList()
        cols2 += Var("ID", VarResult(VarType.INT, 1) )
        vr += Var("Отношения_с_клиентом", VarResult(VarType.RECORD, Record(cols2) ))

        poi.initRowData(vr)
        poi.requestParamAutoTest(ParamContainerTest() )
    }


}

private fun generatePoi(idTemplate: Long): PoiXlsx {

    val (templateDataFile, outFile) = templateOutFile(idTemplate)

    return PoiXlsx(templateDataFile, AfinaQuery) { outFile }
}

private fun templateOutFile(idTemplate: Long): Pair<File, File> {
    val templateFileName = "test-template.xlsx"

    val templateFile = File("${defaultDirectory("temp")}/$templateFileName")

    AfinaConnect.init(secret[Tag.AFINA_URL]!!,
        secret[Tag.AFINA_USER]!!, secret[Tag.AFINA_PSWD]!!)

    val templateDataFile = AfinaQuery.selectBlobToFile(SELECT_BLOB_TEMPLATE_REPORT, arrayOf(idTemplate), templateFile)

    return Pair(templateDataFile, File("${defaultDirectory("temp")}/test-out${Date().time}.xlsx"))
}

private fun defaultDirectory(dirName: String): File {
    val directory = File("${getDefaultToDirectory().absolutePath}/$dirName")

    if(!directory.exists()) {
        directory.mkdirs()
    }

    return directory
}

private class ParamContainerTest : ParamContainer {

    override val bookForTableBox: JTabbedPane = JTabbedPane()

    override val container: Container = JPanel().apply { bookForTableBox.addTab("", this) }

    override fun afterReportCreated(reportFile: File) {
    }
}

private const val SELECT_BLOB_TEMPLATE_REPORT = "select r.TEMPLATE from OD.XLS_REPORT r where r.id = ?"