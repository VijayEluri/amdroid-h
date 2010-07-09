package com.sound.ampache.utility;

/* Copyright (c) 2010 ABAAKOUK Mehdi <theli48@gmail.com>
 *
 * For the PhoneStateListener:
 *  Copyright (c) 2008 Kevin James Purdy <purdyk@onid.orst.edu>
 * 
 * Amdroid Port:
 *  Copyright (c) 2010 Jacob Alexander < haata@users.sf.net >
 *  Copyrigth (c) 2010 Michael Gapczynski <GapczynskiM@gmail.com>
 *
 * +------------------------------------------------------------------------+
 * | This program is free software; you can redistribute it and/or          |
 * | modify it under the terms of the GNU General Public License            |
 * | as published by the Free Software Foundation; either version 2         |
 * | of the License, or (at your option) any later version.                 |
 * |                                                                        |
 * | This program is distributed in the hope that it will be useful,        |
 * | but WITHOUT ANY WARRANTY; without even the implied warranty of         |
 * | MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          |
 * | GNU General Public License for more details.                           |
 * |                                                                        |
 * | You should have received a copy of the GNU General Public License      |
 * | along with this program; if not, write to the Free Software            |
 * | Foundation, Inc., 59 Temple Place - Suite 330,                         |
 * | Boston, MA  02111-1307, USA.                                           |
 * +------------------------------------------------------------------------+
 */
import java.util.ArrayList;

import com.sound.ampache.objects.Media;
import com.sound.ampache.objects.Song;
import com.sound.ampache.objects.Video;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class Player {

	private MediaPlayer mPlayer;

	private static String TAG = "AmdroidPlayer";
	private Song mSong;
	private Video mVideo;
	private int mBuffering = -1;

	private Boolean mPlayAfterPrepared = true;

	private enum STATE {
		Idle, Initialised, Prepared, Started, Paused, Stopped
	}

	private STATE mState;

	private Context mContext;

	private MyPhoneStateListener mPhoneStateListener;
	private MyMediaPlayerListener mMediaPlayerListener;

	private ArrayList<PlayerListener> mPlayerListeners;

	public static abstract class PlayerListener {
		abstract public void onTogglePlaying(boolean playing);

		abstract public void onNewSongPlaying(Song song);
		abstract public void onNewVideoPlaying(Video video);

		abstract public void onBuffering(int buffer);
		abstract public void onPlayerStopped();
	}

	public Player(Context context) {

		mContext = context;

		mPhoneStateListener = new MyPhoneStateListener();

		mPlayerListeners = new ArrayList<PlayerListener>();

		mMediaPlayerListener = new MyMediaPlayerListener();

		mPlayer = new MediaPlayer();
		mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mPlayer.setOnErrorListener(mMediaPlayerListener);
		mPlayer.setOnPreparedListener(mMediaPlayerListener);
		mPlayer.setOnCompletionListener(mMediaPlayerListener);
		mPlayer.setOnBufferingUpdateListener(mMediaPlayerListener);

		setState(STATE.Idle);
	}

	public void stop() {
		mPlayer.stop();
		setState( STATE.Stopped );
	}

	private void setState(STATE state) {
		mState = state;

		for (PlayerListener obj : mPlayerListeners) {
			obj.onTogglePlaying(isPlaying());
		}

		String st = "";
		switch (state) {
		case Idle:
			st = "Idle";
			break;
		case Initialised:
			st = "Initialised";
			break;
		case Prepared:
			st = "Prepared";
			break;
		case Started:
			st = "Started";
			break;
		case Paused:
			st = "Paused";
			break;
		case Stopped:
			st = "Stopped";
			break;
		default:
			st = "Unknown";
			break;
		}
		Log.v(TAG, "setState(" + st + ")");
	}

	private void updateBuffer(int buffer) {
		mBuffering = buffer;
		for (PlayerListener obj : mPlayerListeners) {
			obj.onBuffering(mBuffering);
		}
	}

	public int getBuffer() {
		return mBuffering;
	}

	public void playMedia(Media media) {
		if (media.getType() == "Song") {
			playSong( (Song) media );
		}
		if (media.getType() == "Video") {
			playVideo( (Video) media );
		}

		Log.e(TAG, "Invalid Media Type: " + media.getType());
	}

	public void playSong(Song song) {
		setState(STATE.Idle);

		String uri = song.liveUrl();
		Log.v(TAG, "Playing uri: " + uri);

		if (mState == STATE.Prepared || mState == STATE.Started
				|| mState == STATE.Paused) {
			mPlayer.stop();
		}

		mPlayAfterPrepared = true;
		mSong = song;
		updateBuffer(-1);

		for (PlayerListener obj : mPlayerListeners) {
			obj.onNewSongPlaying(mSong);
		}

		mPlayer.reset();
		try {
			mPlayer.setDataSource(uri);
			setState(STATE.Initialised);
			mPlayer.prepareAsync();
		} catch (Exception blah) {
			return;
		}
	}
 
	public void playVideo(Video video) {
		setState(STATE.Idle);

		String uri = video.liveUrl();
		Log.v(TAG, "Playing uri: " + uri);

		if (mState == STATE.Prepared || mState == STATE.Started
				|| mState == STATE.Paused) {
			mPlayer.stop();
		}

		mPlayAfterPrepared = true;
		mVideo = video;
		updateBuffer(-1);

		for (PlayerListener obj : mPlayerListeners) {
			obj.onNewVideoPlaying(mVideo);
		}

		mPlayer.reset();
		try {
			mPlayer.setDataSource(uri);
			setState(STATE.Initialised);
			mPlayer.prepareAsync();
		} catch (Exception blah) {
			return;
		}
	}

	public void doPauseResume() {
		if (mState == STATE.Started || mState == STATE.Paused) {
			if (mPlayer.isPlaying()) {
				mPlayer.pause();
				setState(STATE.Paused);
			} else {
				mPlayer.start();
				setState(STATE.Started);
			}
		} else if (mState == STATE.Initialised) {
			mPlayAfterPrepared = !mPlayAfterPrepared;
		} else if (mState == STATE.Prepared) {
			mPlayer.start();
			setState(STATE.Started);
		}
	}

	public void seekTo(int position) {
		if (mState == STATE.Prepared || mState == STATE.Started
				|| mState == STATE.Paused) {
			mPlayer.seekTo(position);
		}
	}

	public boolean isSeekable() {
		return mState == STATE.Prepared || mState == STATE.Started
				|| mState == STATE.Paused;
	}

	public boolean isPlaying() {
		return (mState == STATE.Initialised && mPlayAfterPrepared)
				|| mState == STATE.Started;
	}

	public int getCurrentPosition() {
		if (mState == STATE.Prepared || mState == STATE.Started
				|| mState == STATE.Paused) {
			return mPlayer.getCurrentPosition();
		} else {
			return 0;
		}
	}

	public int getDuration() {
		if (mState == STATE.Initialised || mState == STATE.Prepared
				|| mState == STATE.Started || mState == STATE.Paused) {
			try {
				return Integer.parseInt(mSong.time) * 1000;
			} catch (Exception poo) {
			}
			if (mState != STATE.Initialised) {
				return mPlayer.getDuration();
			}
		}
		return 0;
	}

	public void quit() {
		TelephonyManager tmgr = (TelephonyManager) mContext
				.getSystemService(Context.TELEPHONY_SERVICE);
		tmgr.listen(mPhoneStateListener, 0);
	}

	public void setPlayerListener(PlayerListener StatusChangeObject) {
		mPlayerListeners.add(StatusChangeObject);
	}

	private class MyMediaPlayerListener implements
			MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
			MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnErrorListener {
		public boolean onError(MediaPlayer mp, int what, int extra) {
			Log.e(TAG, "Player error (" + what + "," + extra + ")");
			return false;
		}

		public void onPrepared(MediaPlayer mp) {
			setState(STATE.Prepared);
			if (mPlayAfterPrepared) {
				mPlayer.start();
				setState(STATE.Started);
			}
			mPlayAfterPrepared = true;
		}

		public void onCompletion(MediaPlayer mp) {
			/* TODO
			mSong = null;
			mPlayer.stop();
			setState(STATE.Stopped);

			Log.v(TAG, "Completion");
			Song song = Lullaby.pl.playNext();
			*/
			Song song = null;
			if (song == null) {
				for (PlayerListener obj : mPlayerListeners) {
					obj.onPlayerStopped();
				}
				mPlayer.stop();
				mPlayer.reset();
				setState(STATE.Stopped);
			}
		}

		public void onBufferingUpdate(MediaPlayer mp, int buffer) {
			updateBuffer(buffer);
		}
	}

	// Handle phone calls
	private class MyPhoneStateListener extends PhoneStateListener {
		private Boolean mResumeAfterCall = false;

		@Override
		public void onCallStateChanged(int state, String incomingNumber) {
			if (state == TelephonyManager.CALL_STATE_RINGING) {
				AudioManager audioManager = (AudioManager) mContext
						.getSystemService(Context.AUDIO_SERVICE);
				int ringvolume = audioManager
						.getStreamVolume(AudioManager.STREAM_RING);
				if (ringvolume > 0) {
					mResumeAfterCall = (mPlayer.isPlaying() || mResumeAfterCall);
					mPlayer.pause();
				}
			} else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
				// pause the music while a conversation is in progress
				mResumeAfterCall = (mPlayer.isPlaying() || mResumeAfterCall);
				mPlayer.pause();
			} else if (state == TelephonyManager.CALL_STATE_IDLE) {
				// start playing again
				if (mResumeAfterCall) {
					// resume playback only if music was playing
					// when the call was answered
					mPlayer.start();
					mResumeAfterCall = false;
				}
			}
		}
	};
}

