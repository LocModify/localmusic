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

        // Lắng nghe sự thay đổi trạng thái của Player
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateAllControllers()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateAllControllers()
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

                // SỬA LỖI ĐẢO NGƯỢC: Truyền session trực tiếp vào đây thay vì dùng biến toàn cục
                override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                    super.onPostConnect(session, controller)
                    session.setCustomLayout(controller, buildCustomLayout(session.player))
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
                        }
                        ACTION_REPEAT -> {
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

    // Hàm tạo danh sách nút bấm (Tách riêng ra cho gọn)
    private fun buildCustomLayout(player: Player): List<CommandButton> {
        val shuffleIcon = if (player.shuffleModeEnabled) {
            R.drawable.ic_shuffle_on
        } else {
            R.drawable.ic_shuffle_off
        }

        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            else -> R.drawable.ic_repeat_off
        }

        return listOf(
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
    }

    // Hàm cập nhật cho tất cả các controller đang kết nối
    private fun updateAllControllers() {
        val session = mediaSession ?: return
        val customLayout = buildCustomLayout(session.player)
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