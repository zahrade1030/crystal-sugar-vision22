package com.example.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.cv.ClassificationResult
import com.example.data.security.SecurityManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val securityManager: SecurityManager = SecurityManager()
) {

    private fun getPublicDownloadsDir(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloads.exists()) downloads.mkdirs()
        return downloads
    }

    /**
     * Exports classification result to PDF document
     */
    fun exportToPdf(result: ClassificationResult, operatorName: String = "Lab Specialist"): File {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            isFakeBoldText = true
        }

        val paintSubtitle = Paint().apply {
            color = Color.parseColor("#0284C7")
            textSize = 12f
            isFakeBoldText = true
        }

        val paintText = Paint().apply {
            color = Color.parseColor("#334155")
            textSize = 11f
        }

        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        canvas.drawText("CRYSTAL VISION - LABORATORY ANALYSIS REPORT", 40f, 50f, paintTitle)
        canvas.drawText("Islamic Azad University, Hamedan Branch - Sugar ICUMSA Classifier", 40f, 70f, paintSubtitle)
        canvas.drawLine(40f, 85f, 555f, 85f, paintText)

        var y = 120f
        canvas.drawText("Date & Time: $dateStr", 40f, y, paintText); y += 18f
        canvas.drawText("Operator Name: $operatorName", 40f, y, paintText); y += 18f
        canvas.drawText("Processing Time: ${result.processingTimeMs} ms", 40f, y, paintText); y += 24f

        canvas.drawText("ICUMSA ESTIMATION (LAB-BASED INTERPOLATION):", 40f, y, paintSubtitle); y += 18f
        val est = result.icumsaEstimation
        if (est != null) {
            val icumsaText = if (est.isOutOfRange) {
                "ICUMSA: OUT OF REFERENCE RANGE (${est.outOfRangeReason ?: "Out of bounds"})"
            } else if (est.isUnknown) {
                "ICUMSA: UNKNOWN (Path Distance: ${String.format("%.2f", est.distanceToPath)} > Threshold)"
            } else {
                "Estimated ICUMSA: ${String.format("%.1f", est.estimatedIcumsa)} IU"
            }
            canvas.drawText(icumsaText, 60f, y, paintText); y += 16f
            canvas.drawText(
                "Reference Interval: [Ref ${est.selectedRef1.sampleId}: ${est.selectedRef1.icumsaValue} IU] -> [Ref ${est.selectedRef2.sampleId}: ${est.selectedRef2.icumsaValue} IU] (t = ${String.format("%.3f", est.relativePositionT)})",
                60f, y, paintText
            ); y += 16f
            canvas.drawText(
                "Sample Median LAB: L*=${String.format("%.2f", est.sampleLabMedian.medianL)}, a*=${String.format("%.2f", est.sampleLabMedian.medianA)}, b*=${String.format("%.2f", est.sampleLabMedian.medianB)}",
                60f, y, paintText
            ); y += 16f
            canvas.drawText("Distance to Reference Path: ${String.format("%.2f", est.distanceToPath)}", 60f, y, paintText); y += 24f
        } else {
            canvas.drawText("Estimated ICUMSA: Not available", 60f, y, paintText); y += 24f
        }

        canvas.drawText("PRIMARY 191-DIM CLASSIFICATION RESULT:", 40f, y, paintSubtitle); y += 18f
        canvas.drawText("Nearest Reference Sample: ${result.nearestSampleName}", 60f, y, paintText); y += 16f
        canvas.drawText("Confidence Score: ${String.format("%.2f", result.confidenceScorePct)}% (${result.confidenceLevel})", 60f, y, paintText); y += 16f
        canvas.drawText("Is Unknown Sample: ${if (result.isUnknown) "YES" else "NO"}", 60f, y, paintText); y += 24f

        canvas.drawText("SIMILARITY METRICS BREAKDOWN (REFERENCE SAMPLES):", 40f, y, paintSubtitle); y += 18f
        for (m in result.allMatches.take(6)) {
            canvas.drawText(
                "• ${m.sampleName}: Cosine=${String.format("%.1f", m.cosineSimilarityPct)}%, EucDist=${String.format("%.2f", m.euclideanDistance)}, Match=${String.format("%.1f", m.finalSimilarityPct)}%",
                60f, y, paintText
            )
            y += 16f
        }

        y += 30f
        canvas.drawText("Signature & Lab Seal: _________________________", 40f, y, paintText)

        pdfDoc.finishPage(page)

        val fileName = "Analysis_Report_${System.currentTimeMillis()}.pdf"
        val file = try {
            File(getPublicDownloadsDir(), fileName)
        } catch (e: Exception) {
            File(context.cacheDir, fileName)
        }

        FileOutputStream(file).use { pdfDoc.writeTo(it) }
        pdfDoc.close()
        return file
    }

    /**
     * Exports result to CSV format
     */
    fun exportToCsv(results: List<ClassificationResult>): File {
        val fileName = "Sugar_Analysis_Export_${System.currentTimeMillis()}.csv"
        val file = try {
            File(getPublicDownloadsDir(), fileName)
        } catch (e: Exception) {
            File(context.cacheDir, fileName)
        }

        val sb = StringBuilder()
        // UTF-8 BOM for Persian and Excel compatibility
        sb.append("\uFEFF")
        sb.append("Timestamp,Matched Sample,Estimated ICUMSA,Ref1 (ID/ICUMSA),Ref2 (ID/ICUMSA),Interpolation t,Median L*,Median a*,Median b*,Path Distance,Confidence Score (%),Confidence Level,Unknown,Out Of Range,Processing Time (ms)\n")

        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (r in results) {
            val tsStr = dateFmt.format(Date())
            val est = r.icumsaEstimation
            val estVal = if (est != null) String.format(Locale.US, "%.1f", est.estimatedIcumsa) else "N/A"
            val ref1Str = if (est != null) "Ref ${est.selectedRef1.sampleId} (${est.selectedRef1.icumsaValue})" else "N/A"
            val ref2Str = if (est != null) "Ref ${est.selectedRef2.sampleId} (${est.selectedRef2.icumsaValue})" else "N/A"
            val tStr = if (est != null) String.format(Locale.US, "%.3f", est.relativePositionT) else "N/A"
            val medL = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianL) else "0.0"
            val medA = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianA) else "0.0"
            val medB = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianB) else "0.0"
            val pathDist = if (est != null) String.format(Locale.US, "%.2f", est.distanceToPath) else "0.0"
            val outOfRange = est?.isOutOfRange ?: false

            sb.append("$tsStr,\"${r.nearestSampleName}\",$estVal,\"$ref1Str\",\"$ref2Str\",$tStr,$medL,$medA,$medB,$pathDist,${String.format(Locale.US, "%.2f", r.confidenceScorePct)},${r.confidenceLevel},${r.isUnknown},$outOfRange,${r.processingTimeMs}\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Exports result to Excel format (.csv table compatible with MS Excel)
     */
    fun exportToExcel(results: List<ClassificationResult>): File {
        val fileName = "Sugar_Analysis_Excel_${System.currentTimeMillis()}.csv"
        val file = try {
            File(getPublicDownloadsDir(), fileName)
        } catch (e: Exception) {
            File(context.cacheDir, fileName)
        }

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("تاریخ و زمان,نمونه منطبق شده,مقدار تخمینی ICUMSA,مرجع ۱,مرجع ۲,موقعیت نسبی t,میانه L*,میانه a*,میانه b*,فاصله تا مسیر مرجع,درصد اطمینان,سطح اطمینان,وضعیت ناشناخته,خارج از محدوده,زمان پردازش (میلی ثانیه)\n")

        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (r in results) {
            val tsStr = dateFmt.format(Date())
            val est = r.icumsaEstimation
            val estVal = if (est != null) String.format(Locale.US, "%.1f", est.estimatedIcumsa) else "نامشخص"
            val ref1Str = if (est != null) "مرجع ${est.selectedRef1.sampleId} (${est.selectedRef1.icumsaValue})" else "-"
            val ref2Str = if (est != null) "مرجع ${est.selectedRef2.sampleId} (${est.selectedRef2.icumsaValue})" else "-"
            val tStr = if (est != null) String.format(Locale.US, "%.3f", est.relativePositionT) else "-"
            val medL = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianL) else "-"
            val medA = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianA) else "-"
            val medB = if (est != null) String.format(Locale.US, "%.2f", est.sampleLabMedian.medianB) else "-"
            val pathDist = if (est != null) String.format(Locale.US, "%.2f", est.distanceToPath) else "-"
            val outOfRange = if (est?.isOutOfRange == true) "بله" else "خیر"
            val unknownStr = if (r.isUnknown) "بله" else "خیر"

            sb.append("$tsStr,\"${r.nearestSampleName}\",$estVal,\"$ref1Str\",\"$ref2Str\",$tStr,$medL,$medA,$medB,$pathDist,${String.format(Locale.US, "%.2f", r.confidenceScorePct)}%,${r.confidenceLevel},$unknownStr,$outOfRange,${r.processingTimeMs}\n")
        }

        file.writeText(sb.toString())
        return file
    }

    /**
     * Exports result to encrypted JSON payload
     */
    fun exportToEncryptedJson(results: List<ClassificationResult>): File {
        val file = File(context.cacheDir, "Encrypted_Analysis_${System.currentTimeMillis()}.json.enc")
        val sb = StringBuilder()
        sb.append("[\n")
        for ((idx, r) in results.withIndex()) {
            val est = r.icumsaEstimation
            sb.append("  {\n")
            sb.append("    \"matchedSample\": \"${r.nearestSampleName}\",\n")
            sb.append("    \"confidence\": ${r.confidenceScorePct},\n")
            sb.append("    \"processingMs\": ${r.processingTimeMs},\n")
            if (est != null) {
                sb.append("    \"estimatedIcumsa\": ${est.estimatedIcumsa},\n")
                sb.append("    \"ref1Id\": ${est.selectedRef1.sampleId},\n")
                sb.append("    \"ref1Icumsa\": ${est.selectedRef1.icumsaValue},\n")
                sb.append("    \"ref2Id\": ${est.selectedRef2.sampleId},\n")
                sb.append("    \"ref2Icumsa\": ${est.selectedRef2.icumsaValue},\n")
                sb.append("    \"sampleMedianL\": ${est.sampleLabMedian.medianL},\n")
                sb.append("    \"sampleMedianA\": ${est.sampleLabMedian.medianA},\n")
                sb.append("    \"sampleMedianB\": ${est.sampleLabMedian.medianB},\n")
                sb.append("    \"distanceToPath\": ${est.distanceToPath},\n")
                sb.append("    \"isOutOfRange\": ${est.isOutOfRange},\n")
            }
            sb.append("    \"isUnknown\": ${r.isUnknown}\n")
            sb.append("  }${if (idx < results.size - 1) "," else ""}\n")
        }
        sb.append("]")

        val encrypted = securityManager.encryptString(sb.toString())
        file.writeText(encrypted)
        return file
    }

    /**
     * Opens an exported file in an external app
     */
    fun openFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shares an exported file via system chooser
     */
    fun shareFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "اشتراک‌گذاری گزارش").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
