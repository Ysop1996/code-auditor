package de.lifeos.core.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.legal.LegalExecutionResult
import java.io.File
import java.io.FileOutputStream

class DeterministicPdfGenerator(private val context: Context) {

    fun generateLegalDocumentPdf(
        fileName: String,
        legalResult: LegalExecutionResult,
        senderInfo: String = "Life-OS Autonomer Vault"
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paintText = Paint().apply {
            color = Color.BLACK
            textSize = 10.5f
            isAntiAlias = true
        }

        val paintHeader = Paint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            isAntiAlias = true
        }

        canvas.drawText(senderInfo, 50f, 60f, paintHeader)
        canvas.drawLine(50f, 68f, 545f, 68f, paintHeader)

        var yPosition = 120f
        val lineHeight = 16f

        legalResult.generatedLetter.lines().forEach { rawLine ->
            val wrappedLines = wrapText(rawLine, paintText, 495f)
            for (line in wrappedLines) {
                if (yPosition > 780f) break
                canvas.drawText(line, 50f, yPosition, paintText)
                yPosition += lineHeight
            }
        }

        pdfDocument.finishPage(page)
        val outputFile = File(context.noBackupFilesDir, "$fileName.pdf")
        FileOutputStream(outputFile).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return outputFile
    }

    fun generateProjectSummaryPdf(
        projectId: String,
        associatedNodes: List<AttractorNode>,
        healthStatus: String,
        frictionW: Double
    ): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }

        canvas.drawText("LIFE-OS PROJEKT-KONSOLIDIERUNG // $projectId", 50f, 50f, titlePaint)
        canvas.drawText("Status: $healthStatus | Reibungswert W: ${"%.3f".format(frictionW)}", 50f, 75f, textPaint)
        canvas.drawLine(50f, 85f, 545f, 85f, textPaint)

        var y = 110f
        associatedNodes.forEachIndexed { index, node ->
            canvas.drawText("${index + 1}. [Masse: ${"%.2f".format(node.mass)}] ${node.id}", 50f, y, textPaint)
            y += 14f
            canvas.drawText("   ${node.payload.take(80)}...", 50f, y, textPaint)
            y += 20f
        }

        pdfDocument.finishPage(page)
        val outputFile = File(context.noBackupFilesDir, "Report_$projectId.pdf")
        FileOutputStream(outputFile).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()
        return outputFile
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (paint.measureText(currentLine.toString() + " " + word) <= maxWidth) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        return lines
    }
}
