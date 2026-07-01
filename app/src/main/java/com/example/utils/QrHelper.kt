package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.print.PrintAttributes
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.Attendee
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.ByteArrayOutputStream
import java.io.File

object QrHelper {
    /**
     * Generates a square QR Code Bitmap from the given text.
     */
    fun generateQrCode(content: String, size: Int = 512): Bitmap {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    // We can use custom colors or standard Black & White.
                    // Let's use clean black for QR dots and transparent/white for the background.
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            // Return an empty 1x1 bitmap as fallback
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    /**
     * Generates a printable HTML grid containing unique QR codes and student cards,
     * and triggers the Android system print dialog.
     */
    fun printStudentQrCards(
        context: Context,
        attendees: List<Attendee>,
        schoolName: String,
        schoolAddress: String,
        schoolLogoPath: String
    ) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                // Get Base64 for School Logo
                val schoolLogoBase64 = getFileBase64(schoolLogoPath)
                val schoolLogoHtml = if (schoolLogoBase64 != null) {
                    "<img class=\"school-logo\" src=\"data:image/jpeg;base64,$schoolLogoBase64\" />"
                } else {
                    "<div class=\"school-logo-placeholder\">🏫</div>"
                }

                val cardsHtml = StringBuilder()

                // Generate HTML card for each student
                attendees.forEach { student ->
                    val qrBase64 = getQrBase64(student.uid)
                    val photoBase64 = getFileBase64(student.photoPath)
                    val photoHtml = if (photoBase64 != null) {
                        "<img class=\"student-photo\" src=\"data:image/jpeg;base64,$photoBase64\" />"
                    } else {
                        "<div class=\"student-photo-placeholder\">👤</div>"
                    }

                    cardsHtml.append("""
                        <div class="card">
                            <div class="card-header">
                                $schoolLogoHtml
                                <div class="school-details">
                                    <div class="school-name">$schoolName</div>
                                    <div class="school-address">$schoolAddress</div>
                                </div>
                            </div>
                            <div class="card-body">
                                $photoHtml
                                <div class="student-info">
                                    <div class="student-name">${student.name}</div>
                                    <div class="student-nisn">ID: ${student.uid}</div>
                                    <div class="student-role">${student.role.uppercase()}</div>
                                </div>
                                <img class="qr-code" src="data:image/png;base64,$qrBase64" />
                            </div>
                            <div class="card-footer">
                                KARTU ABSENSI RESMI - ABSEN X-DEGAN
                            </div>
                        </div>
                    """.trimIndent())
                }

                val fullHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <meta charset="utf-8">
                    <style>
                        @media print {
                            body { margin: 0; padding: 0; background-color: #fff; }
                            .no-print { display: none; }
                            .page-break { page-break-after: always; }
                        }
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            color: #333;
                            background-color: #f3f4f6;
                            margin: 16px;
                            padding: 0;
                        }
                        .grid {
                            display: flex;
                            flex-wrap: wrap;
                            gap: 16px;
                            justify-content: center;
                        }
                        .card {
                            width: 320px;
                            height: 200px;
                            border: 2px dashed #9ca3af;
                            border-radius: 12px;
                            padding: 10px;
                            box-sizing: border-box;
                            display: flex;
                            flex-direction: column;
                            justify-content: space-between;
                            background-color: #fff;
                            page-break-inside: avoid;
                        }
                        .card-header {
                            display: flex;
                            align-items: center;
                            border-bottom: 2px solid #e5e7eb;
                            padding-bottom: 6px;
                            margin-bottom: 4px;
                        }
                        .school-logo {
                            width: 28px;
                            height: 28px;
                            object-fit: cover;
                            border-radius: 4px;
                            margin-right: 8px;
                        }
                        .school-logo-placeholder {
                            font-size: 20px;
                            margin-right: 8px;
                            display: flex;
                            align-items: center;
                        }
                        .school-details {
                            flex: 1;
                            display: flex;
                            flex-direction: column;
                        }
                        .school-name {
                            font-size: 10px;
                            font-weight: 800;
                            color: #1e3a8a;
                            line-height: 1.2;
                            text-transform: uppercase;
                        }
                        .school-address {
                            font-size: 7px;
                            color: #4b5563;
                            line-height: 1.2;
                        }
                        .card-body {
                            display: flex;
                            align-items: center;
                            flex: 1;
                            gap: 8px;
                            margin-bottom: 4px;
                        }
                        .student-photo {
                            width: 52px;
                            height: 64px;
                            object-fit: cover;
                            border-radius: 4px;
                            border: 1px solid #d1d5db;
                        }
                        .student-photo-placeholder {
                            width: 52px;
                            height: 64px;
                            background-color: #f3f4f6;
                            border-radius: 4px;
                            border: 1px solid #d1d5db;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            font-size: 24px;
                        }
                        .student-info {
                            flex: 1;
                            display: flex;
                            flex-direction: column;
                            justify-content: center;
                        }
                        .student-name {
                            font-size: 11px;
                            font-weight: 700;
                            color: #111827;
                            margin-bottom: 2px;
                            line-height: 1.2;
                        }
                        .student-nisn {
                            font-size: 9px;
                            color: #4b5563;
                            font-family: monospace;
                            margin-bottom: 2px;
                        }
                        .student-role {
                            font-size: 7px;
                            font-weight: 800;
                            background-color: #dbeafe;
                            color: #1e40af;
                            padding: 1px 4px;
                            border-radius: 20px;
                            align-self: flex-start;
                        }
                        .qr-code {
                            width: 64px;
                            height: 64px;
                            object-fit: contain;
                        }
                        .card-footer {
                            font-size: 7px;
                            font-weight: bold;
                            text-align: center;
                            color: #9ca3af;
                            border-top: 1px dashed #e5e7eb;
                            padding-top: 4px;
                        }
                    </style>
                    </head>
                    <body>
                        <h3 style="text-align:center; color:#1e3a8a; font-family:sans-serif; margin-bottom: 4px;">CETAK KARTU ABSENSI QR SISWA</h3>
                        <p style="text-align:center; font-size:10px; color:#4b5563; margin-top: 0; margin-bottom: 20px;">Silakan gunting sesuai garis putus-putus untuk dibagikan kepada siswa.</p>
                        <div class="grid">
                            $cardsHtml
                        </div>
                    </body>
                    </html>
                """.trimIndent()

                // WebView to render content and start system print dialog
                val webView = WebView(context)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val printAdapter = webView.createPrintDocumentAdapter("Kartu Absensi QR Siswa")
                        val jobName = "Cetak_Kartu_Absensi_QR"
                        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                    }
                }
                webView.loadDataWithBaseURL(null, fullHtml, "text/html", "utf-8", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getQrBase64(content: String): String {
        val bitmap = generateQrCode(content, 250)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun getFileBase64(path: String?): String? {
        if (path.isNullOrEmpty()) return null
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
