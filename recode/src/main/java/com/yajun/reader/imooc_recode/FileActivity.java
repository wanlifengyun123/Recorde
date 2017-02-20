package com.yajun.reader.imooc_recode;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by yajun on 2017/2/15.
 *
 */
public class FileActivity extends AppCompatActivity {

    private TextView mTvLog;
    private TextView mTvPressSay;
    private TextView mTvPlay;

    private StringBuilder sb = new StringBuilder();

    private ExecutorService mExecutorService;
    private MediaRecorder mMediaRecorder;
    private MediaPlayer mMediaPlayer;
    private File mAudioFile;
    private long mStartRecordTime,mStopRecordTime;

    private Handler mMainHander;

    // 主线程和后台播放的多线程同步
    private volatile boolean mIsPlaying;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);

        mTvLog = (TextView) findViewById(R.id.id_log);
        mTvPressSay = (TextView) findViewById(R.id.id_press_say);
        mTvPlay = (TextView) findViewById(R.id.id_play);

        mTvPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查当前状态，防止重复播放
                if(mAudioFile != null && !mIsPlaying){
                    //设置当前播放状态
                    mIsPlaying = true;
                    setTextLog("开始播放录音");
                    mExecutorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            doPlay(mAudioFile);
                        }
                    });
                }

            }
        });

        //录音JIN不具备线程安全性，
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainHander = new Handler(Looper.getMainLooper());

        mTvPressSay.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        startRecord();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        stopRecord();
                        break;
                    default:
                        break;
                }
                //处理touth事件，返回true
                return true;
            }
        });
    }

    /**
     * 实际播放逻辑
     * @param audioFile
     */
    private void doPlay(File audioFile) {

        try {
            // 配置播放器 MediaPlay
            mMediaPlayer = new MediaPlayer();
            // 设置声音文件
            mMediaPlayer.setDataSource(audioFile.getAbsolutePath());
            // 设置监听回掉 结束回掉
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // 释放播放器
                    stopPlay();

                }
            });
            mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    // 提醒用户
                    playFail();
                    //释放播放器
                    stopPlay();
                    //错误已处理, 返回true
                    return true;
                }
            });
            // 配置音量 是否循环
            mMediaPlayer.setVolume(1,1);
            mMediaPlayer.setLooping(false);
            // 准备开始
            mMediaPlayer.prepare();
            mMediaPlayer.start();

            // 异常处理，防止闪退
        }catch (RuntimeException | IOException e){
            e.printStackTrace();
            // 提醒用户
            playFail();
            //释放播放器
            stopPlay();
        }

    }

    /**
     * 提醒用户播放失败
     */
    private void playFail() {
        mMainHander.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"播放异常",Toast.LENGTH_LONG).show();
                setTextLog("播放异常");
            }
        });
    }

    /**
     * 停止播放
     */
    private void stopPlay(){
        //重置播放状态
        mIsPlaying = false;
        // 释放播放器
        if(mMediaPlayer !=null){
            // 重置播放器监听，防止内存泄露
            mMediaPlayer.setOnCompletionListener(null);
            mMediaPlayer.setOnErrorListener(null);

            mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mMainHander.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"播放结束",Toast.LENGTH_LONG).show();
                setTextLog("播放结束");
            }
        });

    }

    /**
     * 开始录音
     */
    private void startRecord() {
        //改变UI状态
        mTvPressSay.setText("开始说话...");
        setTextLog("开始说话...");

        //提交后台任务 ，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //释放之前的Record
                releaseRecord();
                //执行录音逻辑，如果录音失败提示用户
                if(!doStart()){
                    recordFail();
                }
            }
        });
    }

    //启动录音逻辑
    private boolean doStart() {

        try {
            //创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/imoocAudio/" + System.currentTimeMillis() + ".m4a");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();

            // 配置MediaRecorder
            mMediaRecorder = new MediaRecorder();
            // 来源麦克风
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            // 保存文件格式 MP4
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            // 获取采样率
            mMediaRecorder.setAudioSamplingRate(44100);
            // 通用 AAC 编码格式
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            // 音质比较好的频率
            mMediaRecorder.setAudioEncodingBitRate(96000);
            // 设置录音位置
            mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());

            //开始录音
            mMediaRecorder.prepare();
            mMediaRecorder.start();

            //记录开始时间
            mStartRecordTime = System.currentTimeMillis();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            //获取异常 ，返回false 提示用户
            return false;
        }
        return true;
    }


    /**
     * 结束录音
     */
    private void stopRecord() {
        //改变UI状态
        mTvPressSay.setText("按住说话");
        setTextLog("结束说话...");

        //提交后台任务 ，执行录音逻辑
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                //执行录音逻辑，如果录音失败提示用户
                if(!doStop()){
                    recordFail();
                }
                //释放之前的Record
                releaseRecord();
            }
        });

    }

    /**
     * 停止录音逻辑
     * @return
     */
    private boolean doStop() {
        try {
            // 停止录音
            mMediaRecorder.stop();
            // 记录结束时间
            mStopRecordTime = System.currentTimeMillis();
            // 只接受大于3秒的录音
            final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            if(second > 3){
                //改变UI ，
                mMainHander.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FileActivity.this,"录音成功",Toast.LENGTH_LONG).show();
                        setTextLog("录音成功：" + second + "s;");
                    }
                });
            }else {
                //改变UI ，
                mMainHander.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(FileActivity.this,"录音失败,只接受大于3秒的录音",Toast.LENGTH_LONG).show();
                        setTextLog("录音失败：" + second + "s;");
                    }
                });
            }

        }catch (RuntimeException e){
            e.printStackTrace();
            //获取异常 ，返回false 提示用户
            return false;
        }
        // 停止成功
        return true;
    }

    /**
     * 释放录音Record
     */
    private void releaseRecord() {
        if(mMediaRecorder != null){
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
    }

    /**
     * 录音错误处理
     */
    private void recordFail() {
        mAudioFile = null;
        mMainHander.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(FileActivity.this,"录音失败",Toast.LENGTH_LONG).show();
                setTextLog("录音失败");
            }
        });


    }

    public void setTextLog(String string){
        sb.append(System.currentTimeMillis() + ":" + string);
        sb.append("\n");
        mTvLog.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //结束线程，避免线程泄露
        mExecutorService.shutdownNow();
        releaseRecord();
        stopPlay();
    }
}
