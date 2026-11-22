package com.ztrongz.localmusic

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    // Định nghĩa các hằng số cho Custom Command để dễ quản lý
    companion object {
        private const val ACTION_SET_PAUSE_AT_END = "SET_PAUSE_AT_END"
        private const val ACTION_SHUFFLE = "ACTION_SHUFFLE"
        private const val ACTION_REPEAT = "ACTION_REPEAT"
    }

    override fun onCreate() {
        super.onCreate()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Mặc định tắt lặp và tắt xáo trộn
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        val session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    // Đăng ký các Custom Command (bao gồm cả Shuffle và Repeat)
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_SET_PAUSE_AT_END, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                // Hàm này dùng để gửi các nút lên giao diện thông báo (Notification)
                override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                    super.onPostConnect(session, controller)
                    
                    // Tạo danh sách các nút bấm (Shuffle và Repeat)
                    // Lưu ý: Anh cần chắc chắn đã có file ic_shuffle và ic_repeat trong thư mục drawable
                    val customLayout = listOf(
                        CommandButton.Builder()
                            .setDisplayName("Shuffle")
                            .setIconResId(R.drawable.ic_shuffle)
                            .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                            .build(),
                        CommandButton.Builder()
                            .setDisplayName("Repeat")
                            .setIconResId(R.drawable.ic_repeat)
                            .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                            .build()
                    )
                    // Gửi layout lên thông báo
                    session.setCustomLayout(controller, customLayout)
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_SET_PAUSE_AT_END -> {
                            val pause = args.getBoolean("pause")
                            if (session.player is ExoPlayer) {
                                (session.player as ExoPlayer).pauseAtEndOfMediaItems = pause
                            }
                        }
                        
                        ACTION_SHUFFLE -> {
                            // Đảo trạng thái Shuffle (Bật <-> Tắt)
                            session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                            // (Tùy chọn) Anh có thể gọi lại onPostConnect ở đây nếu muốn icon đổi màu khi bật/tắt
                        }
                        
                        ACTION_REPEAT -> {
                            // Chuyển đổi vòng lặp: Tắt -> Lặp tất cả -> Lặp 1 bài -> Tắt
                            val currentMode = session.player.repeatMode
                            val newMode = when (currentMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            session.player.repeatMode = newMode
                        }
                        
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        mediaSession = session
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}