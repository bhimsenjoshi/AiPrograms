package com.hmie.btreport.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hmie.btreport.model.Expense
import com.hmie.btreport.model.ExpenseType
import com.hmie.btreport.model.Trip
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates BT_Report_<Name>_<Date>.docx with embedded receipt images.
 * Uses direct OOXML / ZIP – no Apache POI needed.
 */
class WordGenerator(private val context: Context) {

    companion object {
        const val COMPANY      = "HMIE"
        const val COMPANY_FULL = "HMIE"
        // 1 inch = 914400 EMU
        const val IMG_MAX_W_EMU = 3657600L   // 4 inches max width
        const val IMG_MAX_H_EMU = 4572000L   // 5 inches max height
    }

    private data class ImgEntry(val rId: String, val fileName: String, val bytes: ByteArray,
                                val cx: Long, val cy: Long, val expenseLabel: String)

    fun generate(trip: Trip, expenses: List<Expense>): File {
        val name = trip.employeeName.replace(" ", "_")
        val date = SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH).format(Date())
        val file = File(
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "BT_Report_${name}_$date.docx"
        )
        file.parentFile?.mkdirs()

        // Collect receipt images
        val images = mutableListOf<ImgEntry>()
        var imgIdx = 1
        expenses.forEach { exp ->
            val path = exp.imageUri ?: return@forEach
            val imgFile = File(path)
            if (!imgFile.exists()) return@forEach
            val bytes = loadResized(path) ?: return@forEach
            val (cx, cy) = imageDims(path)
            val rId = "rId${imgIdx + 2}"   // rId1=styles, rId2=settings, rId3+ = images
            images.add(ImgEntry(rId, "image${imgIdx}.jpg", bytes, cx, cy,
                "${exp.type.displayName} – ${exp.date}" +
                (if (exp.fromCity.isNotBlank()) " (${exp.fromCity}→${exp.toCity})" else "") +
                " Rs.${"%.0f".format(exp.amount)}"))
            imgIdx++
        }

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zeText(zip, "[Content_Types].xml", contentTypes(images))
            zeText(zip, "_rels/.rels",          rootRels())
            zeText(zip, "word/document.xml",    document(trip, expenses, images))
            zeText(zip, "word/styles.xml",      styles())
            zeText(zip, "word/settings.xml",    settings())
            zeText(zip, "word/_rels/document.xml.rels", documentRels(images))
            images.forEach { img ->
                zeBytes(zip, "word/media/${img.fileName}", img.bytes)
            }
        }
        return file
    }

    private fun zeText(zip: ZipOutputStream, name: String, s: String) {
        zip.putNextEntry(ZipEntry(name)); zip.write(s.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }
    private fun zeBytes(zip: ZipOutputStream, name: String, b: ByteArray) {
        zip.putNextEntry(ZipEntry(name)); zip.write(b); zip.closeEntry()
    }

    // ── Image helpers ─────────────────────────────────────────────────────────

    private fun loadResized(path: String): ByteArray? = try {
        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > 1200 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().also { it.inSampleSize = sample })
            ?: return null
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    } catch (e: Exception) { null }

    private fun imageDims(path: String): Pair<Long, Long> {
        val opts = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        val w = opts.outWidth.coerceAtLeast(1)
        val h = opts.outHeight.coerceAtLeast(1)
        val cx = IMG_MAX_W_EMU
        var cy = (cx.toDouble() * h / w).toLong()
        if (cy > IMG_MAX_H_EMU) {
            cy = IMG_MAX_H_EMU
        }
        return Pair(cx, cy)
    }

    // ── OOXML boilerplate ─────────────────────────────────────────────────────

    private fun contentTypes(images: List<ImgEntry>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml"  ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml"   ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>""")
        images.forEach { append("""
  <Override PartName="/word/media/${it.fileName}" ContentType="image/jpeg"/>""") }
        append("\n</Types>")
    }

    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun documentRels(images: List<ImgEntry>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles"   Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>""")
        images.forEach {
            append("""
  <Relationship Id="${it.rId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/${it.fileName}"/>""")
        }
        append("\n</Relationships>")
    }

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
</w:styles>"""

    // ── Document body ─────────────────────────────────────────────────────────

    private fun document(trip: Trip, expenses: List<Expense>, images: List<ImgEntry>): String {
        val today = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH).format(Date())
        val sb    = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"
            xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
            xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
            xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
<w:body>""")

        // ── Header ──
        sb.centerPara(COMPANY, bold = true, sz = 28)
        sb.centerPara("INSTRUMENT ENGINEERING DIVISION", bold = true, sz = 22)
        sb.centerPara("BUSINESS TRIP REPORT", bold = true, sz = 26)
        sb.para("")
        sb.para("Report Date: $today")
        sb.para("")

        // ── Employee details ──
        sb.heading("EMPLOYEE DETAILS")
        sb.labelVal("Name",           trip.employeeName)
        sb.labelVal("Employee ID",    trip.employeeId)
        sb.labelVal("Department",     trip.department)
        sb.labelVal("Designation",    trip.designation)
        sb.labelVal("Cost Center",    trip.costCenter)
        sb.para("")

        // ── Trip details ──
        sb.heading("TRIP DETAILS")
        sb.labelVal("Purpose",  trip.purpose)
        sb.labelVal("Period",   "${trip.startDate}  to  ${trip.endDate}")
        sb.labelVal("Route",    trip.route)
        sb.para("")

        // ── Travel itinerary ──
        sb.heading("TRAVEL ITINERARY")
        sb.append(travelTable(expenses))
        sb.para("")

        // ── Expense summary ──
        sb.heading("EXPENSE SUMMARY")
        sb.append(expSummaryTable(expenses))
        sb.para("")

        // ── Receipt images ──
        if (images.isNotEmpty()) {
            sb.heading("RECEIPT IMAGES")
            images.forEachIndexed { idx, img ->
                sb.para("Receipt ${idx + 1}: ${img.expenseLabel}", bold = true)
                sb.append(inlineImage(img, idx + 1))
                sb.para("")
            }
        }

        // ── Declaration ──
        sb.heading("DECLARATION")
        sb.para("I hereby declare that the expenses listed above were actually incurred by me in the " +
                "official performance of my duties and that the amounts claimed are correct.")
        sb.para("")
        sb.para("")
        sb.para("Employee Signature: _________________________    Date: ________________")
        sb.para("")
        sb.para("Approved by:        _________________________    Date: ________________")
        sb.para("")
        sb.para("HR / Finance:       _________________________    Date: ________________")
        sb.para("")

        sb.append("""<w:sectPr>
  <w:pgSz w:w="12240" w:h="15840"/>
  <w:pgMar w:top="1080" w:right="900" w:bottom="1080" w:left="900"/>
</w:sectPr>""")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    // ── Inline image ──────────────────────────────────────────────────────────

    private fun inlineImage(img: ImgEntry, id: Int): String = """
<w:p><w:r><w:drawing>
  <wp:inline distT="0" distB="0" distL="0" distR="0">
    <wp:extent cx="${img.cx}" cy="${img.cy}"/>
    <wp:effectExtent l="0" t="0" r="0" b="0"/>
    <wp:docPr id="$id" name="Receipt $id"/>
    <wp:cNvGraphicFramePr>
      <a:graphicFrameLocks xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" noChangeAspect="1"/>
    </wp:cNvGraphicFramePr>
    <a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
      <a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">
        <pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">
          <pic:nvPicPr>
            <pic:cNvPr id="$id" name="Receipt $id"/>
            <pic:cNvPicPr/>
          </pic:nvPicPr>
          <pic:blipFill>
            <a:blip r:embed="${img.rId}"/>
            <a:stretch><a:fillRect/></a:stretch>
          </pic:blipFill>
          <pic:spPr>
            <a:xfrm><a:off x="0" y="0"/><a:ext cx="${img.cx}" cy="${img.cy}"/></a:xfrm>
            <a:prstGeom prst="rect"><a:avLst/></a:prstGeom>
          </pic:spPr>
        </pic:pic>
      </a:graphicData>
    </a:graphic>
  </wp:inline>
</w:drawing></w:r></w:p>"""

    // ── Tables ────────────────────────────────────────────────────────────────

    private fun travelTable(expenses: List<Expense>): String {
        val travel = expenses.filter { it.type == ExpenseType.FLIGHT || it.type == ExpenseType.CAB }
        val sb = StringBuilder()
        sb.append(tblStart())
        sb.append(tblRow(listOf("Date","From","To","Mode","Ref / Receipt","Amount (Rs.)"), header = true))
        travel.forEach { exp ->
            sb.append(tblRow(listOf(exp.date, exp.fromCity, exp.toCity,
                if (exp.type == ExpenseType.FLIGHT) "Air" else "Cab",
                exp.receiptRef, "%.2f".format(exp.amount))))
        }
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun expSummaryTable(expenses: List<Expense>): String {
        val grouped = expenses.groupBy { it.type }
        val sb = StringBuilder()
        sb.append(tblStart())
        sb.append(tblRow(listOf("Category","Amount (Rs.)","Receipts"), header = true))
        var total = 0.0
        ExpenseType.values().forEach { type ->
            val list = grouped[type] ?: return@forEach
            val sum  = list.sumOf { it.amount }; total += sum
            sb.append(tblRow(listOf(type.displayName, "%.2f".format(sum),
                list.filter { it.receiptRef.isNotBlank() }.joinToString(", ") { it.receiptRef })))
        }
        sb.append(tblRow(listOf("TOTAL", "%.2f".format(total), ""), header = true))
        sb.append("</w:tbl>")
        return sb.toString()
    }

    private fun tblStart() = """<w:tbl>
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
</w:tblPr>"""

    private fun tblRow(cells: List<String>, header: Boolean = false): String {
        val sb = StringBuilder("<w:tr>")
        cells.forEach { text ->
            sb.append("""<w:tc><w:tcPr><w:tcW w:w="0" w:type="auto"/></w:tcPr>
<w:p><w:pPr><w:jc w:val="${if (header) "center" else "left"}"/></w:pPr>
<w:r><w:rPr>${if (header) "<w:b/>" else ""}<w:sz w:val="18"/></w:rPr>
<w:t xml:space="preserve">${text.xe()}</w:t></w:r></w:p></w:tc>""")
        }
        sb.append("</w:tr>"); return sb.toString()
    }

    // ── Para helpers ──────────────────────────────────────────────────────────

    private fun StringBuilder.para(text: String, bold: Boolean = false) = appendLine(
        """<w:p><w:r><w:rPr>${if (bold) "<w:b/>" else ""}<w:sz w:val="20"/></w:rPr>
<w:t xml:space="preserve">${text.xe()}</w:t></w:r></w:p>""")

    private fun StringBuilder.centerPara(text: String, bold: Boolean, sz: Int) = appendLine(
        """<w:p><w:pPr><w:jc w:val="center"/></w:pPr>
<w:r><w:rPr>${if (bold) "<w:b/>" else ""}<w:sz w:val="$sz"/><w:szCs w:val="$sz"/></w:rPr>
<w:t>${text.xe()}</w:t></w:r></w:p>""")

    private fun StringBuilder.heading(text: String) = appendLine(
        """<w:p><w:r><w:rPr><w:b/><w:sz w:val="24"/><w:color w:val="1565C0"/></w:rPr>
<w:t>${text.xe()}</w:t></w:r></w:p>""")

    private fun StringBuilder.labelVal(label: String, value: String) = appendLine(
        """<w:p><w:r><w:rPr><w:b/><w:sz w:val="20"/></w:rPr>
<w:t xml:space="preserve">${"%-20s".format("$label:").xe()}</w:t></w:r>
<w:r><w:rPr><w:sz w:val="20"/></w:rPr><w:t>${value.xe()}</w:t></w:r></w:p>""")

    private fun String.xe() = replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
}
