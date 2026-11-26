package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ztrongz.localmusic.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val tracks = mutableListOf<Track>()
    private lateinit var songAdapter: SongAdapter

    private var isShowingFavorites = false
    private var currentQuery = ""

    // Tab "Thư mục": isShowingFolders = đang ở tab; openFolder != null = đang mở bên trong 1 thư mục
    private lateinit var folderAdapter: FolderAdapter
    private var isShowingFolders = false
    private var openFolder: SavedPlaylist? = null
    private var defaultSearchHint: CharSequence? = null

    // Tab hiện tại: 0 = Tất cả, 1 = Yêu thích, 2 = Thư mục (vuốt trái/phải để đổi tab)
    private var currentTab = 0
    private var isSwitching = false
    // Trạng thái kéo (vuốt) ngang trên danh sách
    private var swipeTracking = false     // đang theo dõi cú chạm bắt đầu trong vùng danh sách
    private var swipeDragging = false     // đã khóa là vuốt ngang -> danh sách đi theo ngón tay
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeVelocity: VelocityTracker? = null

    // Nút Back: đang mở thư mục thì quay về danh sách thư mục
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeFolder()
        }
    }

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult

        val newlyAddedTracks = mutableListOf<Track>()

        uris.forEach { uri ->
            val track = extractTrackInfo(uri)
            if (track != null && tracks.none { it.uri == uri }) {
                tracks.add(track)
                newlyAddedTracks.add(track)
            }
        }
        persistPermission(uris)

        if (newlyAddedTracks.isNotEmpty()) {
            addTracksToPlaylist(newlyAddedTracks)
            saveTracksToStorage()
            filterAndShowSongs()
        }
    }

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songAdapter = SongAdapter(
            songs = tracks,
            onSongClick = { track -> 
                val index = tracks.indexOf(track)
                if (index != -1) playSongAt(index)
            },
            onMoreClick = { track, view -> 
                val index = tracks.indexOf(track)
                if (index != -1) showSongOptions(index, view)
            }
        )
        folderAdapter = FolderAdapter(
            countOf = { p -> tracks.count { t -> p.uris.contains(t.uri.toString()) } },
            onFolderClick = { p -> openFolderView(p) },
            onMoreClick = { p, view -> showFolderMenu(p, view) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = songAdapter
        defaultSearchHint = binding.etSearch.hint
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding.folderBackBar.setOnClickListener { closeFolder() }   // thanh "← Tên thư mục"

        setupCategoryChips()
        setupSearchBar()

        binding.miniPlayerLayout.setOnClickListener {
            if (controller?.currentMediaItem != null) {
                val intent = Intent(this, NowPlayingActivity::class.java)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_up, 0)
            }
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnAdd.setOnClickListener {
            pickFiles.launch(arrayOf("audio/*"))
        }

        binding.btnPlayPause.setOnClickListener {
            controller?.let { c ->
                if (c.isPlaying) c.pause() else c.play()
            }
        }

        binding.btnNext.setOnClickListener { controller?.seekToNextMediaItem() }
        binding.btnPrev.setOnClickListener { controller?.seekToPreviousMediaItem() }

        loadTracksFromStorage()
        connectController()
    }

    override fun onResume() {
        super.onResume()
        // Chỉ cập nhật trạng thái Yêu thích, KHÔNG đồng bộ xóa bài từ Player
        refreshFavoriteStatus()

        // Quay lại từ màn hình khác: cập nhật tab Thư mục (có thể vừa lưu thêm thư mục mới)
        if (isShowingFolders) {
            openFolder = openFolder?.let { f -> PlaylistStore.getAll(this).firstOrNull { it.name == f.name } }
            if (openFolder == null) {
                backCallback.isEnabled = false
                binding.etSearch.hint = defaultSearchHint
            }
            filterAndShowSongs()
        }
    }

    private fun refreshFavoriteStatus() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            var changed = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uri = obj.getString("uri")
                val isFav = obj.optBoolean("isFavorite", false)

                val index = tracks.indexOfFirst { it.uri.toString() == uri }
                if (index != -1 && tracks[index].isFavorite != isFav) {
                    tracks[index] = tracks[index].copy(isFavorite = isFav)
                    changed = true
                }
            }
            if (changed) {
                filterAndShowSongs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupSearchBar() {
        binding.etSearch.doAfterTextChanged { text ->
            currentQuery = text.toString().trim()
            filterAndShowSongs()
        }
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener { selectTab(0) }
        binding.chipFav.setOnClickListener { selectTab(1) }
        binding.chipFolder.setOnClickListener { selectTab(2) }
    }

    // Chuyển tab (có hiệu ứng trượt). Chip được tô sáng ngay để cảm giác phản hồi nhanh.
    private fun selectTab(index: Int) {
        if (isSwitching) return
        val chips = listOf(binding.chipAll, binding.chipFav, binding.chipFolder)
        val dir = when {
            index > currentTab -> 1
            index < currentTab -> -1
            else -> 0
        }
        // Bấm lại đúng tab đang đứng: chỉ có tác dụng khi đang mở trong 1 thư mục (về danh sách thư mục)
        if (dir == 0 && !(index == 2 && openFolder != null)) return

        highlightChip(chips[index])
        animateListSwitch(if (dir == 0) -1 else dir) {
            when (index) {
                0 -> { isShowingFavorites = false; leaveFolders() }
                1 -> { isShowingFavorites = true; leaveFolders() }
                else -> {
                    isShowingFavorites = false
                    isShowingFolders = true
                    openFolder = null
                    backCallback.isEnabled = false
                    binding.etSearch.hint = defaultSearchHint
                }
            }
            currentTab = index
            filterAndShowSongs()
        }
    }

    // Hiệu ứng đổi danh sách: danh sách cũ trượt ra + mờ dần, đổi dữ liệu, danh sách mới trượt vào từ phía đối diện
    // dir = 1: đi tới (nội dung trượt sang trái), dir = -1: đi lùi (trượt sang phải)
    private fun animateListSwitch(dir: Int, change: () -> Unit) {
        val rv = binding.recyclerView
        isSwitching = true
        val distance = rv.width * 0.18f          // quãng trượt (tăng số này = trượt xa hơn)
        // Quãng trượt ra: nếu vừa kéo bằng tay thì trượt tiếp từ vị trí hiện tại, không bị giật lùi
        val exitDistance = maxOf(distance, abs(rv.translationX) + rv.width * 0.08f)
        rv.animate().cancel()
        rv.animate()
            .translationX(-dir * exitDistance)
            .alpha(0f)
            .setDuration(120)                     // thời gian trượt ra (ms)
            .withLayer()
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                change()
                rv.scrollToPosition(0)
                rv.translationX = dir * distance
                rv.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(260)             // thời gian trượt vào (ms)
                    .withLayer()
                    .setInterpolator(DecelerateInterpolator(1.8f))
                    .withEndAction { isSwitching = false }
                    .start()
            }
            .start()
    }

    // ===== Vuốt ngang trên danh sách để đổi tab: danh sách đi theo ngón tay, thả ra thì chuyển/hoàn lại =====
    // Có thể chuyển theo hướng dir (1 = tab kế, -1 = lùi) không?
    private fun canSwipe(dir: Int): Boolean {
        if (openFolder != null) return dir == -1            // đang mở thư mục: chỉ vuốt phải để thoát
        return (currentTab + dir) in 0..2
    }

    private fun commitSwipe(dir: Int) {
        if (openFolder != null) closeFolder() else selectTab(currentTab + dir)
    }

    // Trả danh sách về vị trí cũ (khi vuốt chưa đủ xa hoặc đang ở tab đầu/cuối)
    private fun cancelSwipe() {
        val rv = binding.recyclerView
        isSwitching = true
        rv.animate().cancel()
        rv.animate()
            .translationX(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction {
                rv.setLayerType(View.LAYER_TYPE_NONE, null)
                isSwitching = false
            }
            .start()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val rv = binding.recyclerView
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rect = Rect()
                // Chỉ nhận vuốt khi ngón tay bắt đầu trong vùng danh sách và không đang chạy hiệu ứng
                swipeTracking = !isSwitching && rv.getGlobalVisibleRect(rect) &&
                    rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
                swipeDragging = false
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
                swipeVelocity?.recycle()
                swipeVelocity = if (swipeTracking) VelocityTracker.obtain().also { it.addMovement(ev) } else null
            }

            MotionEvent.ACTION_MOVE -> if (swipeTracking) {
                swipeVelocity?.addMovement(ev)
                val dx = ev.rawX - swipeStartX
                val dy = ev.rawY - swipeStartY
                val slop = ViewConfiguration.get(this).scaledTouchSlop

                if (!swipeDragging) {
                    if (abs(dx) > slop && abs(dx) > abs(dy) * 1.2f) {
                        // Khóa vuốt ngang: hủy cú chạm đang gửi cho item (tránh bấm nhầm / cuộn dọc)
                        swipeDragging = true
                        val cancel = MotionEvent.obtain(ev)
                        cancel.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancel)
                        cancel.recycle()
                        rv.animate().cancel()
                        rv.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    } else if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        swipeTracking = false                 // cuộn dọc bình thường
                    }
                }

                if (swipeDragging) {
                    val dir = if (dx < 0) 1 else -1
                    if (canSwipe(dir)) {
                        rv.translationX = dx * 0.9f           // đi theo ngón tay
                        rv.alpha = 1f - 0.5f * min(abs(dx) / rv.width, 1f)
                    } else {
                        rv.translationX = dx * 0.25f          // đầu/cuối: kéo nặng tay, thả ra sẽ bật lại
                        rv.alpha = 1f
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeDragging) {
                    swipeVelocity?.addMovement(ev)
                    swipeVelocity?.computeCurrentVelocity(1000)
                    val vx = swipeVelocity?.xVelocity ?: 0f
                    val dx = ev.rawX - swipeStartX
                    val dir = if (dx < 0) 1 else -1
                    // Vuốt nhẹ cũng được: chỉ cần kéo qua ~20% chiều rộng, hoặc vuốt nhanh một đoạn ngắn
                    val farEnough = abs(dx) > rv.width * 0.20f
                    val fastEnough = abs(vx) > 700f && abs(dx) > rv.width * 0.06f && (vx < 0) == (dx < 0)
                    swipeDragging = false
                    swipeTracking = false
                    swipeVelocity?.recycle()
                    swipeVelocity = null
                    if (ev.actionMasked == MotionEvent.ACTION_UP && canSwipe(dir) && (farEnough || fastEnough)) {
                        rv.setLayerType(View.LAYER_TYPE_NONE, null)
                        commitSwipe(dir)
                    } else {
                        cancelSwipe()
                    }
                    return true
                }
                swipeTracking = false
                swipeVelocity?.recycle()
                swipeVelocity = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Thoát khỏi tab Thư mục
    private fun leaveFolders() {
        isShowingFolders = false
        openFolder = null
        backCallback.isEnabled = false
        binding.etSearch.hint = defaultSearchHint
    }

    private fun openFolderView(p: SavedPlaylist) {
        if (isSwitching) return
        animateListSwitch(1) {
            openFolder = p
            backCallback.isEnabled = true
            binding.etSearch.hint = "Tìm trong \"${p.name}\"..."
            filterAndShowSongs()
        }
    }

    private fun closeFolder() {
        if (isSwitching) return
        animateListSwitch(-1) {
            openFolder = null
            backCallback.isEnabled = false
            binding.etSearch.hint = defaultSearchHint
            filterAndShowSongs()
        }
    }

    // Menu dấu 3 chấm của thư mục: xóa thư mục (không xóa bài hát)
    private fun showFolderMenu(p: SavedPlaylist, anchor: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_folder_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 15f

        popupView.findViewById<View>(R.id.btnFolderDelete).setOnClickListener {
            popup.dismiss()
            PlaylistStore.delete(this, p.name)
            Toast.makeText(this, "Đã xóa thư mục", Toast.LENGTH_SHORT).show()
            filterAndShowSongs()
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, anchor.width - popupView.measuredWidth - 16, 0)
    }

    // Tô sáng chip đang chọn, các chip còn lại về trạng thái thường
    private fun highlightChip(active: View) {
        listOf(binding.chipAll, binding.chipFav, binding.chipFolder).forEach { chip ->
            val isActive = chip === active
            chip.setBackgroundResource(if (isActive) R.drawable.bg_category_active else R.drawable.bg_category_inactive)
            if (chip is TextView) {
                chip.setTextColor(if (isActive) Color.WHITE else Color.parseColor("#1F1B2E"))
                chip.compoundDrawableTintList = ContextCompat.getColorStateList(
                    this, if (isActive) android.R.color.white else android.R.color.black
                )
            }
        }
    }

    private fun filterAndShowSongs() {
        // Thanh quay lại chỉ hiện khi đang mở bên trong 1 thư mục
        val bar = binding.folderBackBar
        if (openFolder != null) {
            if (bar.visibility != View.VISIBLE) {
                bar.alpha = 0f
                bar.visibility = View.VISIBLE
                bar.animate().alpha(1f).setDuration(220).start()   // thanh quay lại hiện dần
            }
        } else {
            bar.animate().cancel()
            bar.alpha = 1f
            bar.visibility = View.GONE
        }
        binding.tvFolderTitle.text = openFolder?.name ?: ""

        // Tab Thư mục (chưa mở thư mục nào): hiện danh sách thư mục
        if (isShowingFolders && openFolder == null) {
            if (binding.recyclerView.adapter !== folderAdapter) binding.recyclerView.adapter = folderAdapter
            var folders: List<SavedPlaylist> = PlaylistStore.getAll(this)
            if (currentQuery.isNotEmpty()) {
                folders = folders.filter { it.name.contains(currentQuery, ignoreCase = true) }
            }
            folderAdapter.updateData(folders)
            return
        }
        if (binding.recyclerView.adapter !== songAdapter) binding.recyclerView.adapter = songAdapter

        var displayList: List<Track> = tracks

        // Đang mở 1 thư mục: chỉ hiện các bài thuộc thư mục đó
        openFolder?.let { f ->
            displayList = displayList.filter { f.uris.contains(it.uri.toString()) }
        }

        if (isShowingFavorites) {
            displayList = displayList.filter { it.isFavorite }
        }

        if (currentQuery.isNotEmpty()) {
            displayList = displayList.filter { 
                it.title.contains(currentQuery, ignoreCase = true) || 
                it.artist.contains(currentQuery, ignoreCase = true)
            }
        }

        songAdapter.updateData(displayList)
    }

    private fun saveTracksToStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        tracks.forEach { 
            val obj = JSONObject()
            obj.put("uri", it.uri.toString())
            obj.put("isFavorite", it.isFavorite)
            obj.put("title", it.title)
            jsonArray.put(obj) 
        }
        prefs.edit().putString("saved_tracks", jsonArray.toString()).apply()
    }

    private fun loadTracksFromStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return

        try {
            val jsonArray = JSONArray(jsonString)
            tracks.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uri = Uri.parse(obj.getString("uri"))
                val isFav = obj.optBoolean("isFavorite", false)
                val savedTitle = obj.optString("title", "")

                val track = extractTrackInfo(uri)
                if (track != null) {
                    val finalTitle = removeAudioExtension(if (savedTitle.isNotEmpty()) savedTitle else track.title)
                    tracks.add(track.copy(title = finalTitle, isFavorite = isFav))
                }
            }
            filterAndShowSongs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSongOptions(position: Int, view: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_song_options, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 15f

        val track = tracks[position]

        val btnFavorite = popupView.findViewById<View>(R.id.btnMenuFavorite)
        val ivHeart = popupView.findViewById<ImageView>(R.id.ivMenuHeart)
        val tvFavorite = popupView.findViewById<TextView>(R.id.tvMenuFavorite)
        val btnRename = popupView.findViewById<View>(R.id.btnMenuRename)
        val btnShare = popupView.findViewById<View>(R.id.btnMenuShare)
        val btnDelete = popupView.findViewById<View>(R.id.btnMenuDelete)

        tvFavorite.setTextColor(Color.parseColor("#1F1B2E"))

        fun updateFavoriteUI(isFav: Boolean) {
            if (isFav) {
                ivHeart.setImageResource(R.drawable.ic_heart_filled)
                tvFavorite.text = "Bỏ yêu thích"
            } else {
                ivHeart.setImageResource(R.drawable.ic_heart_outline)
                tvFavorite.text = "Yêu thích"
            }
        }

        updateFavoriteUI(track.isFavorite)

        btnFavorite.setOnClickListener {
            toggleFavorite(position)
            updateFavoriteUI(tracks[position].isFavorite)
        }

        btnRename.setOnClickListener {
            popupWindow.dismiss()
            showRenameDialog(position)
        }

        btnShare.setOnClickListener {
            popupWindow.dismiss()
            ShareHelper.shareSong(this, track.uri, track.title)
        }

        btnDelete.setOnClickListener {
            popupWindow.dismiss()
            deleteSong(position)
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val offsetX = view.width - popupWidth - 16
        popupWindow.showAsDropDown(view, offsetX, 0)
    }

    private fun toggleFavorite(position: Int) {
        val track = tracks[position]
        tracks[position] = track.copy(isFavorite = !track.isFavorite)
        saveTracksToStorage()
        filterAndShowSongs() 
    }

    private fun showRenameDialog(position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtTitle = dialogView.findViewById<EditText>(R.id.edtNewTitle)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnSave)

        val currentTitle = tracks[position].title
        val titleWithoutExtension = removeAudioExtension(currentTitle)

        edtTitle.setText(titleWithoutExtension)
        edtTitle.setSelection(edtTitle.text.length)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = edtTitle.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                tracks[position] = tracks[position].copy(title = newTitle)
                saveTracksToStorage()
                filterAndShowSongs()
                updatePlayerPlaylist()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun deleteSong(position: Int) {
        tracks.removeAt(position)
        updatePlayerPlaylist()
        saveTracksToStorage()
        filterAndShowSongs()
    }

    // SỬA LẠI HÀM NÀY ĐỂ TÌM BÀI HÁT BẰNG URI THAY VÌ INDEX
    private fun playSongAt(position: Int) {
        val c = controller ?: return
        val track = tracks[position]
        val trackUri = track.uri

        // Tìm xem bài hát này có đang nằm trong Player (Hàng đợi) không
        var targetIndex = -1
        for (i in 0 until c.mediaItemCount) {
            if (c.getMediaItemAt(i).localConfiguration?.uri == trackUri) {
                targetIndex = i
                break
            }
        }

        if (targetIndex != -1) {
            // Nếu có trong Hàng đợi -> Chỉ cần seek tới và phát
            c.seekTo(targetIndex, 0L)
            c.play()
        } else {
            // Nếu không có (do đã bị xóa khỏi Hàng đợi) -> Chèn lại vào sau bài hiện tại và phát
            val newItem = buildMediaItem(track)
            val insertIndex = if (c.mediaItemCount == 0) 0 else c.currentMediaItemIndex + 1
            c.addMediaItem(insertIndex, newItem)
            c.seekTo(insertIndex, 0L)
            c.play()
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)

            if (controller?.mediaItemCount == 0 && tracks.isNotEmpty()) {
                val c = controller ?: return@addListener
                val items = tracks.map { t -> buildMediaItem(t) }
                c.setMediaItems(items)
            }
            updateMiniPlayerUI()
            filterAndShowSongs()
        }, MoreExecutors.directExecutor())
    }

    private fun updateMiniPlayerUI() {
        val c = controller ?: return
        val currentMediaItem = c.currentMediaItem

        binding.tvNowPlaying.text = currentMediaItem?.mediaMetadata?.title ?: "Chưa phát bài nào"
        val artistText = currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""

        if (artistText.isBlank()) {
            binding.tvMiniArtist.visibility = View.GONE
        } else {
            binding.tvMiniArtist.visibility = View.VISIBLE
            binding.tvMiniArtist.text = artistText
        }

        val currentUri = currentMediaItem?.localConfiguration?.uri
        val currentTrack = tracks.find { it.uri == currentUri }
        
        if (currentTrack != null && currentTrack.cover != null) {
            binding.ivMiniCover.setImageBitmap(currentTrack.cover)
        } else {
            // Nếu bài đang phát không có trong tracks (do bị xóa), thử lấy từ Artwork
            val artworkData = currentMediaItem?.mediaMetadata?.artworkData
            if (artworkData != null) {
                val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                binding.ivMiniCover.setImageBitmap(bitmap)
            } else {
                binding.ivMiniCover.setImageResource(R.drawable.bg_cover_default)
            }
        }

        binding.btnPlayPause.setImageResource(
            if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateMiniPlayerUI()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateMiniPlayerUI()
        }
    }

    private fun buildMediaItem(track: Track): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setDurationMs(track.duration)

        if (track.cover != null) {
            val stream = ByteArrayOutputStream()
            track.cover.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        } else {
            metadataBuilder.setArtworkData(getDefaultArtworkBytes(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun updatePlayerPlaylist() {
        val c = controller ?: return
        val wasPlaying = c.isPlaying
        val currentPos = c.currentMediaItemIndex
        val currentTime = c.currentPosition
        val wasShuffleOn = c.shuffleModeEnabled 

        val items = tracks.map { t -> buildMediaItem(t) }

        c.setMediaItems(items, if (currentPos < items.size) currentPos else 0, currentTime)
        c.shuffleModeEnabled = wasShuffleOn 
        if (wasPlaying) c.play()
    }

    private fun addTracksToPlaylist(newTracks: List<Track>) {
        val c = controller ?: return
        val wasShuffleOn = c.shuffleModeEnabled 
        val newItems = newTracks.map { t -> buildMediaItem(t) }

        if (c.mediaItemCount == 0) c.setMediaItems(newItems) else c.addMediaItems(newItems)
        c.shuffleModeEnabled = wasShuffleOn 
    }

    private fun getDefaultArtworkBytes(): ByteArray {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_cover_default)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    private fun extractTrackInfo(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val title = removeAudioExtension(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: queryName(uri) ?: "Unknown Title")
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            val embeddedPicture = retriever.embeddedPicture
            var coverBitmap: Bitmap? = null
            if (embeddedPicture != null) {
                coverBitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
            }
            Track(uri, title, artist, duration, coverBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
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

    private fun persistPermission(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {}
        }
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controllerFuture.addListener({
            MediaController.releaseFuture(controllerFuture)
        }, MoreExecutors.directExecutor())
        super.onDestroy()
    }
}