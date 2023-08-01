package org.easydarwin.easyplayer;

/*
 * Copyright (C) 2015 Zhang Rui <bbcallen@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewConfigurationCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.easydarwin.easyplayer.databinding.ActivityMainProBinding;
import org.easydarwin.easyplayer.util.FileUtil;
import org.easydarwin.easyplayer.util.SPUtil;
import org.easydarwin.easyplayer.views.ProVideoView;
import org.easydarwin.easyplayer.views.VideoControllerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

public class ProVideoActivity extends AppCompatActivity {
    private static final String TAG = "ProVideoActivity";

    public static final int REQUEST_WRITE_STORAGE = 111;

    private String mVideoPath;
    private Uri mVideoUri;

    private ActivityMainProBinding mBinding;
    private ProVideoView mVideoView,mVideoView2,mVideoView3,mVideoView4;        // 播放器View
    private View mProgress;

    private GestureDetector detector;

    private VideoControllerView mediaController,mediaController2,mediaController3,mediaController4;
    private MediaScannerConnection mScanner;

    private Runnable mSpeedCalcTask;

    private int mMode;                      // 画面模式

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
        }

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main_pro);

        // handle arguments
        mVideoPath = getIntent().getStringExtra("videoPath");

        String mSnapPath = getIntent().getStringExtra("snapPath");
        if (TextUtils.isEmpty(mSnapPath)) {
            Glide.with(this).load(mSnapPath).into(mBinding.surfaceCover);
            ViewCompat.setTransitionName(mBinding.surfaceCover, "snapCover");
        }

        Intent intent = getIntent();
        String intentAction = intent.getAction();
        if (!TextUtils.isEmpty(intentAction)) {
            if (intentAction.equals(Intent.ACTION_VIEW)) {
                mVideoPath = intent.getDataString();
            } else if (intentAction.equals(Intent.ACTION_SEND)) {
                mVideoUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                    String scheme = mVideoUri.getScheme();

                    if (TextUtils.isEmpty(scheme)) {
                        Log.e(TAG, "Null unknown scheme\n");
                        finish();
                        return;
                    }

                    if (scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE)) {
                        mVideoPath = mVideoUri.getPath();
                    } else if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                        Log.e(TAG, "Can not resolve content below Android-ICS\n");
                        finish();
                        return;
                    } else {
                        Log.e(TAG, "Unknown scheme " + scheme + "\n");
                        finish();
                        return;
                    }
                }
            }
        }

        SPUtil.setDefaultParams(this);

        if (BuildConfig.DEBUG) {
            IjkMediaPlayer.native_setLogLevel(IjkMediaPlayer.IJK_LOG_DEBUG);// init player
        }

        mediaController = new VideoControllerView(this);
        mediaController.setMediaPlayer(mBinding.videoView);
        mediaController2 = new VideoControllerView(this);
        mediaController2.setMediaPlayer(mBinding.videoView2);
        mediaController3 = new VideoControllerView(this);
        mediaController3.setMediaPlayer(mBinding.videoView3);
        mediaController4 = new VideoControllerView(this);
        mediaController4.setMediaPlayer(mBinding.videoView4);
        mVideoView = mBinding.videoView;
        mVideoView2 = mBinding.videoView2;
        mVideoView3 = mBinding.videoView3;
        mVideoView4 = mBinding.videoView4;
//        mVideoView.setMediaController(mediaController);

        mProgress = findViewById(android.R.id.progress);
        mVideoView .setOnInfoListener((iMediaPlayer, arg1, arg2) -> {
            switch (arg1) {
                case IMediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING:
                    Log.i(TAG, "MEDIA_INFO_VIDEO_TRACK_LAGGING");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    Log.i(TAG, "MEDIA_INFO_VIDEO_RENDERING_START");
                    mProgress.setVisibility(View.GONE);
                    mBinding.surfaceCover.setVisibility(View.GONE);
                    mBinding.playerContainer.setVisibility(View.GONE);
                    mBinding.videoView.setVisibility(View.VISIBLE);
                    mBinding.videoView2.setVisibility(View.VISIBLE);

                    // 快照
                    File file = FileUtil.getSnapshotFile(mVideoPath);
                    mVideoView.takePicture(file.getPath());
                    mVideoView2.takePicture(file.getPath());
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                    Log.i(TAG, "MEDIA_INFO_BUFFERING_START");
                    break;
                case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                    Log.i(TAG, "MEDIA_INFO_BUFFERING_END");
                    break;
                case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                    Log.i(TAG, "MEDIA_INFO_NETWORK_BANDWIDTH");
                    break;
                case IMediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                    Log.i(TAG, "MEDIA_INFO_BAD_INTERLEAVING");
                    break;
                case IMediaPlayer.MEDIA_INFO_NOT_SEEKABLE:
                    Log.i(TAG, "MEDIA_INFO_NOT_SEEKABLE");
                    break;
                case IMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    Log.i(TAG, "MEDIA_INFO_METADATA_UPDATE");
                    break;
                case IMediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE:
                    Log.i(TAG, "MEDIA_INFO_UNSUPPORTED_SUBTITLE");
                    break;
                case IMediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT:
                    Log.i(TAG, "MEDIA_INFO_SUBTITLE_TIMED_OUT");
                    break;
                case IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                    Log.i(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED");
                    break;
                case IMediaPlayer.MEDIA_INFO_AUDIO_RENDERING_START:
                    Log.i(TAG, "MEDIA_INFO_AUDIO_RENDERING_START");
                    break;
            }

            return false;
        });

//        mVideoView.setOnErrorListener((iMediaPlayer, i, i1) -> {
//            Log.i(TAG, "播放错误");
//            mBinding.videoView.setVisibility(View.GONE);
//            mBinding.videoView2.setVisibility(View.GONE);
//            mProgress.setVisibility(View.GONE);
//            mBinding.playerContainer.setVisibility(View.VISIBLE);
//
//            mVideoView.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    mVideoView.reStart();
//                }
//            }, 5000);
//
//            return true;
//        });
//
//        mVideoView.setOnCompletionListener(iMediaPlayer -> {
//            Log.i(TAG, "播放完成");
//            mProgress.setVisibility(View.GONE);
//
//            if (mVideoPath.toLowerCase().startsWith("rtsp") ||
//                    mVideoPath.toLowerCase().startsWith("rtmp") ||
//                    mVideoPath.toLowerCase().startsWith("http")) {
//                mVideoView.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        mVideoView.reStart();
//                    }
//                }, 5000);
//            }
//        });
//
//        mVideoView.setOnPreparedListener(iMediaPlayer -> Log.i(TAG, String.format("onPrepared")));

        if (mVideoPath != null) {
            mVideoView.setVideoPath(mVideoPath);
            mVideoView2.setVideoPath(mVideoPath);
            mVideoView3.setVideoPath(mVideoPath);
            mVideoView4.setVideoPath(mVideoPath);
        } else if (mVideoUri != null) {
            mVideoView.setVideoURI(mVideoUri);
            mVideoView2.setVideoURI(mVideoUri);
            mVideoView3.setVideoURI(mVideoUri);
            mVideoView4.setVideoURI(mVideoUri);
        } else {
            Log.e(TAG, "Null Data Source\n");
            finish();
            return;
        }

        mVideoView.start();

        mVideoView2.start();

        mVideoView3.start();

        mVideoView4.start();

        GestureDetector.SimpleOnGestureListener listener = new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (mVideoView.isInPlaybackState()) {
                    mVideoView.toggleMediaControlsVisibility();
                    mVideoView2.toggleMediaControlsVisibility();
                    return true;
                }

                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                return true;
            }
        };

        detector = new GestureDetector(this, listener);

        mVideoView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);

            return true;
        });

        mSpeedCalcTask = new Runnable() {
            private long mReceivedBytes;

            @Override
            public void run() {
                long l = mVideoView.getReceivedBytes();
                long received = l - mReceivedBytes;
                mReceivedBytes = l;

                TextView view = (TextView) findViewById(R.id.loading_speed);
                view.setText(String.format("%3.01fKB/s", received * 1.0f / 1024));

                if (findViewById(android.R.id.progress).getVisibility() == View.VISIBLE){
                    mVideoView.postDelayed(this,1000);
                }
            }
        };

        mVideoView.post(mSpeedCalcTask);

//        if (BuildConfig.DEBUG) {
//            mBinding.videoView2.setVideoPath("rtmp://13088.liveplay.myqcloud.com/live/13088_65829b3d3e");
//            mBinding.videoView2.setShowing(false);
//            mBinding.videoView2.start();
//        }
    }


    @Override
    protected void onStop() {
        super.onStop();

        mVideoView.stopPlayback();
        mVideoView2.stopPlayback();

        if (BuildConfig.DEBUG) {
            mBinding.videoView2.stopPlayback();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mScanner != null) {
            mScanner.disconnect();
            mScanner = null;
        }
    }

}
