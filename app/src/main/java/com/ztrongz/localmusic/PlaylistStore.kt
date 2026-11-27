package com.ztrongz.localmusic

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Một danh sách phát đã lưu (hiện trong mục "Thư mục")
data class SavedPlaylist(val name: String, val uris: List<String>)

// Lưu / đọc các danh sách phát đã lưu bằng SharedPreferences (dạng JSON)
object PlaylistStore {
    private const val PREFS = "MusicAppPrefs"
    private const val KEY = "saved_playlists"

    fun getAll(context: Context): MutableList<SavedPlaylist> {
        val list = mutableListOf<SavedPlaylist>()
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return list
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriArr = obj.getJSONArray("uris")
                val uris = (0 until uriArr.length()).map { uriArr.getString(it) }
                list.add(SavedPlaylist(obj.getString("name"), uris))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun saveAll(context: Context, list: List<SavedPlaylist>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("name", p.name)
            obj.put("uris", JSONArray(p.uris))
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }

    // Kiểm tra tên đã có chưa (không phân biệt hoa thường)
    fun exists(context: Context, name: String): Boolean =
        getAll(context).any { it.name.equals(name.trim(), ignoreCase = true) }

    // Trả về false (và KHÔNG lưu) nếu tên đã tồn tại
    fun add(context: Context, name: String, uris: List<String>): Boolean {
        if (exists(context, name)) return false
        val list = getAll(context)
        list.add(SavedPlaylist(name.trim(), uris))
        saveAll(context, list)
        return true
    }

    // Bỏ 1 bài khỏi thư mục (KHÔNG xóa bài khỏi thư viện)
    fun removeSong(context: Context, folderName: String, uri: String) {
        val list = getAll(context)
        val i = list.indexOfFirst { it.name == folderName }
        if (i == -1) return
        list[i] = SavedPlaylist(folderName, list[i].uris.filter { it != uri })
        saveAll(context, list)
    }

    fun delete(context: Context, name: String) {
        val list = getAll(context)
        list.removeAll { it.name == name }
        saveAll(context, list)
    }
}
