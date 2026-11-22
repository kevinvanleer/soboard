package kvl.android.kvl.soboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Item(val id: Long, val file: File, val name: String?)

fun decode(file: File, maxSide: Int): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val largest = maxOf(info.size.width, info.size.height)
        decoder.setTargetSampleSize((largest + maxSide - 1) / maxSide)
    }
}.getOrNull()

class Store private constructor(private val context: Context) :
    SQLiteOpenHelper(context, "kvl.android.kvl.soboard.db", null, 1) {

    val items = mutableStateListOf<Item>()
    var loaded by mutableStateOf(false)
        private set

    private val imageDir = File(context.filesDir, "images").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var created = false

    init {
        scope.launch {
            items.addAll(withContext(Dispatchers.IO) { load() })
            loaded = true
            if (created) {
                val sample = Uri.parse("android.resource://${context.packageName}/${R.drawable.sample_boarding_pass}")
                add(sample, context.getString(R.string.sample_name))
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        created = true
        db.execSQL(
            "CREATE TABLE $TABLE (_id INTEGER PRIMARY KEY, image_uri TEXT, airline TEXT, " +
                "flight_number TEXT, departure_time TEXT, user_defined_name TEXT, raw_data TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun fileFor(id: Long) = File(imageDir, id.toString())

    private fun copyImage(source: Uri, target: File): Boolean {
        val copied = runCatching {
            context.contentResolver.openInputStream(source)!!.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            bounds.outWidth > 0
        }.getOrDefault(false)
        if (!copied) target.delete()
        return copied
    }

    private fun load(): List<Item> {
        val db = writableDatabase
        val columns = arrayOf("_id", "image_uri", "airline", "flight_number", "user_defined_name")
        val rows = db.query(TABLE, columns, null, null, null, null, "_id DESC").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val flight = cursor.getString(3)
                    val legacyName = flight?.let { listOfNotNull(cursor.getString(2), "Flight $it").joinToString(" ") }
                    add(Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(4) ?: legacyName))
                }
            }
        }
        return rows.mapNotNull { (id, uri, name) ->
            val file = fileFor(id)
            if (file.exists() || (uri != null && copyImage(Uri.parse(uri), file))) {
                Item(id, file, name)
            } else {
                db.delete(TABLE, "_id = ?", arrayOf(id.toString()))
                null
            }
        }
    }

    suspend fun add(source: Uri, name: String? = null): Item? {
        val item = withContext(Dispatchers.IO) {
            val db = writableDatabase
            val id = db.insert(TABLE, null, ContentValues().apply { put("user_defined_name", name) })
            val file = fileFor(id)
            if (copyImage(source, file)) {
                db.update(TABLE, ContentValues().apply { put("image_uri", Uri.fromFile(file).toString()) }, "_id = ?", arrayOf(id.toString()))
                Item(id, file, name)
            } else {
                db.delete(TABLE, "_id = ?", arrayOf(id.toString()))
                null
            }
        } ?: return null
        items.add(0, item)
        if (name == null) {
            scope.launch {
                val suggestion = withContext(Dispatchers.IO) { suggestName(context, item.file) }
                if (suggestion != null && items.find { it.id == item.id }?.name == null) rename(item.id, suggestion)
            }
        }
        return item
    }

    fun rename(id: Long, name: String?) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        val clean = name?.trim()?.ifEmpty { null }
        items[index] = items[index].copy(name = clean)
        scope.launch(Dispatchers.IO) {
            writableDatabase.update(TABLE, ContentValues().apply { put("user_defined_name", clean) }, "_id = ?", arrayOf(id.toString()))
        }
    }

    fun purge(item: Item) {
        scope.launch(Dispatchers.IO) {
            writableDatabase.delete(TABLE, "_id = ?", arrayOf(item.id.toString()))
            item.file.delete()
        }
    }

    companion object {
        private const val TABLE = "ticket_info"
        private var instance: Store? = null

        fun get(context: Context) = instance ?: Store(context.applicationContext).also { instance = it }
    }
}
