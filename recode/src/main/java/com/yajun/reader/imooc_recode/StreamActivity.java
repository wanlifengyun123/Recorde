package com.yajun.reader.imooc_recode;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by yajun on 2017/2/15.
 *
 */

public class StreamActivity extends AppCompatActivity {

    private TextView mTvLog;
    private TextView mTvStart;
    private TextView mTvPlay;

    private StringBuilder sb = new StringBuilder();

    //保证多线程内存同步
    private volatile boolean mIsRecording;
    private ExecutorService mExecutorService;
    private Handler mMainHander;
    private AudioRecord mAudioRecord;
    private long mStartRecordTime,mStopRecordTime;
    private File mAudioFile;

    // BUFFER 不能太大 ，避免oom
    private static final int BUFFER_SIZE = 2042;
    private byte[] mBuffer = new byte[BUFFER_SIZE];
    private FileOutputStream mFileOutputStream;
    private volatile boolean mIsPlaying;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        mTvLog = (TextView) findViewById(R.id.id_log);
        mTvStart = (TextView) findViewById(R.id.id_start);
        mTvPlay = (TextView) findViewById(R.id.id_play);

        //录音JIN不具备线程安全性，所以单线程
        mExecutorService = Executors.newSingleThreadExecutor();
        mMainHander = new Handler(Looper.getMainLooper());

        mTvStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 根据当前状态，改变UI，执行相应逻辑
                if(!mIsRecording){
                    startRecord();
                }else {
                    stopRecord();
                }
            }
        });

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
    }

    private void doPlay(File audioFile) {

        // 配置播放器 MediaPlay
        // 音乐类型，扬声器播放
        int streamType = AudioManager.STREAM_MUSIC;
        // 录音的采样频率，所以播放时保持一致
        int sampleRateInHz = 44100;
        // 单声道输入 in  输出 out
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        // 音质
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        // 流模式
        int mode = AudioTrack.MODE_STREAM;

        // 计算最小 buffer 大小
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz,channelConfig,audioFormat);

        // 构造 AudioTrack
        AudioTrack mAudioTrack = new AudioTrack(streamType,sampleRateInHz,channelConfig,
                audioFormat,
                // 不能小于 AudioTrack 的最低要求，也不能小于我们每次读的大小
                Math.max(minBufferSize,BUFFER_SIZE),mode);
        mAudioTrack.play();
        // 文件读取数据
        FileInputStream mFileInputStream = null;
        try {
            mFileInputStream = new FileInputStream(audioFile.getAbsolutePath());
            // 循环读数据，写到播放器
            int read;
            while ((read = mFileInputStream.read(mBuffer)) > 0){
                int ret = mAudioTrack.write(mBuffer,0,read);
                // 检查while 返回值，处理错误
                switch (ret){
                    case AudioTrack.ERROR_INVALID_OPERATION:
                    case AudioTrack.ERROR_DEAD_OBJECT:
                    case AudioTrack.ERROR_BAD_VALUE:
                        playFail();
                        return;
                    default:
                        break;

                }
            }
            // 异常处理，防止闪退
        } catch (RuntimeException | IOException e) {
            e.printStackTrace();
            // 蒱获异常，避免闪退
            playFail();
        } finally {
            mIsPlaying  = false;
            // 关闭文件输入流
            if(mFileInputStream != null){
                closeQuietly(mFileInputStream);
            }
            //释放播放器
            resetQuietly(mAudioTrack);
        }


    }

    private void resetQuietly(AudioTrack audioTrack) {
        try {
            audioTrack.stop();
            audioTrack.release();
            mMainHander.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(StreamActivity.this,"播放录音结束",Toast.LENGTH_LONG).show();
                    setTextLog("播放录音结束");
                }
            });
        }catch (RuntimeException e){
            e.printStackTrace();
            playFail();
        }
    }

    private void playFail() {
        mAudioFile = null;
        mMainHander.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this,"播放录音失败",Toast.LENGTH_LONG).show();
                setTextLog("播放录音失败,有异常");
            }
        });
    }

    /**
     * 静默关闭输入流
     * @param fileInputStream
     */
    private void closeQuietly(FileInputStream fileInputStream) {
        try {
            fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void startRecord() {
        mTvStart.setText("停止录音");
        setTextLog("开始录音");
        //提交后台任务 ，执行开始录音逻辑
        mIsRecording = true;
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if(!doStart()){
                    recordFail();
                }
            }
        });
    }

    private boolean doStart() {
        try {
            // 创建录音文件
            mAudioFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/imoocAudio/" + System.currentTimeMillis() + ".pcm");
            mAudioFile.getParentFile().mkdirs();
            mAudioFile.createNewFile();
            // 创建文件输出流
            mFileOutputStream = new FileOutputStream(mAudioFile);
            // 创建 AudioRecorder
            // 来源麦克风
            int audioSource = MediaRecorder.AudioSource.MIC;
            // 获取采样率
            int sampleRateInHz = 44100;
            // 单声道输入
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            // AudioRecord 内部内存最小大小
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz,channelConfig,audioFormat);
            mAudioRecord = new AudioRecord(audioSource,sampleRateInHz,
                    channelConfig,audioFormat,
                    Math.min(bufferSizeInBytes,BUFFER_SIZE));
            // 开始录音
            mAudioRecord.startRecording();
            mStartRecordTime = System.currentTimeMillis();
            // 循环读取数据写到输出流中
            while (mIsRecording){
                // 只要在录音状态，一直读取数据
                int read = mAudioRecord.read(mBuffer,0,BUFFER_SIZE);
                if(read > 0){
                    // 读取成功，写入为文件
                    mFileOutputStream.write(mBuffer,0,read);
                }else {
                    // 读取失败，返回false。
                    return false;
                }
            }
            // 退出循环，停止录音，释放资源
            return doStop();
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            // 蒱获异常，避免闪退，返回false 提示用户
            return false;
        } finally {
            if(mAudioRecord != null){
                mAudioRecord.release();
            }
            mIsRecording = false;
        }
    }

    private void stopRecord() {
        mTvStart.setText("开始录音");
        setTextLog("停止录音");
        //提交后台任务 ，执行停止录音逻辑
        mIsRecording = false;
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if(!doStop()){
                    recordFail();
                }
            }
        });
    }

    private boolean doStop() {
        try {
            // 结束录音
            mAudioRecord.stop();
            // 释放AudioRecord
            mAudioRecord.release();
            mAudioRecord = null;
            // 释放资源
            mFileOutputStream.close();
            mStopRecordTime = System.currentTimeMillis();
            final int second = (int) ((mStopRecordTime - mStartRecordTime) / 1000);
            // 只接受大于3秒的录音
            if(second > 3){
                //改变UI ，
                mMainHander.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(StreamActivity.this,"录音成功",Toast.LENGTH_LONG).show();
                        setTextLog("录音成功：" + second + "s;");
                    }
                });
            }else {
                //改变UI ，
                mAudioFile.deleteOnExit();
                mMainHander.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(StreamActivity.this,"录音失败,只接受大于3秒的录音",Toast.LENGTH_LONG).show();
                        setTextLog("录音失败：" + second + "s;");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 录音错误处理
     */
    private void recordFail() {
        // 删除文件
        mAudioFile.deleteOnExit();
        mMainHander.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(StreamActivity.this,"录音失败",Toast.LENGTH_LONG).show();
                mTvStart.setText("开始录音");
                setTextLog("录音失败,有异常");
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
        if(mAudioRecord != null){
            mAudioRecord.release();
            mAudioRecord = null;
        }
    }
}
