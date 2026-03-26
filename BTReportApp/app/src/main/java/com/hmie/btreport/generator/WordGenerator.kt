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
 * Generates a Business Trip Report (.docx) using direct OOXML / ZIP generation.
 * No Apache POI dependency required.
 */
class WordGenerator(private val context: Context) {

    fun generate(trip: Trip, expenses: List<Expense>): File {
        val sdf = SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH)
        val name = trip.employeeName.replace(" ", "_")
        val date = sdf.format(Date())
        val file = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "BT_Report_${name}_$date.docx"
        )
        file.parentFile?.mkdirs()

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            addEntry(zip, "[Content_Types].xml", contentTypes())
            addEntry(zip, "_rels/.rels", rootRels())
            addEntry(zip, "word/document.xml", document(trip, expenses))
            addEntry(zip, "word/styles.xml", styles())
            addEntry(zip, "word/settings.xml", settings())
            addEntry(zip, "word/_rels/document.xml.rels", documentRels())
        }
        return file
    }

    private fun addEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
</Types>"""

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>"""

    private fun documentRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"
    Target="styles.xml"/>
  <Relationship Id="rId2"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings"
    Target="settings.xml"/>
</Relationships>"""

    private fun settings() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:defaultTabStop w:val="720"/>
</w:settings>"""

    private fun styles() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:styleId="Normal" w:default="1">
    <w:name w:val="Normal"/>
    <w:rPr><w:sz w:val="20"/><w:szCs w:val="20"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:rPr><w:b/><w:sz w:val="28"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:rPr><w:b/><w:sz w:val="24"/></w:rPr>
  </w:style>
</w:styles>"""

    // ── Main document body ────────────────────────────────────────────────────

    private fun document(trip: Trip, expenses: List<Expense>): String {
        val today = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:w14="http://schemas.microsoft.com/office/word/2010/wordml">
<w:body>""")

        // ── Title ──
        sb.centeredPara("HINDUSTAN MACHINE TOOLS LIMITED", bold = true, size = 28)
        sb.centeredPara("INSTRUMENT ENGINEERING DIVISION", bold = true, size = 22)
        sb.centeredPara("BUSINESS TRIP REPORT", bold = true, size = 26)
        sb.para("")

        // ── Report date ──
        sb.para("Report Date: $today", bold = false)
        sb.para("")

        // ── Employee details ──
        sb.sectionHeading("EMPLOYEE DETAILS")
        sb.labelValue("Name", trip.employeeName)
        sb.labelValue("Employee No", trip.employeeId)
        sb.labelValue("Department", trip.department)
        sb.labelValue("Designation", trip.designation)
        sb.labelValue("Cost Center", trip.costCenter)
        sb.para("")

        // ── Trip details ──
        sb.sectionHeading("TRIP DETAILS")
        sb.labelValue("Purpose of Visit", trip.purpose)
        sb.labelValue("Period", "${trip.startDate}  to  ${trip.endDate}")
        sb.labelValue("Route", trip.route)
        sb.para("")

        // ── Travel itinerary table ──
        sb.sectionHeading("TRAVEL ITINERARY")
        sb.append(travelTable(expenses))
        sb.para("")

        // ── Expense summary table ──
        sb.sectionHeading("EXPENSE SUMMARY")
        sb.append(expenseSummaryTable(expenses))
        sb.para("")

        // ── Receipt list ──
        val receipts = expenses.filter { it.receiptRef.isNotBlank() }
        if (receipts.isNotEmpty()) {
            sb.sectionHeading("RECEIPTS ATTACHED")
            receipts.forEachIndexed { i, exp ->
                sb.para("${i + 1}. ${exp.type.displayName}  –  ${exp.receiptRef}  (${exp.date})  Rs. ${"%.2f".format(exp.amount)}")
            }
            sb.para("")
        }

        // ── Declaration ──
        sb.sectionHeading("DECLARATION")
        sb.para(
            "I hereby declare that the expenses listed above were actually incurred by me in the " +
            "official performance of my duties and that the amounts claimed are correct to the best " +
            "of my knowledge."
        )
        sb.para("")
        sb.para("")
        sb.para("Employee Signature: _________________________    Date: ________________")
        sb.para("")
        sb.para("Approved by:        _________________________    Date: ________________")
        sb.para("")
        sb.para("HR / Finance:       _________________________    Date: ________________")

        // Word requires a sectPr at the end
        sb.append("""<w:sectPr>
  <w:pgSz w:w="12240" w:h="15840"/>
  <w:pgMar w:top="1440" w:right="1080" w:bottom="1440" w:left="1080"/>
</w:sectPr>""")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    // ── Travel itinerary table ────────────────────────────────────────────────

    private fun travelTable(expenses: List<Expense>): String {
        val flights = expenses.filter { it.type == ExpenseType.FLIGHT }
        val sb = StringBuilder()
        sb.append("""<w:tbl>
<w:tblPr>
  <w:tblW w:w="9360" w:type="dxa"/>
  <w:tblBorders>
    <w:top    w:val="single" w:sz="4" w:color="000000"/>
    <w:left   w:val="single" w:sz="4" w:color="000000"/>
    <w:bottom w:val="single" w:sz="4" w:color="000000"/>
    <w:right  w:val="single" w:sz="4" w:color="000000"/>
    <w:insideH w:val="single" w:sz="4" w:color="000000"/>
    <w:insideV w:val="single" w:sz="4" w:color="000000"/>
  </w:tblBorders>
</w:tblPr>""")
        // Header
        sb.append(tableRow(listOf("Date", "From", "To", "Mode", "Flight / Ref No", "Amt (Rs.)"), header = true))
        // Data rows
        expenses.filter { it.type == ExpenseType.FLIGHT || it.type == ExpenseType.CAB }.forEach { exp ->
            sb.append(tableRow(listOf(
                exp.date, exp.fromCity, exp.toCity,
                if (exp.type == ExpenseType.FLIGHT) "Air" else "Road",
                exp.receiptRef,
                "%.2f".format(exp.amount)
            )))
        }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    // ── Expense summary table ─────────────────────────────────────────────────

    private fun expenseSummaryTable(expenses: List<Expense>): String {
        val grouped = expenses.groupBy { it.type }
        val sb = StringBuilder()
        sb.append("""<w:tbl>
<w:tblPr>
  <w:tblW w:w="9360" w:type="dxa"/>
  <w:tblBorders>
    <w:top    w:val="single" w:sz="4" w:color="000000"/>
    <w:left   w:val="single" w:sz="4" w:color="000000"/>
    <w:bottom w:val="single" w:sz="4" w:color="000000"/>
    <w:right  w:val="single" w:sz="4" w:color="000000"/>
    <w:insideH w:val="single" w:sz="4" w:color="000000"/>
    <w:insideV w:val="single" w:sz="4" w:color="000000"/>
  </w:tblBorders>
</w:tblPr>""")
        sb.append(tableRow(listOf("Category", "Amount (Rs.)", "Receipt References"), header = true))
        var total = 0.0
        ExpenseType.values().forEach { type ->
            val list = grouped[type] ?: return@forEach
            val sum = list.sumOf { it.amount }
            total += sum
            sb.append(tableRow(listOf(
                type.displayName,
                "%.2f".format(sum),
                list.filter { it.receiptRef.isNotBlank() }.joinToString(", ") { it.receiptRef }
            )))
        }
        sb.append(tableRow(listOf("TOTAL", "%.2f".format(total), ""), header = true))
        sb.append("</w:tbl>")
        return sb.toString()
    }

    // ── OOXML helpers ─────────────────────────────────────────────────────────

    private fun tableRow(cells: List<String>, header: Boolean = false): String {
        val sb = StringBuilder("<w:tr>")
        cells.forEach { text ->
            sb.append("""<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/></w:tcPr>
<w:p><w:pPr><w:jc w:val="${if (header) "center" else "left"}"/></w:pPr>
<w:r><w:rPr>${if (header) "<w:b/>" else ""}</w:rPr>
<w:t xml:space="preserve">${text.xmlEscape()}</w:t></w:r></w:p></w:tc>""")
        }
        sb.append("</w:tr>")
        return sb.toString()
    }

    private fun StringBuilder.para(text: String, bold: Boolean = false) {
        appendLine("""<w:p><w:r><w:rPr>${if (bold) "<w:b/>" else ""}</w:rPr>
<w:t xml:space="preserve">${text.xmlEscape()}</w:t></w:r></w:p>""")
    }

    private fun StringBuilder.centeredPara(text: String, bold: Boolean = false, size: Int = 20) {
        appendLine("""<w:p>
<w:pPr><w:jc w:val="center"/></w:pPr>
<w:r><w:rPr>${if (bold) "<w:b/>" else ""}<w:sz w:val="$size"/><w:szCs w:val="$size"/></w:rPr>
<w:t>${text.xmlEscape()}</w:t></w:r></w:p>""")
    }

    private fun StringBuilder.sectionHeading(text: String) {
        appendLine("""<w:p>
<w:pPr><w:pStyle w:val="Heading2"/></w:pPr>
<w:r><w:rPr><w:b/><w:sz w:val="24"/></w:rPr>
<w:t>${text.xmlEscape()}</w:t></w:r></w:p>""")
    }

    private fun StringBuilder.labelValue(label: String, value: String) {
        appendLine("""<w:p><w:r>
<w:rPr><w:b/></w:rPr><w:t xml:space="preserve">${"%-20s".format("$label:").xmlEscape()}</w:t></w:r>
<w:r><w:t>${value.xmlEscape()}</w:t></w:r></w:p>""")
    }

    private fun String.xmlEscape() = this
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
