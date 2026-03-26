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

/**
 * Generates HMIE DBT Settlement Excel (.xlsx) with 3 sheets:
 *  Sheet 1 – DBT Page_A  (main settlement form)
 *  Sheet 2 – Other Exp   (detailed other expenses)
 *  Sheet 3 – DA_Tax exmp proofs (DA computation & food tax-exempt list)
 *
 * Uses direct OOXML / ZIP generation – no Apache POI dependency needed.
 */
class ExcelGenerator(private val context: Context) {

    fun generate(trip: Trip, expenses: List<Expense>): File {
        val sdf = SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH)
        val name = trip.employeeName.replace(" ", "_")
        val date = sdf.format(Date())
        val file = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "DBT_Settlement_${name}_$date.xlsx"
        )
        file.parentFile?.mkdirs()

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            addEntry(zip, "[Content_Types].xml", contentTypes())
            addEntry(zip, "_rels/.rels", rootRels())
            addEntry(zip, "xl/workbook.xml", workbook())
            addEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            addEntry(zip, "xl/styles.xml", styles())
            addEntry(zip, "xl/worksheets/sheet1.xml", sheet1(trip, expenses))
            addEntry(zip, "xl/worksheets/sheet2.xml", sheet2(trip, expenses))
            addEntry(zip, "xl/worksheets/sheet3.xml", sheet3(trip, expenses))
        }
        return file
    }

    // ── ZIP helper ──────────────────────────────────────────────────────────

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // ── OOXML boilerplate ────────────────────────────────────────────────────

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml"
    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml"
    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml"
    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml"
    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml"
    ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbook() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="DBT Page_A"         sheetId="1" r:id="rId1"/>
    <sheet name="Other Exp"          sheetId="2" r:id="rId2"/>
    <sheet name="DA_Tax exmp proofs" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"    Target="styles.xml"/>
</Relationships>"""

    /** Styles: 0=default, 1=bold, 2=bold+bg header, 3=currency, 4=bold right-align */
    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="10"/><name val="Calibri"/></font>
    <font><b/><sz val="10"/><name val="Calibri"/></font>
    <font><b/><sz val="12"/><name val="Calibri"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF4472C4"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FF000000"/></left>
      <right style="thin"><color rgb="FF000000"/></right>
      <top style="thin"><color rgb="FF000000"/></top>
      <bottom style="thin"><color rgb="FF000000"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="6">
    <xf numFmtId="0"   fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0"   fontId="1" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0"   fontId="2" fillId="2" borderId="1" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0"/>
    <xf numFmtId="0"   fontId="1" fillId="0" borderId="1" xfId="0"><alignment horizontal="center"/></xf>
    <xf numFmtId="164" fontId="1" fillId="0" borderId="1" xfId="0"/>
  </cellXfs>
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00"/>
  </numFmts>
</styleSheet>"""

    // ── Cell builders ────────────────────────────────────────────────────────

    /** Inline-string cell (no shared strings needed). s=style index */
    private fun c(col: String, row: Int, value: String, s: Int = 0): String {
        val safe = value.xmlEscape()
        return """<c r="$col$row" t="inlineStr" s="$s"><is><t>$safe</t></is></c>"""
    }

    /** Number cell */
    private fun cn(col: String, row: Int, value: Double, s: Int = 3): String =
        """<c r="$col$row" s="$s"><v>$value</v></c>"""

    private fun String.xmlEscape() = this
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    // ── Sheet 1: DBT Page_A ──────────────────────────────────────────────────

    private fun sheet1(trip: Trip, expenses: List<Expense>): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""<cols>
  <col min="1" max="1" width="5"/>
  <col min="2" max="2" width="30"/>
  <col min="3" max="3" width="18"/>
  <col min="4" max="4" width="18"/>
  <col min="5" max="5" width="20"/>
  <col min="6" max="6" width="18"/>
  <col min="7" max="7" width="18"/>
</cols>""")
        sb.appendLine("<sheetData>")

        var r = 1
        // Title
        sb.row(r++) { c("A", it, "HINDUSTAN MACHINE TOOLS LIMITED - INSTRUMENT ENGINEERING DIVISION", s = 2) }
        sb.row(r++) { c("A", it, "DBT SETTLEMENT FORM", s = 2) }
        sb.row(r++) { c("A", it, "") } // blank

        // Employee details
        sb.row(r++) {
            c("A", it, "Employee Name:", 1) + c("B", it, trip.employeeName) +
            c("D", it, "Employee No:", 1) + c("E", it, trip.employeeId)
        }
        sb.row(r++) {
            c("A", it, "Department:", 1) + c("B", it, trip.department) +
            c("D", it, "Designation:", 1) + c("E", it, trip.designation)
        }
        sb.row(r++) {
            c("A", it, "Cost Center:", 1) + c("B", it, trip.costCenter)
        }
        sb.row(r++) { c("A", it, "") } // blank

        // Trip details
        sb.row(r++) { c("A", it, "Purpose of Visit:", 1) + c("B", it, trip.purpose) }
        sb.row(r++) { c("A", it, "Period:", 1) + c("B", it, "${trip.startDate} to ${trip.endDate}") }
        sb.row(r++) { c("A", it, "Route:", 1) + c("B", it, trip.route) }
        sb.row(r++) { c("A", it, "") }

        // Expense table header
        sb.row(r++) { c("A", it, "EXPENSE DETAILS", 1) }
        sb.row(r++) {
            c("A", it, "Sl.", 4) + c("B", it, "Particulars", 4) + c("C", it, "Date", 4) +
            c("D", it, "From", 4) + c("E", it, "To", 4) +
            c("F", it, "Claimed (Rs.)", 4) + c("G", it, "Eligible (Rs.)", 4)
        }

        var sl = 1
        var totalClaimed = 0.0
        expenses.forEach { exp ->
            sb.row(r++) {
                c("A", it, sl++.toString(), 3) +
                c("B", it, exp.type.displayName, 3) +
                c("C", it, exp.date, 3) +
                c("D", it, exp.fromCity, 3) +
                c("E", it, exp.toCity, 3) +
                cn("F", it, exp.amount, 3) +
                cn("G", it, exp.amount, 3)
            }
            totalClaimed += exp.amount
        }

        // Totals row
        sb.row(r++) {
            c("A", it, "", 5) + c("B", it, "TOTAL", 5) +
            c("C", it, "") + c("D", it, "") + c("E", it, "") +
            cn("F", it, totalClaimed, 5) + cn("G", it, totalClaimed, 5)
        }
        sb.row(r++) { c("A", it, "") }

        // Receipt references
        sb.row(r++) { c("A", it, "Receipt References:", 1) }
        expenses.filter { it.receiptRef.isNotBlank() }.forEach { exp ->
            sb.row(r++) { c("B", it, "${exp.type.displayName}: ${exp.receiptRef}") }
        }
        sb.row(r++) { c("A", it, "") }

        // Declaration
        sb.row(r++) {
            c("A", it, "I hereby declare that the above expenses were actually incurred by me in the official performance of my duties.")
        }
        sb.row(r++) { c("A", it, "") }
        sb.row(r++) {
            c("A", it, "Employee Signature: _____________________") +
            c("D", it, "Date: _____________________")
        }
        sb.row(r++) { c("A", it, "") }
        sb.row(r++) {
            c("A", it, "Approved by: ____________________________") +
            c("D", it, "Date: _____________________")
        }
        sb.row(r++) {
            c("A", it, "HR / Finance: ___________________________") +
            c("D", it, "Date: _____________________")
        }

        sb.appendLine("</sheetData>")
        sb.appendLine("</worksheet>")
        return sb.toString()
    }

    // ── Sheet 2: Other Exp ───────────────────────────────────────────────────

    private fun sheet2(trip: Trip, expenses: List<Expense>): String {
        val others = expenses.filter { it.type == ExpenseType.OTHER || it.type == ExpenseType.CAB || it.type == ExpenseType.FOOD }
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""<cols>
  <col min="1" max="1" width="5"/>
  <col min="2" max="2" width="18"/>
  <col min="3" max="3" width="35"/>
  <col min="4" max="4" width="18"/>
  <col min="5" max="5" width="25"/>
</cols>""")
        sb.appendLine("<sheetData>")

        var r = 1
        sb.row(r++) { c("A", it, "OTHER EXPENSES – DETAILED STATEMENT", 2) }
        sb.row(r++) {
            c("A", it, "Employee: ${trip.employeeName}", 1) +
            c("C", it, "Period: ${trip.startDate} to ${trip.endDate}", 1)
        }
        sb.row(r++) { c("A", it, "") }
        sb.row(r++) {
            c("A", it, "Sl.", 4) + c("B", it, "Date", 4) +
            c("C", it, "Description", 4) + c("D", it, "Amount (Rs.)", 4) +
            c("E", it, "Receipt Reference", 4)
        }

        var sl = 1
        var total = 0.0
        others.forEach { exp ->
            sb.row(r++) {
                c("A", it, sl++.toString(), 3) + c("B", it, exp.date, 3) +
                c("C", it, "${exp.type.displayName}: ${exp.description}", 3) +
                cn("D", it, exp.amount, 3) + c("E", it, exp.receiptRef, 3)
            }
            total += exp.amount
        }
        sb.row(r++) {
            c("A", it, "", 5) + c("B", it, "", 5) + c("C", it, "TOTAL", 5) +
            cn("D", it, total, 5) + c("E", it, "", 5)
        }

        sb.appendLine("</sheetData>")
        sb.appendLine("</worksheet>")
        return sb.toString()
    }

    // ── Sheet 3: DA_Tax exmp proofs ──────────────────────────────────────────

    private fun sheet3(trip: Trip, expenses: List<Expense>): String {
        val foodExp = expenses.filter { it.type == ExpenseType.FOOD }
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""<cols>
  <col min="1" max="1" width="18"/>
  <col min="2" max="2" width="20"/>
  <col min="3" max="3" width="15"/>
  <col min="4" max="4" width="18"/>
  <col min="5" max="5" width="25"/>
</cols>""")
        sb.appendLine("<sheetData>")

        var r = 1
        sb.row(r++) { c("A", it, "DAILY ALLOWANCE & TAX EXEMPTION PROOFS", 2) }
        sb.row(r++) { c("A", it, "") }

        // DA Section
        sb.row(r++) { c("A", it, "SECTION A – DAILY ALLOWANCE (DA) COMPUTATION", 1) }
        sb.row(r++) {
            c("A", it, "Date", 4) + c("B", it, "City / Location", 4) +
            c("C", it, "DA Rate (Rs.)", 4) + c("D", it, "DA Amount (Rs.)", 4) +
            c("E", it, "Remarks", 4)
        }

        // Generate a row per trip day
        val daRate = 500.0 // standard DA rate – adjust as per HMIE policy
        val startParts = trip.startDate.split("-")
        val endParts = trip.endDate.split("-")
        var totalDA = 0.0
        val cities = trip.route.split("-")

        try {
            val sdf = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
            val start = sdf.parse(trip.startDate) ?: Date()
            val end = sdf.parse(trip.endDate) ?: Date()
            val cal = Calendar.getInstance().apply { time = start }
            var dayIdx = 0
            while (!cal.time.after(end)) {
                val dateStr = sdf.format(cal.time)
                val city = cities.getOrElse(dayIdx) { cities.lastOrNull() ?: "" }
                sb.row(r++) {
                    c("A", it, dateStr, 3) + c("B", it, city, 3) +
                    cn("C", it, daRate, 3) + cn("D", it, daRate, 3) +
                    c("E", it, "Official duty", 3)
                }
                totalDA += daRate
                cal.add(Calendar.DAY_OF_MONTH, 1)
                dayIdx++
            }
        } catch (e: Exception) {
            // Fallback if date parsing fails
            sb.row(r++) {
                c("A", it, trip.startDate, 3) + c("B", it, trip.route, 3) +
                cn("C", it, daRate, 3) + cn("D", it, daRate, 3) +
                c("E", it, "Official duty", 3)
            }
            totalDA = daRate
        }

        sb.row(r++) {
            c("A", it, "TOTAL DA", 5) + c("B", it, "", 5) + c("C", it, "", 5) +
            cn("D", it, totalDA, 5) + c("E", it, "", 5)
        }
        sb.row(r++) { c("A", it, "NOTE: DA is exempt from tax under Section 10(14) of Income Tax Act, 1961.", 1) }
        sb.row(r++) { c("A", it, "") }

        // Food / Tax exempt section
        sb.row(r++) { c("A", it, "SECTION B – FOOD EXPENSES (Tax Exempt u/s 10)", 1) }
        sb.row(r++) {
            c("A", it, "Date", 4) + c("B", it, "Description", 4) +
            c("C", it, "Amount (Rs.)", 4) + c("D", it, "Receipt Reference", 4) +
            c("E", it, "Remarks", 4)
        }
        var totalFood = 0.0
        foodExp.forEach { exp ->
            sb.row(r++) {
                c("A", it, exp.date, 3) + c("B", it, exp.description, 3) +
                cn("C", it, exp.amount, 3) + c("D", it, exp.receiptRef, 3) +
                c("E", it, exp.fromCity, 3)
            }
            totalFood += exp.amount
        }
        sb.row(r++) {
            c("A", it, "TOTAL FOOD", 5) + c("B", it, "", 5) +
            cn("C", it, totalFood, 5) + c("D", it, "", 5) + c("E", it, "", 5)
        }

        sb.appendLine("</sheetData>")
        sb.appendLine("</worksheet>")
        return sb.toString()
    }

    // ── Row helper ────────────────────────────────────────────────────────────

    private fun StringBuilder.row(rowNum: Int, cells: (Int) -> String) {
        appendLine("""<row r="$rowNum">${cells(rowNum)}</row>""")
    }
}
