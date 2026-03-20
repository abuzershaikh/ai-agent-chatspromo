package com.message.bulksend.pdfviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object PdfUtils {
    
    /**
     * Open a PDF file in PdfViewerActivity
     * @param context Context
     * @param pdfUri URI of the PDF file
     */
    fun openPdf(context: Context, pdfUri: Uri) {
        try {
            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                data = pdfUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Open a PDF file from file path
     * @param context Context
     * @param filePath Path to the PDF file
     */
    fun openPdfFromPath(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            openPdf(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Share a PDF file
     * @param context Context
     * @param pdfUri URI of the PDF file
     */
    fun sharePdf(context: Context, pdfUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Check if a file is a PDF
     * @param fileName Name of the file
     * @return true if file is PDF, false otherwise
     */
    fun isPdfFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".pdf")
    }
    
    /**
     * Check if a URI points to a PDF file
     * @param context Context
     * @param uri URI to check
     * @return true if URI is PDF, false otherwise
     */
    fun isPdfUri(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == "application/pdf"
    }
}
