package com.hmie.btreport.generator

import android.content.Context
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import com.hmie.btreport.model.Trip
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExcelGenerator(private val context: Context) {

    companion object {
        const val COMPANY      = "HMIE"
        const val COMPANY_FULL = "HMIE"
        const val DA_FIRST_DAY = 1000.0
        const val DA_OTHER     = 2000.0
    }

    private data class DayRow(
        val display: String, val from: String, val to: String,
        val da: Double, val lodging: Double, val others: Double
    ) { val total get() = da + lodging + others }

    // Style indices
    private val S0 = 0   // normal
    private val S1 = 1   // bold
    private val S2 = 2   // 14pt bold center (title)
    private val S3 = 3   // 11pt bold center (subtitle/section)
    private val S4 = 4   // bordered normal wrap
    private val S5 = 5   // bordered bold center gray (table header)
    private val S6 = 6   // bordered number 0.0
    private val S7 = 7   // bordered bold number yellow (total)
    private val S8 = 8   // bordered bold center yellow (total label)
    private val S9 = 9   // italic gray center (credit)

    fun generate(trip: Trip, expenses: List<Expense>): File {
        val name = trip.employeeName.replace(" ", "_")
        val date = SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH).format(Date())
        val file = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "DBT_Settlement_${name}_$date.xlsx"
        )
        file.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            ze(zip, "[Content_Types].xml", ct())
            ze(zip, "_rels/.rels",          rootRels())
            ze(zip, "xl/workbook.xml",      wb())
            ze(zip, "xl/_rels/workbook.xml.rels", wbRels())
            ze(zip, "xl/styles.xml",        styles())
            ze(zip, "xl/worksheets/sheet1.xml", sheet1(trip, expenses))
            ze(zip, "xl/worksheets/sheet2.xml", sheet2(trip))
            ze(zip, "xl/worksheets/sheet3.xml", sheet3(trip, expenses))
            ze(zip, "xl/worksheets/sheet4.xml", sheet4(trip, expenses))
        }
        return file
    }

    private fun ze(zip: ZipOutputStream, name: String, s: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(s.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun ct() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet4.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml"   ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun wb() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="DBT Page_A"         sheetId="1" r:id="rId1"/>
    <sheet name="Total Page (B)"     sheetId="2" r:id="rId2"/>
    <sheet name="Other Exp"          sheetId="3" r:id="rId3"/>
    <sheet name="DA_Tax exmp proofs" sheetId="4" r:id="rId4"/>
  </sheets>
</workbook>"""

    private fun wbRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet4.xml"/>
  <Relationship Id="rId5" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"    Target="styles.xml"/>
</Relationships>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1"><numFmt numFmtId="164" formatCode="0.0"/></numFmts>
  <fonts count="5">
    <font><sz val="10"/><name val="Arial"/></font>
    <font><b/><sz val="10"/><name val="Arial"/></font>
    <font><b/><sz val="14"/><name val="Arial"/></font>
    <font><b/><sz val="11"/><name val="Arial"/></font>
    <font><i/><sz val="9"/><color rgb="FF9E9E9E"/><name val="Arial"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD9E1F2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFEB9C"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FF000000"/></left>
      <right style="thin"><color rgb="FF000000"/></right>
      <top style="thin"><color rgb="FF000000"/></top>
      <bottom style="thin"><color rgb="FF000000"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="10">
    <xf numFmtId="0"   fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0"   fontId="1" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0"   fontId="2" fillId="0" borderId="0" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="0"   fontId="3" fillId="0" borderId="0" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="0"   fontId="0" fillId="0" borderId="1" xfId="0"><alignment wrapText="1"/></xf>
    <xf numFmtId="0"   fontId="1" fillId="2" borderId="1" xfId="0"><alignment horizontal="center" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0"><alignment horizontal="right"/></xf>
    <xf numFmtId="164" fontId="1" fillId="3" borderId="1" xfId="0"><alignment horizontal="right"/></xf>
    <xf numFmtId="0"   fontId="1" fillId="3" borderId="1" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="0"   fontId="4" fillId="0" borderId="0" xfId="0"><alignment horizontal="center"/></xf>
  </cellXfs>
</styleSheet>"""

    // ── Cell / row helpers ────────────────────────────────────────────────────

    private fun c(col: String, row: Int, v: String, s: Int = S0) =
        """<c r="$col$row" t="inlineStr" s="$s"><is><t>${v.xe()}</t></is></c>"""

    private fun cn(col: String, row: Int, v: Double, s: Int = S6) =
        """<c r="$col$row" s="$s"><v>$v</v></c>"""

    private fun String.xe() = replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")

    private fun StringBuilder.row(r: Int, ht: Int = 18, cells: (Int) -> String) =
        appendLine("""<row r="$r" ht="$ht" customHeight="1">${cells(r)}</row>""")

    private fun mc(vararg refs: String) =
        if (refs.isEmpty()) "" else buildString {
            append("""<mergeCells count="${refs.size}">""")
            refs.forEach { append("""<mergeCell ref="$it"/>""") }
            append("</mergeCells>")
        }

    private fun colDefs(vararg widths: Double) = buildString {
        append("<cols>")
        widths.forEachIndexed { i, w ->
            append("""<col min="${i+1}" max="${i+1}" width="$w" customWidth="1"/>""")
        }
        append("</cols>")
    }

    private fun ws(colDefs: String, sheetData: String, merges: String) =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
$colDefs
<sheetData>
$sheetData</sheetData>
$merges
</worksheet>"""

    // ── Sheet 1 : DBT Page_A ─────────────────────────────────────────────────

    private fun sheet1(trip: Trip, expenses: List<Expense>): String {
        val days  = buildDayRows(expenses)
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
        val sb    = StringBuilder()
        val merges = mutableListOf<String>()
        var r = 1

        fun merge(ref: String) = merges.add(ref)

        // Title
        merge("A1:I1")
        sb.row(r++, 28) { c("A", it, COMPANY, S2) }
        sb.row(r++, 6)  { "" }
        merge("A3:I3")
        sb.row(r++, 22) { c("A", it, "DOMESTIC BUSINESS TRIP SETTLEMENT", S3) }
        sb.row(r++, 6)  { "" }
        // Date top-right
        merge("H5:I5")
        sb.row(r++, 18) { c("G", it, "Date:", S1) + c("H", it, today, S1) }
        // Employee row
        merge("B6:D6"); merge("H6:I6")
        sb.row(r++, 20) {
            c("A",it,"Name",S1)+c("B",it,trip.employeeName,S4)+
            c("E",it,"Emp ID #",S1)+c("F",it,trip.employeeId,S4)+
            c("G",it,"Desg",S1)+c("H",it,trip.designation,S4)
        }
        // Budget row
        merge("B7:D7"); merge("H7:I7")
        sb.row(r++, 20) {
            c("A",it,"Budget",S1)+c("B",it,"Operating [✓]  /  Technology [  ]",S4)+
            c("E",it,"WBS Code",S1)+c("F",it,trip.costCenter,S4)+
            c("G",it,"GL #",S1)+c("H",it,"",S4)
        }
        // Purpose
        merge("B8:I8")
        sb.row(r++, 30) { c("A",it,"BT Purpose",S1)+c("B",it,trip.purpose,S4) }
        // Table header (2 rows)
        val h1 = r
        merge("A${h1}:A${h1+1}"); merge("B${h1}:C${h1}"); merge("D${h1}:E${h1}")
        merge("F${h1}:F${h1+1}"); merge("G${h1}:G${h1+1}")
        merge("H${h1}:H${h1+1}"); merge("I${h1}:I${h1+1}")
        sb.row(r++, 20) {
            c("A",it,"Date\n[DD/MM/YY]",S5)+c("B",it,"Destination",S5)+
            c("D",it,"Time of",S5)+c("F",it,"D A",S5)+
            c("G",it,"Lodging",S5)+c("H",it,"Others",S5)+c("I",it,"Total",S5)
        }
        sb.row(r++, 18) {
            c("B",it,"From",S5)+c("C",it,"To",S5)+
            c("D",it,"Departure",S5)+c("E",it,"Arrival",S5)
        }

        var totDA = 0.0; var totLod = 0.0; var totOth = 0.0
        days.forEach { d ->
            sb.row(r++, 18) {
                c("A",it,d.display,S4)+c("B",it,d.from,S4)+c("C",it,d.to,S4)+
                c("D",it,"",S4)+c("E",it,"",S4)+
                cn("F",it,d.da)+cn("G",it,d.lodging)+cn("H",it,d.others)+cn("I",it,d.total)
            }
            totDA += d.da; totLod += d.lodging; totOth += d.others
        }
        // Blank data rows for user
        repeat(maxOf(0, 8 - days.size)) {
            sb.row(r++, 18) {
                c("A",it,"",S4)+c("B",it,"",S4)+c("C",it,"",S4)+c("D",it,"",S4)+c("E",it,"",S4)+
                cn("F",it,0.0)+cn("G",it,0.0)+cn("H",it,0.0)+cn("I",it,0.0)
            }
        }
        val grand = totDA + totLod + totOth
        sb.row(r++, 6) { "" }
        sb.row(r++, 6) { "" }
        // Page [A] total
        merge("A${r}:E${r}")
        sb.row(r++, 20) {
            c("A",it,"Total Page [A]",S8)+
            cn("F",it,totDA,S7)+cn("G",it,totLod,S7)+cn("H",it,totOth,S7)+cn("I",it,grand,S7)
        }
        sb.row(r++, 6) { "" }
        // Consolidation header
        merge("A${r}:I${r}")
        sb.row(r++, 20) { c("A",it,"Page Consolidation  (If need, Use additional pages)",S1) }
        // Consolidation rows
        for (lbl in listOf("Total Page [A]" to Triple(totDA,totLod,totOth),
                           "Total Page [B]" to Triple(0.0,0.0,0.0),
                           "Other Exp"      to Triple(0.0,0.0,0.0))) {
            merge("A${r}:E${r}")
            val v = lbl.second
            val t = v.first+v.second+v.third
            sb.row(r++, 18) {
                c("A",it,lbl.first,S4)+
                cn("F",it,v.first,S6)+cn("G",it,v.second,S6)+cn("H",it,v.third,S6)+cn("I",it,t,S6)
            }
        }
        sb.row(r++, 6) { "" }
        // Grand total
        merge("A${r}:E${r}")
        sb.row(r++, 22) {
            c("A",it,"Grand Total",S8)+
            cn("F",it,totDA,S7)+cn("G",it,totLod,S7)+cn("H",it,totOth,S7)+cn("I",it,grand,S7)
        }
        sb.row(r++, 8) { "" }
        // Guidelines
        merge("A${r}:I${r}"); sb.row(r++,22){c("A",it,"Guidelines for submission",S1)}
        merge("A${r}:I${r}"); sb.row(r++,14){c("A",it,"- Checklist",S1)}
        merge("A${r}:E${r}"); merge("F${r}:I${r}")
        sb.row(r++,13){c("A",it,"1. Approval Copy [With Emp ID/Budget Breakup]",S0)+c("F",it,"5. Electronic Payment proof (Credit card slip / UPI receipt etc.)",S0)}
        merge("A${r}:E${r}"); merge("F${r}:I${r}")
        sb.row(r++,13){c("A",it,"2. Other Expenses Statement (attach if required)",S0)+c("F",it,"6. Co travelers ID # to be mentioned by claimant for Group claim.",S0)}
        merge("A${r}:E${r}"); merge("F${r}:I${r}")
        sb.row(r++,13){c("A",it,"3. Boarding Pass (Proof of journey)",S0)+c("F",it,"7. Own transport claim - Attach supporting's (Toll/Fuel bill)",S0)}
        merge("A${r}:E${r}"); sb.row(r++,13){c("A",it,"4. Electronic Bill / Invoice (soft copy)",S0)}
        sb.row(r++,6){""}
        merge("A${r}:I${r}"); sb.row(r++,14){c("A",it,"* To the extent of bills submission tax will be exempted",S1)}
        sb.row(r++,6){""}
        merge("A${r}:I${r}"); sb.row(r++,14){c("A",it,"- Submission guidelines",S1)}
        merge("A${r}:I${r}"); sb.row(r++,13){c("A",it,"1) Within 15 days from the date of reporting to HMIE.",S0)}
        merge("A${r}:I${r}"); sb.row(r++,13){c("A",it,"2) If fail to submit: < 30 days - with HOD Recommendation  /  > 30 days - Disciplinary action",S0)}
        sb.row(r++,8){""}
        // Declaration
        merge("A${r}:I${r}")
        sb.row(r++,36){c("A",it,"I undertake that the details / documents furnished by me are correct.  If any information found to be incorrect, I am liable for any disciplinary action against me, as may be decided by Management.",S4)}
        merge("C${r}:G${r}"); sb.row(r++,18){c("C",it,COMPANY_FULL,S3)}
        sb.row(r++,8){""}

        return ws(colDefs(12.0,14.0,12.0,10.0,10.0,10.0,10.0,10.0,12.0), sb.toString(),
            mc(*merges.toTypedArray()))
    }

    // ── Sheet 2 : Total Page (B) ─────────────────────────────────────────────

    private fun sheet2(trip: Trip): String {
        val today = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
        val sb = StringBuilder(); val ml = mutableListOf<String>(); var r = 1
        fun m(ref: String) = ml.add(ref)

        m("A1:I1"); sb.row(r++,28){c("A",it,COMPANY,S2)}
        sb.row(r++,6){""}
        m("A3:I3"); sb.row(r++,22){c("A",it,"DOMESTIC BUSINESS TRIP SETTLEMENT – Page B (Continuation)",S3)}
        sb.row(r++,6){""}
        m("H5:I5"); sb.row(r++,18){c("G",it,"Date:",S1)+c("H",it,today,S1)}
        m("B6:D6"); m("H6:I6")
        sb.row(r++,20){c("A",it,"Name",S1)+c("B",it,trip.employeeName,S4)+c("E",it,"Emp ID #",S1)+c("F",it,trip.employeeId,S4)+c("G",it,"Desg",S1)+c("H",it,trip.designation,S4)}
        m("B7:D7"); m("H7:I7")
        sb.row(r++,20){c("A",it,"Budget",S1)+c("B",it,"Operating [✓]  /  Technology [  ]",S4)+c("E",it,"WBS Code",S1)+c("F",it,trip.costCenter,S4)+c("G",it,"GL #",S1)+c("H",it,"",S4)}
        m("B8:I8"); sb.row(r++,30){c("A",it,"BT Purpose",S1)+c("B",it,trip.purpose,S4)}
        val h1=r
        m("A${h1}:A${h1+1}");m("B${h1}:C${h1}");m("D${h1}:E${h1}")
        m("F${h1}:F${h1+1}");m("G${h1}:G${h1+1}");m("H${h1}:H${h1+1}");m("I${h1}:I${h1+1}")
        sb.row(r++,20){c("A",it,"Date\n[DD/MM/YY]",S5)+c("B",it,"Destination",S5)+c("D",it,"Time of",S5)+c("F",it,"D A",S5)+c("G",it,"Lodging",S5)+c("H",it,"Others",S5)+c("I",it,"Total",S5)}
        sb.row(r++,18){c("B",it,"From",S5)+c("C",it,"To",S5)+c("D",it,"Departure",S5)+c("E",it,"Arrival",S5)}
        repeat(12){sb.row(r++,18){c("A",it,"",S4)+c("B",it,"",S4)+c("C",it,"",S4)+c("D",it,"",S4)+c("E",it,"",S4)+cn("F",it,0.0)+cn("G",it,0.0)+cn("H",it,0.0)+cn("I",it,0.0)}}
        sb.row(r++,6){""}
        m("A${r}:E${r}"); sb.row(r++,20){c("A",it,"Total Page [B]",S8)+cn("F",it,0.0,S7)+cn("G",it,0.0,S7)+cn("H",it,0.0,S7)+cn("I",it,0.0,S7)}

        return ws(colDefs(12.0,14.0,12.0,10.0,10.0,10.0,10.0,10.0,12.0), sb.toString(), mc(*ml.toTypedArray()))
    }

    // ── Sheet 3 : Other Exp ──────────────────────────────────────────────────

    private fun sheet3(trip: Trip, expenses: List<Expense>): String {
        val cabs = expenses.filter { it.type == ExpenseType.CAB || it.type == ExpenseType.OTHER }
        val sb = StringBuilder(); val ml = mutableListOf<String>(); var r = 1
        fun m(ref: String) = ml.add(ref)

        m("A1:E1"); sb.row(r++,26){c("A",it,"DBT SETTLEMENT - OTHER EXPENSES",S2)}
        m("A2:E2"); sb.row(r++,16){c("A",it,"(Cab & Etc)",S3)}
        sb.row(r++,6){""}
        m("B4:D4")
        sb.row(r++,20){c("A",it,"Name",S1)+c("B",it,trip.employeeName,S4)+c("E",it,"I.D. No.",S1)+c("F",it,trip.employeeId,S4)}
        sb.row(r++,20){c("D",it,"Desg",S1)+c("E",it,trip.designation,S4)+c("F",it,"HOD",S1)}
        sb.row(r++,6){""}
        val h1=r; m("B${h1}:C${h1}")
        sb.row(r++,20){c("A",it,"Date\n[DD/MM/YY]",S5)+c("B",it,"Destination",S5)+c("D",it,"Nature of Expenses",S5)+c("E",it,"Particulars",S5)+c("F",it,"Total",S5)}
        sb.row(r++,18){c("B",it,"From",S5)+c("C",it,"To",S5)}
        var total = 0.0
        cabs.forEach { exp ->
            sb.row(r++,18){c("A",it,exp.date,S4)+c("B",it,exp.fromCity,S4)+c("C",it,exp.toCity,S4)+c("D",it,exp.type.displayName,S4)+c("E",it,exp.description,S4)+cn("F",it,exp.amount)}
            total += exp.amount
        }
        sb.row(r++,6){""}
        m("A${r}:E${r}"); sb.row(r++,20){c("A",it,"Total",S8)+cn("F",it,total,S7)}
        sb.row(r++,6){""}
        m("A${r}:F${r}"); sb.row(r++,16){c("A",it,COMPANY_FULL,S3)}

        return ws(colDefs(12.0,14.0,14.0,16.0,28.0,12.0), sb.toString(), mc(*ml.toTypedArray()))
    }

    // ── Sheet 4 : DA_Tax exmp proofs ─────────────────────────────────────────

    private fun sheet4(trip: Trip, expenses: List<Expense>): String {
        val bills = expenses.filter { it.type == ExpenseType.FOOD || it.type == ExpenseType.OTHER }
        val cabs  = expenses.filter { it.type == ExpenseType.CAB }
        val totalDA = buildDayRows(expenses).sumOf { it.da }
        val sb = StringBuilder(); val ml = mutableListOf<String>(); var r = 1
        fun m(ref: String) = ml.add(ref)

        m("A1:C1"); m("D1:E1"); m("G1:H1")
        sb.row(r++,26){c("A",it,"BUSINESS TRIP EXPENSE BILL SUBMISSION REPORT",S2)+c("D",it,"Employee Name",S1)+c("F",it,trip.employeeName,S4)+c("G",it,"Emp ID",S1)+c("H",it,trip.employeeId,S4)}
        sb.row(r++,6){""}
        m("C3:D3")
        sb.row(r++,20){c("C",it,"Dept. / Section",S1)+c("E",it,trip.department,S4)+c("G",it,"Domestic",S1)}
        sb.row(r++,6){""}
        m("D5:G5")
        sb.row(r++,20){c("A",it,"S. No",S5)+c("B",it,"Inv #",S5)+c("C",it,"Date",S5)+c("D",it,"Particulars",S5)+c("H",it,"INR",S5)}

        var billTotal = 0.0; var sno = 1
        bills.forEach { exp ->
            m("D${r}:G${r}")
            sb.row(r++,18){c("A",it,"${sno++}",S4)+c("B",it,exp.receiptRef,S4)+c("C",it,exp.date,S4)+c("D",it,exp.description,S4)+cn("H",it,exp.amount)}
            billTotal += exp.amount
        }
        if (cabs.isNotEmpty()) {
            val cabSum = cabs.sumOf { it.amount }
            m("D${r}:G${r}"); sb.row(r++,18){c("A",it,"${sno++}",S4)+c("B",it,"Cab Bills",S4)+c("C",it,"",S4)+c("D",it,"Local Travel",S4)+cn("H",it,cabSum)}
            billTotal += cabSum
        }
        repeat(3){m("D${r}:G${r}"); sb.row(r++,18){c("A",it,"",S4)+c("B",it,"",S4)+c("C",it,"",S4)+c("D",it,"",S4)+cn("H",it,0.0)}}
        m("A${r}:G${r}"); sb.row(r++,20){c("A",it,"Total",S8)+cn("H",it,billTotal,S7)}
        sb.row(r++,6){""}
        m("A${r}:H${r}"); sb.row(r++,28){c("A",it,"I hereby declare that the above information is true to the best of my knowledge and amounts are actually incurred for performing official duties.",S4)}
        sb.row(r++,6){""}
        m("B${r}:H${r}"); sb.row(r++,18){c("B",it,"OFFICE (FI) USE ONLY",S1)}
        sb.row(r++,6){""}
        m("A${r}:G${r}"); sb.row(r++,18){c("A",it,"Total DA paid",S1)+c("G",it,"Rs",S1)+cn("H",it,totalDA)}
        m("A${r}:G${r}"); sb.row(r++,18){c("A",it,"Exempt Amount [Bills submitted]",S1)+c("G",it,"Rs",S1)+cn("H",it,billTotal)}
        m("A${r}:G${r}"); sb.row(r++,18){c("A",it,"Taxable amount",S1)+c("G",it,"Rs",S1)+cn("H",it,totalDA-billTotal)}

        return ws(colDefs(6.0,14.0,11.0,30.0,8.0,8.0,8.0,12.0), sb.toString(), mc(*ml.toTypedArray()))
    }

    // ── Build day rows ────────────────────────────────────────────────────────

    private fun buildDayRows(expenses: List<Expense>): List<DayRow> {
        if (expenses.isEmpty()) return emptyList()
        val dfP = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
        val dfD = SimpleDateFormat("dd-MMM-yy",   Locale.ENGLISH)

        val dates = expenses.mapNotNull { try { dfP.parse(it.date) } catch (e: Exception) { null } }
            .distinctBy { dfD.format(it) }.sortedBy { it }
        if (dates.isEmpty()) return emptyList()

        val runs = mutableListOf<MutableList<Date>>()
        var cur  = mutableListOf<Date>()
        dates.forEach { d ->
            if (cur.isEmpty() || (d.time - cur.last().time) / 86400000L <= 3) cur.add(d)
            else { runs.add(cur); cur = mutableListOf(d) }
        }
        if (cur.isNotEmpty()) runs.add(cur)

        val result = mutableListOf<DayRow>()
        val cal = Calendar.getInstance()
        runs.forEach { run ->
            cal.time = run.first(); var first = true
            while (!cal.time.after(run.last())) {
                val now = cal.time.clone() as Date
                val key = dfD.format(now)
                val dayExp = expenses.filter { e ->
                    try { dfD.format(dfP.parse(e.date)!!) == key } catch (ex: Exception) { false }
                }
                val flight  = dayExp.firstOrNull { it.type == ExpenseType.FLIGHT }
                val lodging = dayExp.filter { it.type == ExpenseType.HOTEL }.sumOf { it.amount }
                val others  = dayExp.filter { it.type == ExpenseType.CAB   }.sumOf { it.amount }
                result.add(DayRow(key, flight?.fromCity ?: "", flight?.toCity ?: "",
                    if (first) DA_FIRST_DAY else DA_OTHER, lodging, others))
                cal.add(Calendar.DAY_OF_MONTH, 1); first = false
            }
        }
        return result
    }
}
