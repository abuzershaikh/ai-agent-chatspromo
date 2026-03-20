package com.message.bulksend.tools.invoicemaker

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates Invoice as PDF or PNG
 */
class InvoiceGeneratorTool(private val context: Context) {
    
    private val currencyFormat = DecimalFormat("#,##0.00")
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    
    fun generatePng(invoice: InvoiceDataTool): File? {
        return try {
            val width = 1080
            val height = calculateHeight(invoice)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            drawInvoice(canvas, invoice, width, height)
            
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "Invoice_${invoice.invoiceNumber}.png"
            )
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun generatePdf(invoice: InvoiceDataTool): File? {
        return try {
            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = document.startPage(pageInfo)
            
            drawInvoicePdf(page.canvas, invoice, pageWidth, pageHeight)
            
            document.finishPage(page)
            
            val file = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "Invoice_${invoice.invoiceNumber}.pdf"
            )
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun calculateHeight(invoice: InvoiceDataTool): Int {
        val baseHeight = 800
        val itemsHeight = invoice.items.size * 60
        return baseHeight + itemsHeight + 400
    }
    
    private fun drawInvoice(canvas: Canvas, invoice: InvoiceDataTool, width: Int, height: Int) {
        canvas.drawColor(Color.WHITE)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var yPos = 40f
        val margin = 40f
        
        // Header Background
        paint.color = Color.parseColor("#10B981")
        canvas.drawRect(0f, 0f, width.toFloat(), 180f, paint)
        
        // Logo or Business Name
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        
        if (invoice.businessInfo.logoUri != null) {
            try {
                val logoUri = Uri.parse(invoice.businessInfo.logoUri)
                val inputStream = context.contentResolver.openInputStream(logoUri)
                val logoBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (logoBitmap != null) {
                    val logoSize = 100
                    val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoSize, logoSize, true)
                    canvas.drawBitmap(scaledLogo, margin, 40f, paint)
                    logoBitmap.recycle()
                    scaledLogo.recycle()
                }
            } catch (e: Exception) {
                canvas.drawText(invoice.businessInfo.businessName, margin, 100f, paint)
            }
        } else {
            canvas.drawText(invoice.businessInfo.businessName, margin, 100f, paint)
        }
        
        // Invoice Title
        paint.textSize = 28f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("INVOICE", width - margin, 70f, paint)
        
        paint.textSize = 18f
        canvas.drawText("#${invoice.invoiceNumber}", width - margin, 100f, paint)
        canvas.drawText("Date: ${dateFormat.format(Date(invoice.invoiceDate))}", width - margin, 130f, paint)
        
        yPos = 220f
        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.parseColor("#1F2937")
        
        // Business Info
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FROM:", margin, yPos, paint)
        yPos += 25f
        
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 13f
        paint.color = Color.parseColor("#4B5563")
        if (invoice.businessInfo.address.isNotEmpty()) {
            canvas.drawText(invoice.businessInfo.address, margin, yPos, paint)
            yPos += 20f
        }
        if (invoice.businessInfo.phone.isNotEmpty()) {
            canvas.drawText("Phone: ${invoice.businessInfo.phone}", margin, yPos, paint)
            yPos += 20f
        }
        if (invoice.businessInfo.email.isNotEmpty()) {
            canvas.drawText("Email: ${invoice.businessInfo.email}", margin, yPos, paint)
            yPos += 20f
        }
        if (invoice.businessInfo.taxNumber.isNotEmpty()) {
            canvas.drawText("Tax ID: ${invoice.businessInfo.taxNumber}", margin, yPos, paint)
        }
        
        // Bill To
        yPos = 220f
        val billToX = width / 2f + 40f
        paint.color = Color.parseColor("#1F2937")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 14f
        canvas.drawText("BILL TO:", billToX, yPos, paint)
        yPos += 25f
        
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 13f
        paint.color = Color.parseColor("#4B5563")
        canvas.drawText(invoice.clientInfo.name, billToX, yPos, paint)
        yPos += 20f
        if (invoice.clientInfo.address.isNotEmpty()) {
            canvas.drawText(invoice.clientInfo.address, billToX, yPos, paint)
            yPos += 20f
        }
        if (invoice.clientInfo.phone.isNotEmpty()) {
            canvas.drawText("Phone: ${invoice.clientInfo.phone}", billToX, yPos, paint)
        }
        
        yPos = 380f
        
        // Items Table Header
        paint.color = Color.parseColor("#F3F4F6")
        canvas.drawRect(margin, yPos, width - margin, yPos + 40f, paint)
        
        paint.color = Color.parseColor("#1F2937")
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 13f
        yPos += 28f
        canvas.drawText("DESCRIPTION", margin + 10f, yPos, paint)
        canvas.drawText("QTY", width - 300f, yPos, paint)
        canvas.drawText("RATE", width - 220f, yPos, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", width - margin - 10f, yPos, paint)
        paint.textAlign = Paint.Align.LEFT
        
        yPos += 20f
        
        // Items
        val cs = invoice.currencySymbol
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.parseColor("#4B5563")
        for (item in invoice.items) {
            yPos += 35f
            canvas.drawText(item.description, margin + 10f, yPos, paint)
            canvas.drawText(item.quantity.toString(), width - 300f, yPos, paint)
            canvas.drawText("$cs${currencyFormat.format(item.rate)}", width - 220f, yPos, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$cs${currencyFormat.format(item.quantity * item.rate)}", width - margin - 10f, yPos, paint)
            paint.textAlign = Paint.Align.LEFT
            
            paint.color = Color.parseColor("#E5E7EB")
            canvas.drawLine(margin, yPos + 15f, width - margin, yPos + 15f, paint)
            paint.color = Color.parseColor("#4B5563")
        }
        
        yPos += 50f
        
        // Totals
        val totalsX = width - 250f
        paint.textSize = 14f
        
        canvas.drawText("Subtotal:", totalsX, yPos, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$cs${currencyFormat.format(invoice.subtotal)}", width - margin - 10f, yPos, paint)
        paint.textAlign = Paint.Align.LEFT
        yPos += 30f
        
        if (invoice.taxRate > 0) {
            canvas.drawText("Tax (${invoice.taxRate.toInt()}%):", totalsX, yPos, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("$cs${currencyFormat.format(invoice.taxAmount)}", width - margin - 10f, yPos, paint)
            paint.textAlign = Paint.Align.LEFT
            yPos += 30f
        }
        
        if (invoice.discount > 0) {
            canvas.drawText("Discount:", totalsX, yPos, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("-$cs${currencyFormat.format(invoice.discount)}", width - margin - 10f, yPos, paint)
            paint.textAlign = Paint.Align.LEFT
            yPos += 30f
        }
        
        // Total
        paint.color = Color.parseColor("#10B981")
        canvas.drawRect(totalsX - 10f, yPos - 5f, width - margin, yPos + 35f, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 18f
        canvas.drawText("TOTAL:", totalsX, yPos + 22f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("$cs${currencyFormat.format(invoice.totalAmount)}", width - margin - 10f, yPos + 22f, paint)
        paint.textAlign = Paint.Align.LEFT
        
        yPos += 70f
        
        // Notes
        if (invoice.notes.isNotEmpty()) {
            paint.color = Color.parseColor("#1F2937")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 13f
            canvas.drawText("Notes:", margin, yPos, paint)
            yPos += 20f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#6B7280")
            paint.textSize = 12f
            canvas.drawText(invoice.notes, margin, yPos, paint)
            yPos += 30f
        }
        
        // Bank Details
        if (invoice.bankDetails.isNotEmpty()) {
            paint.color = Color.parseColor("#1F2937")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textSize = 13f
            canvas.drawText("Bank Details:", margin, yPos, paint)
            yPos += 20f
            paint.typeface = Typeface.DEFAULT
            paint.color = Color.parseColor("#6B7280")
            paint.textSize = 12f
            for (line in invoice.bankDetails.split("\n")) {
                canvas.drawText(line, margin, yPos, paint)
                yPos += 18f
            }
        }
        
        // Footer
        paint.color = Color.parseColor("#9CA3AF")
        paint.textSize = 11f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Thank you for your business!", width / 2f, height - 30f, paint)
    }
    
    private fun drawInvoicePdf(canvas: Canvas, invoice: InvoiceDataTool, width: Int, height: Int) {
        val scale = 0.55f
        canvas.scale(scale, scale)
        drawInvoice(canvas, invoice, (width / scale).toInt(), (height / scale).toInt())
    }
    
    fun shareInvoice(file: File, mimeType: String = "application/pdf") {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Invoice"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
 