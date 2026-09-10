// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.viewer.audio

import android.content.DialogInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import io.github.muntashirakon.AppManager.R
import io.github.muntashirakon.AppManager.utils.CpuUtils
import io.github.muntashirakon.dialog.CapsuleBottomSheetDialogFragment
import java.util.Locale

// Raw code taken from DialogMusicPlayer <https://github.com/VishnuSanal/DialogMusicPlayer>.
// Inspired by other players such as Music Player Go, Poweramp
class AudioPlayerDialogFragment : CapsuleBottomSheetDialogFragment() {

    private lateinit var mIconView: ImageView
    private lateinit var mPlaylistSizeView: TextView
    private lateinit var mTitleView: TextView
    private lateinit var mInfoView: TextView
    private lateinit var mSlider: Slider
    private lateinit var mProgressView: TextView
    private lateinit var mDurationView: TextView
    private lateinit var mPlaybackSpeedView: TextView
    private lateinit var mRewindButton: ImageView
    private lateinit var mForwardButton: ImageView
    private lateinit var mPlayPauseButton: ImageView
    private lateinit var mRepeatButton: ImageView

    private lateinit var mUpdateHandler: Handler
    private lateinit var mUpdateRunnable: Runnable

    private var mIsTimeReversed = false
    private var mShouldCheckRepeat = true

    private lateinit var mMediaPlayer: MediaPlayer
    private lateinit var mViewModel: AudioPlayerViewModel
    private var mWakeLock: PowerManager.WakeLock? = null

    private var mCloseActivity = false

    override fun initRootView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.dialog_audio_player, container, false)

    override fun onBodyInitialized(bodyView: View, savedInstanceState: Bundle?) {
        mViewModel = ViewModelProvider(this)[AudioPlayerViewModel::class.java]
        val uriList = requireArguments().getParcelableArray(ARG_URI_LIST) as Array<Uri>
        mCloseActivity = requireArguments().getBoolean(ARG_CLOSE_ACTIVITY)

        mIconView = bodyView.findViewById(android.R.id.icon)
        mPlaylistSizeView = bodyView.findViewById(R.id.size)
        mTitleView = bodyView.findViewById(android.R.id.title)
        mInfoView = bodyView.findViewById(R.id.info)
        mSlider = bodyView.findViewById(R.id.slider)
        mProgressView = bodyView.findViewById(R.id.progress)
        mDurationView = bodyView.findViewById(R.id.duration)
        mPlaybackSpeedView = bodyView.findViewById(R.id.playback_speed)
        mRewindButton = bodyView.findViewById(R.id.action_rewind)
        mForwardButton = bodyView.findViewById(R.id.action_forward)
        mPlayPauseButton = bodyView.findViewById(R.id.action_play_pause)
        mRepeatButton = bodyView.findViewById(R.id.action_repeat)

        mPlaybackSpeedView.tag = 1.0f
        mRepeatButton.tag = RepeatMode.NO_REPEAT

        mMediaPlayer = MediaPlayer()
        setListeners()
        mViewModel.getAudioMetadataLiveData().observe(viewLifecycleOwner) { setupMetadata(it) }
        mViewModel.getMediaPlayerPreparedLiveData().observe(viewLifecycleOwner) { prepared ->
            if (!prepared) return@observe
            startPlayingMedia()
            mShouldCheckRepeat = true
        }
        mViewModel.getPlaylistLoadedLiveData().observe(viewLifecycleOwner) { updatePlaylistSize() }
        mViewModel.addToPlaylist(uriList)
        mWakeLock = CpuUtils.getPartialWakeLock("pasteThread")
    }

    override fun onStart() {
        super.onStart()
        mWakeLock?.let { if (!it.isHeld) it.acquire() }
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.stop()
            mMediaPlayer.release()
        }
        super.onDismiss(dialog)
    }

    override fun onDestroy() {
        CpuUtils.releaseWakeLock(mWakeLock)
        super.onDestroy()
        if (mCloseActivity) requireActivity().finish()
    }

    private fun setListeners() {
        mSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            if (mediaPlayerInitialized()) {
                mMediaPlayer.seekTo(value.toInt())
                if (!mMediaPlayer.isPlaying) {
                    if (value != mMediaPlayer.duration.toFloat()) {
                        mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow)
                    } else {
                        resetMediaPlayer()
                    }
                }
            }
        }
        mSlider.setLabelFormatter { value -> getFormattedTime(value.toLong(), mIsTimeReversed) }

        mUpdateHandler = Handler(Looper.myLooper()!!)
        mUpdateRunnable = object : Runnable {
            override fun run() {
                if (mediaPlayerInitialized()) {
                    val currentPosition = mMediaPlayer.currentPosition
                    if (currentPosition <= mSlider.valueTo) {
                        mSlider.value = currentPosition.toFloat()
                    }
                    mProgressView.text = getFormattedTime(currentPosition.toLong(), mIsTimeReversed)
                    mUpdateHandler.postDelayed(this, 10)
                }
            }
        }

        mPlayPauseButton.setOnClickListener {
            if (mediaPlayerInitialized()) {
                if (!mMediaPlayer.isPlaying) {
                    if (mMediaPlayer.currentPosition == mMediaPlayer.duration) {
                        mMediaPlayer.seekTo(0)
                    }
                    resumeMediaPlayer()
                } else {
                    pauseMediaPlayer()
                }
            }
        }
        mPlayPauseButton.setOnLongClickListener {
            mMediaPlayer.seekTo(0)
            true
        }

        mProgressView.setOnClickListener { mIsTimeReversed = !mIsTimeReversed }

        mPlaybackSpeedView.setOnClickListener {
            val speed = mPlaybackSpeedView.tag as Float
            when {
                speed == 0.5f -> { mPlaybackSpeedView.setText("0.75x"); mPlaybackSpeedView.tag = 0.75f }
                speed == 0.75f -> { mPlaybackSpeedView.setText("1x"); mPlaybackSpeedView.tag = 1f }
                speed == 1.0f -> { mPlaybackSpeedView.setText("1.25x"); mPlaybackSpeedView.tag = 1.25f }
                speed == 1.25f -> { mPlaybackSpeedView.setText("1.5x"); mPlaybackSpeedView.tag = 1.5f }
                speed == 1.5f -> { mPlaybackSpeedView.setText("2.0x"); mPlaybackSpeedView.tag = 2.0f }
                speed == 2.0f -> { mPlaybackSpeedView.setText("0.5x"); mPlaybackSpeedView.tag = 0.5f }
            }
            updatePlaybackSpeed()
        }

        mRepeatButton.setOnClickListener {
            @RepeatMode val state = mRepeatButton.tag as Int
            when (state) {
                RepeatMode.NO_REPEAT -> {
                    mRepeatButton.setImageResource(R.drawable.ic_repeat)
                    mRepeatButton.tag = RepeatMode.REPEAT_INDEFINITELY
                }
                RepeatMode.REPEAT_INDEFINITELY -> {
                    mRepeatButton.setImageResource(R.drawable.ic_repeat_one)
                    mRepeatButton.tag = RepeatMode.REPEAT_SINGLE_INDEFINITELY
                }
                RepeatMode.REPEAT_SINGLE_INDEFINITELY -> {
                    mRepeatButton.setImageResource(R.drawable.ic_repeat_off)
                    mRepeatButton.tag = RepeatMode.NO_REPEAT
                }
            }
        }

        mRewindButton.setOnClickListener {
            if (mediaPlayerInitialized()) mMediaPlayer.seekTo(mMediaPlayer.currentPosition - 10_000)
        }
        mRewindButton.setOnLongClickListener {
            mViewModel.playPrevious()
            true
        }

        mForwardButton.setOnClickListener {
            if (mediaPlayerInitialized()) mMediaPlayer.seekTo(mMediaPlayer.currentPosition + 10_000)
        }
        mForwardButton.setOnLongClickListener {
            mViewModel.playNext(false)
            true
        }

        mMediaPlayer.setOnCompletionListener { mediaPlayer ->
            if (!mShouldCheckRepeat) {
                mShouldCheckRepeat = true
                return@setOnCompletionListener
            }
            @RepeatMode val state = mRepeatButton.tag as Int
            resetMediaPlayer()
            if (state == RepeatMode.REPEAT_SINGLE_INDEFINITELY) {
                if (mediaPlayer.currentPosition == mediaPlayer.duration) mediaPlayer.seekTo(0)
                resumeMediaPlayer()
            } else {
                mViewModel.playNext(state)
            }
        }
    }

    private fun updatePlaybackSpeed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayerInitialized()) {
            val isPlaying = mMediaPlayer.isPlaying
            mMediaPlayer.playbackParams = mMediaPlayer.playbackParams.setSpeed(mPlaybackSpeedView.tag as Float)
            if (!isPlaying) mMediaPlayer.pause()
        }
    }

    private fun pauseMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.pause()
            mPlayPauseButton.setImageResource(R.drawable.ic_play_arrow)
        }
    }

    private fun resumeMediaPlayer() {
        if (mediaPlayerInitialized()) {
            mMediaPlayer.start()
            mPlayPauseButton.setImageResource(R.drawable.ic_pause)
        }
    }

    private fun resetMediaPlayer() {
        mPlayPauseButton.setImageResource(R.drawable.ic_replay)
    }

    private fun getFormattedTime(millis: Long, isTimeReversed: Boolean): String {
        if (!mediaPlayerInitialized()) return "00:00"
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        val secondsStr = seconds.toString()
        val secs = if (secondsStr.length >= 2) secondsStr.substring(0, 2) else "0$secondsStr"
        if (!isTimeReversed) return "$minutes:$secs"
        return "-${getFormattedTime(mMediaPlayer.duration - millis, false)}"
    }

    private fun mediaPlayerInitialized(): Boolean {
        return try {
            mMediaPlayer.isPlaying
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    private fun updatePlaylistSize() {
        val currentAudio = mViewModel.getCurrentPlaylistIndex() + 1
        val playlistSize = mViewModel.playlistSize()
        mPlaylistSizeView.visibility = if (playlistSize > 1) View.VISIBLE else View.GONE
        mPlaylistSizeView.text = String.format(Locale.ROOT, "%d/%d", currentAudio, playlistSize)
    }

    private fun setupMetadata(meta: AudioMetadata) {
        if (meta.cover != null) {
            mIconView.setImageBitmap(meta.cover)
        } else {
            mIconView.setImageResource(R.drawable.ic_audio_file)
        }
        updatePlaylistSize()
        mTitleView.text = String.format(Locale.ROOT, "%s - %s", meta.artist, meta.title)
        mTitleView.isSelected = true
        mInfoView.text = String.format(Locale.ROOT, "%s - %s", meta.albumArtist, meta.album)
        mInfoView.isSelected = true

        mShouldCheckRepeat = false
        mViewModel.prepareMediaPlayer(mMediaPlayer, meta.uri)
    }

    private fun startPlayingMedia() {
        val duration = mMediaPlayer.duration
        mSlider.valueFrom = 0f
        mSlider.valueTo = duration.toFloat()
        mDurationView.text = getFormattedTime(duration.toLong(), mIsTimeReversed)
        mMediaPlayer.seekTo(0)
        resumeMediaPlayer()
        mUpdateHandler.postDelayed(mUpdateRunnable, 0)
        mPlayPauseButton.setImageResource(R.drawable.ic_pause)
    }

    companion object {
        const val TAG = "AudioPlayerDialogFragment"
        private const val ARG_URI_LIST = "uris"
        private const val ARG_CLOSE_ACTIVITY = "close"

        @JvmStatic
        fun getInstance(uriList: Array<Uri>, closeActivity: Boolean): AudioPlayerDialogFragment {
            return AudioPlayerDialogFragment().apply {
                arguments = Bundle().apply {
                    putParcelableArray(ARG_URI_LIST, uriList)
                    putBoolean(ARG_CLOSE_ACTIVITY, closeActivity)
                }
            }
        }
    }
}
