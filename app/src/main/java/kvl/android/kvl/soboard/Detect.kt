package kvl.android.kvl.soboard

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

fun suggestName(context: Context, file: File): String? {
    val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }.getOrNull() ?: return null

    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX)
        .build()
    val pass = BarcodeScanning.getClient(options).use { scanner ->
        runCatching { Tasks.await(scanner.process(image)) }.getOrNull()
            ?.firstNotNullOfOrNull { it.rawValue?.let(::parseBcbp) }
    }
    if (pass != null) return pass

    return TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).use { recognizer ->
        runCatching { Tasks.await(recognizer.process(image)) }.getOrNull()
            ?.textBlocks?.flatMap { it.lines }?.map { it.text.trim() }
            ?.firstOrNull { line -> line.count(Char::isLetter) >= 3 }
            ?.take(40)
    }
}
