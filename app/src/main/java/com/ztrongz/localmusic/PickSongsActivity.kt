package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Màn hình "Thêm bài hát vào danh sách phát":
 * hiện thư viện nhạc (giống màn hình chính) ở chế độ chọn nhiều.
 * Bấm vào bài để tick, bấm nút tím ở dưới để thêm các bài đã tick vào CUỐI danh sách phát.
 * Bài đã có sẵn trong danh sách phát được làm mờ và ghi "Đã có".
 */
class PickSongsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUEUE_URIS = "queue_uris"       // danh sách uri đang có trong hàng đợi (do bảng Danh sách phát gửi sang)
        const val RESULT_ADDED_COUNT = "added_count"    // số bài đã thêm (trả về khi đóng)
        const val EXTRA_FOLDER_MODE = "folder_mode"     // true = chọn bài cho 1 thư mục: chỉ trả về danh sách uri, không đụng tới trình phát
        const val EXTRA_FOLDER_NAME = "folder_name"
        const val RESULT_URIS = "result_uris"           // (chế độ thư mục) các uri đã chọn
        private const val THUMB_TARGET_PX = 128         // cạnh ngắn ảnh bìa thu nhỏ (px). Tăng = nét hơn nhưng tốn RAM hơn
        private const val BATCH_SIZE = 8                // đọc xong bao nhiêu bài thì cập nhật danh sách một lần
    }

    // Một dòng trong danh sách chọn
    class PickItem(
        val uri: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val thumb: Bitmap?
    )

    private val allItems = mutableListOf<PickItem>()      // toàn bộ thư viện
    private var shownItems: List<PickItem> = emptyList()  // sau khi lọc theo ô tìm kiếm
    private val selected = linkedSetOf<String>()          // các bài đã tick (theo uri)
    private val inQueue = hashSetOf<String>()             // các bài đã có trong hàng đợi
    private var query = ""
    private var folderMode = false

    private lateinit var adapter: PickAdapter
    private lateinit var rvPick: RecyclerView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvEmpty: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnConfirm: TextView
    private lateinit var ivSelectAll: ImageView

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_songs)

        intent.getStringArrayListExtra(EXTRA_QUEUE_URIS)?.let { inQueue.addAll(it) }
        folderMode = intent.getBooleanExtra(EXTRA_FOLDER_MODE, false)
        if (folderMode) {
            val name = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: ""
            findViewById<TextView>(R.id.tvPickTitle).text = "Thêm vào \"$name\""
        }

        rvPick = findViewById(R.id.rvPick)
        tvSubtitle = findViewById(R.id.tvPickSubtitle)
        tvEmpty = findViewById(R.id.tvPickEmpty)
        progress = findViewById(R.id.pbPick)
        btnConfirm = findViewById(R.id.btnPickConfirm)
        ivSelectAll = findViewById(R.id.ivPickSelectAll)

        adapter = PickAdapter { item -> toggle(item.uri) }
        rvPick.layoutManager = LinearLayoutManager(this)
        rvPick.adapter = adapter
        rvPick.setHasFixedSize(true)

        findViewById<EditText>(R.id.etPickSearch).doAfterTextChanged { text ->
            query = text.toString().trim()
            applyFilter()
        }
        findViewById<View>(R.id.btnPickClose).setOnClickListener { finishWithAnim() }
        findViewById<View>(R.id.btnPickSelectAll).setOnClickListener { toggleSelectAll() }
        btnConfirm.setOnClickListener { confirmAdd() }

        connectController()
        loadSongs()
        updateBottom()
    }

    // ---------------------------------------------------------------- Chọn / bỏ chọn
    private fun toggle(uri: String) {
        if (inQueue.contains(uri)) return
        if (!selected.add(uri)) selected.remove(uri)
        adapter.notifyUriChanged(uri)
        updateBottom()
    }

    private fun toggleSelectAll() {
        val selectable = shownItems.filter { !inQueue.contains(it.uri) }
        if (selectable.isEmpty()) return
        val allSelected = selectable.all { selected.contains(it.uri) }
        if (allSelected) selected.removeAll(selectable.map { it.uri }.toSet())
        else selectable.forEach { selected.add(it.uri) }
        adapter.refreshSelection()
        updateBottom()
    }

    private fun applyFilter() {
        shownItems = if (query.isEmpty()) allItems.toList() else allItems.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
        adapter.submit(shownItems, selected, inQueue)
        tvEmpty.visibility = if (shownItems.isEmpty() && progress.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        updateBottom()
    }

    private fun updateBottom() {
        val count = selected.size
        val target = if (folderMode) "thư mục" else "danh sách"
        btnConfirm.text = if (count > 0) "Thêm vào $target ($count)" else "Chọn bài hát để thêm"
        btnConfirm.isEnabled = count > 0
        btnConfirm.alpha = if (count > 0) 1f else 0.5f
        tvSubtitle.text = if (count > 0) "Đã chọn $count • ${allItems.size} bài hát" else "${allItems.size} bài hát trong thư viện"

        val selectable = shownItems.filter { !inQueue.contains(it.uri) }
        val allSelected = selectable.isNotEmpty() && selectable.all { selected.contains(it.uri) }
        ivSelectAll.setImageResource(if (allSelected) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline)
        ivSelectAll.imageTintList = if (allSelected) null else ColorStateList.valueOf(Color.parseColor("#7A5BA8"))
    }

    // ---------------------------------------------------------------- Xác nhận thêm
    private fun confirmAdd() {
        // Chế độ thư mục: chỉ trả về các uri đã chọn (giữ đúng thứ tự thư viện), màn hình chính sẽ lưu vào thư mục
        if (folderMode) {
            val pickedUris = ArrayList(allItems.filter { selected.contains(it.uri) }.map { it.uri })
            if (pickedUris.isEmpty()) return
            setResult(RESULT_OK, Intent().putStringArrayListExtra(RESULT_URIS, pickedUris))
            finishWithAnim()
            return
        }
        val c = controller
        if (c == null) {
            Toast.makeText(this, "Đang kết nối trình phát, thử lại sau giây lát", Toast.LENGTH_SHORT).show()
            return
        }
        val picked = allItems.filter { selected.contains(it.uri) }   // giữ đúng thứ tự trong thư viện
        if (picked.isEmpty()) return
        btnConfirm.isEnabled = false

        // Đọc ảnh bìa gốc + dựng MediaItem ở luồng nền để không đứng hình
        worker.execute {
            val items = picked.map { buildMediaItem(it) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val shuffleOn = c.shuffleModeEnabled
                if (c.mediaItemCount == 0) c.setMediaItems(items) else c.addMediaItems(items)
                c.shuffleModeEnabled = shuffleOn

                setResult(RESULT_OK, Intent().putExtra(RESULT_ADDED_COUNT, items.size))
                Toast.makeText(this, "Đã thêm ${items.size} bài hát", Toast.LENGTH_SHORT).show()
                finishWithAnim()
            }
        }
    }

    private fun buildMediaItem(item: PickItem): MediaItem {
        val uri = Uri.parse(item.uri)
        var artwork: ByteArray? = null
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, uri)
            artwork = r.embeddedPicture
        } catch (_: Exception) {
        } finally {
            r.release()
        }
        if (artwork == null) {
            val bmp = BitmapFactory.decodeResource(resources, R.drawable.bg_cover_default)
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            artwork = stream.toByteArray()
        }
        val meta = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artist)
            .setDurationMs(item.durationMs)
            .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            .build()
        return MediaItem.Builder().setUri(uri).setMediaMetadata(meta).build()
    }

    // ---------------------------------------------------------------- Đọc thư viện (luồng nền)
    private fun loadSongs() {
        progress.visibility = View.VISIBLE
        worker.execute {
            val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
            val json = prefs.getString("saved_tracks", null)
            val batch = mutableListOf<PickItem>()
            try {
                if (json != null) {
                    val arr = JSONArray(json)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val item = readItem(obj.getString("uri"), obj.optString("title", ""))
                        if (item != null) batch.add(item)
                        if (batch.size >= BATCH_SIZE) {
                            val copy = batch.toList(); batch.clear()
                            runOnUiThread { if (!isDestroyed) { allItems.addAll(copy); applyFilter() } }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val rest = batch.toList()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                allItems.addAll(rest)
                progress.visibility = View.GONE
                applyFilter()
            }
        }
    }

    private fun readItem(uriStr: String, savedTitle: String): PickItem? {
        val uri = Uri.parse(uriStr)
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            val rawTitle = if (savedTitle.isNotEmpty()) savedTitle
            else (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: queryName(uri) ?: "Unknown Title")
            val artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val thumb = r.embeddedPicture?.let { decodeThumb(it) }
            PickItem(uriStr, removeAudioExtension(rawTitle), artist, duration, thumb)
        } catch (e: Exception) {
            null   // file đã bị xóa / mất quyền truy cập thì bỏ qua
        } finally {
            r.release()
        }
    }

    private fun decodeThumb(data: ByteArray): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            var sample = 1
            val shortSide = minOf(bounds.outWidth, bounds.outHeight)
            while (shortSide / (sample * 2) >= THUMB_TARGET_PX) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: Exception) {
            null
        }
    }

    private fun removeAudioExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        val known = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "wma", "opus", "amr", "mp4", "3gp", "mka")
        return if (ext in known) name.substringBeforeLast('.') else name
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cur.moveToFirst()) return cur.getString(idx)
        }
        return null
    }

    // ---------------------------------------------------------------- Trình phát + đóng màn hình
    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
        }, MoreExecutors.directExecutor())
    }

    private fun finishWithAnim() {
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishWithAnim()
    }

    override fun onDestroy() {
        worker.shutdown()
        if (::controllerFuture.isInitialized) {
            controllerFuture.addListener({
                MediaController.releaseFuture(controllerFuture)
            }, MoreExecutors.directExecutor())
        }
        super.onDestroy()
    }
}

/** Adapter danh sách chọn bài: tick tròn bên trái, ảnh bìa, tên, ca sĩ, thời lượng. */
private class PickAdapter(
    private val onClick: (PickSongsActivity.PickItem) -> Unit
) : RecyclerView.Adapter<PickAdapter.VH>() {

    companion object {
        private const val PAYLOAD_SELECTION = "selection"
    }

    private var items: List<PickSongsActivity.PickItem> = emptyList()
    private var selected: Set<String> = emptySet()
    private var inQueue: Set<String> = emptySet()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val row: View = view.findViewById(R.id.pickRow)
        val ivCheck: ImageView = view.findViewById(R.id.ivPickCheck)
        val ivThumb: ImageView = view.findViewById(R.id.ivPickThumb)
        val tvTitle: TextView = view.findViewById(R.id.tvPickTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvPickArtist)
        val tvTag: TextView = view.findViewById(R.id.tvPickTag)
    }

    fun submit(list: List<PickSongsActivity.PickItem>, sel: Set<String>, queue: Set<String>) {
        items = list
        selected = sel
        inQueue = queue
        notifyDataSetChanged()
    }

    fun notifyUriChanged(uri: String) {
        val pos = items.indexOfFirst { it.uri == uri }
        if (pos != -1) notifyItemChanged(pos, PAYLOAD_SELECTION)
    }

    fun refreshSelection() {
        notifyItemRangeChanged(0, items.size, PAYLOAD_SELECTION)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pick_song, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvArtist.text = item.artist.ifBlank { "Unknown Artist" }
        // Tên dài: chữ tự chạy lặp lại (marquee cần isSelected = true)
        holder.tvTitle.isSelected = true
        holder.tvArtist.isSelected = true
        if (item.thumb != null) holder.ivThumb.setImageBitmap(item.thumb)
        else holder.ivThumb.setImageResource(R.drawable.bg_cover_default)
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onClick(items[pos])
        }
        bindSelection(holder, item)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) bindSelection(holder, items[position])
        else super.onBindViewHolder(holder, position, payloads)
    }

    private fun bindSelection(holder: VH, item: PickSongsActivity.PickItem) {
        val already = inQueue.contains(item.uri)
        val isSel = selected.contains(item.uri)

        when {
            already -> {
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_filled)
                holder.ivCheck.imageTintList = ColorStateList.valueOf(Color.parseColor("#C9BBE6"))
                holder.tvTag.text = "Đã có"
            }
            isSel -> {
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_filled)
                holder.ivCheck.imageTintList = null
                holder.tvTag.text = formatTime(item.durationMs)
            }
            else -> {
                holder.ivCheck.setImageResource(R.drawable.ic_check_circle_outline)
                holder.ivCheck.imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                holder.tvTag.text = formatTime(item.durationMs)
            }
        }
        holder.row.alpha = if (already) 0.5f else 1f
        if (isSel) holder.row.setBackgroundResource(R.drawable.bg_current_track_highlight)
        else holder.row.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun formatTime(ms: Long): String = String.format(
        "%02d:%02d",
        TimeUnit.MILLISECONDS.toMinutes(ms),
        TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    )
}
