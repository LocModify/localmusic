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

        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false

        // Lắng nghe sự thay đổi trạng thái của Player để cập nhật lại thông báo
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNotificationLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotificationLayout()
            }
        })

        val session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(ACTION_SET_PAUSE_AT_END, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                        .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                    super.onPostConnect(session, controller)
                    // Cập nhật icon ngay khi vừa kết nối (mở thông báo)
                    updateNotificationLayout()
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
                            session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                            // Không cần gọi updateNotificationLayout() ở đây vì Listener ở trên đã tự bắt sự kiện
                        }
                        ACTION_REPEAT -> {
                            val currentMode = session.player.repeatMode
                            val newMode = when (currentMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            session.player.repeatMode = newMode
                            // Không cần gọi updateNotificationLayout() ở đây vì Listener ở trên đã tự bắt sự kiện
                        }
                        else -> return super.onCustomCommand(session, controller, customCommand, args)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        mediaSession = session
    }

    // Hàm cập nhật icon động cho thông báo
    private fun updateNotificationLayout() {
        val session = mediaSession ?: return
        val player = session.player

        // 1. Xác định icon Shuffle (Bật/Tắt)
        val shuffleIcon = if (player.shuffleModeEnabled) {
            R.drawable.ic_shuffle_on
        } else {
            R.drawable.ic_shuffle_off
        }

        // 2. Xác định icon Repeat (Tắt / Lặp tất cả / Lặp 1 bài)
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat_off
        }

        // 3. Tạo lại danh sách nút bấm với icon mới
        val customLayout = listOf(
            CommandButton.Builder()
                .setDisplayName("Shuffle")
                .setIconResId(shuffleIcon)
                .setSessionCommand(SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY))
                .build(),
            CommandButton.Builder()
                .setDisplayName("Repeat")
                .setIconResId(repeatIcon)
                .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .build()
        )

        // 4. Gửi cập nhật cho tất cả các controller (bao gồm cả thông báo và app)
        for (controller in session.connectedControllers) {
            session.setCustomLayout(controller, customLayout)
        }
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