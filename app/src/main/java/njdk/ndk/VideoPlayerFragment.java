package njdk.ndk;

import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import org.opencv.android.OpenCVLoader;
import org.opencv.objdetect.CascadeClassifier;
import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.Util;
import org.videolan.libvlc.WeakHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VideoPlayerFragment extends Fragment implements IVideoPlayer {
    public final static String TAG = "VideoPlayerFragment";

    private SurfaceHolder surfaceHolder = null;
    private LibVLC mLibVLC = null;

    private int mVideoHeight;
    private int mVideoWidth;
    private int mSarDen;
    private int mSarNum;
    private int mUiVisibility = -1;
    private static final int SURFACE_SIZE = 3;

    private SurfaceView surfaceView = null;
    private FaceRtspUtil mFaceUtil;
    //截图后的图片的宽度
    private static final int PIC_WIDTH = 1280;
    //截图后的图片的高度
    private static final int PIC_HEIGHT = 720;
    private String mPicCachePath;
    private Timer mTimer;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        //存放VLC的截屏图片的文件夹路径
        View view = inflater.inflate(R.layout.video_player, null);
        init(view);
        if (Util.isICSOrLater())
            getActivity().getWindow().getDecorView().findViewById(android.R.id.content)
                    .setOnSystemUiVisibilityChangeListener(
                            new OnSystemUiVisibilityChangeListener() {

                                @Override
                                public void onSystemUiVisibilityChange(
                                        int visibility) {
                                    if (visibility == mUiVisibility)
                                        return;
                                    setSurfaceSize(mVideoWidth, mVideoHeight,
                                            mSarNum, mSarDen);
                                    if (visibility == View.SYSTEM_UI_FLAG_VISIBLE) {
                                        Log.d(TAG, "onSystemUiVisibilityChange");
                                    }
                                    mUiVisibility = visibility;
                                }
                            });

        try {
            mLibVLC = LibVLC.getInstance();
            if (mLibVLC != null) {
                EventHandler em = EventHandler.getInstance();
                em.addHandler(eventHandler);
            }
        } catch (LibVlcException e) {
            e.printStackTrace();
            Log.i(TAG, "onCreateView: " + e.getMessage());
        }
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mLibVLC.isPlaying()) {
            mLibVLC.play();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mLibVLC.stop();
        mTimer.cancel();
    }

    private CascadeClassifier initializeOpenCVDependencies() {
        CascadeClassifier classifier = null;
        try {
            InputStream is = getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = getActivity().getDir("cascade", Context.MODE_PRIVATE);
            File mCascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream fos = new FileOutputStream(mCascadeFile);

            byte[] bytes = new byte[4096];
            int len;
            while ((len = is.read(bytes)) != -1) {
                fos.write(bytes, 0, len);
            }
            is.close();
            fos.close();
            classifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error loading cascade", e);
        }
        return classifier;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV init error");
        }
        CascadeClassifier classifier = initializeOpenCVDependencies();
        mFaceUtil = new FaceRtspUtil(classifier, PIC_WIDTH, PIC_HEIGHT);

        mTimer = new Timer();
        //开启一个定时器，每隔一秒截屏检测一次
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                //VLC中将视频帧截取出来，并保存在SD卡中
                String picPath = snapShot();
//
//                //将图片转化为Bitmap对象后
//                Bitmap oldBitmap = getFramePicture(picPath);

                Bitmap oldBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.timg);
                //对保存在本地的图片进行人脸检测，并获取截到的所有人脸
                final List<Bitmap> faces = mFaceUtil.detectFrame(oldBitmap);
                if (faces == null || faces.isEmpty()) {
                    return;
                }
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //将人脸传送到MainActivity中去
                        callBack.pushData(faces);
                    }
                });
            }
        }, 1000, 1000);
    }

    /**
     * 初始化组件
     */
    private void init(View view) {
        surfaceView = (SurfaceView) view.findViewById(R.id.main_surface);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.setFormat(PixelFormat.RGBX_8888);
        surfaceHolder.addCallback(mSurfaceCallback);

        mPicCachePath = getSDPath() + "/FaceTest/";

        File file = new File(mPicCachePath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 截图
     */
    private String snapShot() {
        try {
            String name = mPicCachePath + System.currentTimeMillis() + ".jpg";
            //调用LibVlc的截屏功能，传入一个路径，及图片的宽高
            if (mLibVLC.takeSnapShot(name, PIC_WIDTH, PIC_HEIGHT)) {
                Log.i(TAG, "snapShot: 保存成功--" + System.currentTimeMillis());
                return name;
            }
            Log.i(TAG, "snapShot: 保存失败");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 传入文件路径，获取bitmap
     *
     * @param path 路径
     */
    private Bitmap getFramePicture(String path) {
        if (TextUtils.isEmpty(path) || mFaceUtil == null) {
            Log.i(TAG, "faceDetect: 文件路径为空|| mFaceUtil == null");
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        return file2Bitmap(file);
    }

    private RtspCallBack callBack;

    public void setRtspCallBack(RtspCallBack callBack) {
        this.callBack = callBack;
    }

    public interface RtspCallBack {
        void pushData(List<Bitmap> faces);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setSurfaceSize(mVideoWidth, mVideoHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
    }

    /**
     * attach and disattach surface to the lib
     */
    private final Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            if (format == PixelFormat.RGBX_8888)
                Log.d(TAG, "Pixel format is RGBX_8888");
            else if (format == PixelFormat.RGB_565)
                Log.d(TAG, "Pixel format is RGB_565");
            else if (format == ImageFormat.YV12)
                Log.d(TAG, "Pixel format is YV12");
            else
                Log.d(TAG, "Pixel format is other/unknown");
            mLibVLC.attachSurface(holder.getSurface(),
                    VideoPlayerFragment.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mLibVLC.detachSurface();
        }
    };

    public final Handler mHandler = new VideoPlayerHandler(this);

    private static class VideoPlayerHandler extends
            WeakHandler<VideoPlayerFragment> {
        public VideoPlayerHandler(VideoPlayerFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerFragment activity = getOwner();
            if (activity == null) // WeakReference could be GC'ed early
                return;

            switch (msg.what) {
                case SURFACE_SIZE:
                    activity.changeSurfaceSize();
                    break;
            }
        }
    }

    private void changeSurfaceSize() {
        // get screen size
        int dw = getActivity().getWindow().getDecorView().getWidth();
        int dh = getActivity().getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (dw > dh && isPortrait || dw < dh && !isPortrait) {
            int d = dw;
            dw = dh;
            dh = d;
        }
        if (dw * dh == 0)
            return;
        // compute the aspect ratio
        double ar, vw;
        double density = (double) mSarNum / (double) mSarDen;
        if (density == 1.0) {
            /* No indication about the density, assuming 1:1 */
            ar = (double) mVideoWidth / (double) mVideoHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoWidth * density;
            ar = vw / mVideoHeight;
        }

        // compute the display aspect ratio
        double dar = (double) dw / (double) dh;
        if (dar < ar)
            dh = (int) (dw / ar);
        else
            dw = (int) (dh * ar);

        surfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        LayoutParams lp = surfaceView.getLayoutParams();
        lp.width = dw;
        lp.height = dh;
        surfaceView.setLayoutParams(lp);
        surfaceView.invalidate();
    }

    private final Handler eventHandler = new VideoPlayerEventHandler(this);

    private static class VideoPlayerEventHandler extends
            WeakHandler<VideoPlayerFragment> {
        public VideoPlayerEventHandler(VideoPlayerFragment owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerFragment activity = getOwner();
            if (activity == null)
                return;
            Log.d(TAG, "Event = " + msg.getData().getInt("event"));
            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    break;
                case EventHandler.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    break;
                case EventHandler.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    break;
                case EventHandler.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    activity.getActivity().finish();
                    break;
                case EventHandler.MediaPlayerVout:
                    activity.getActivity().finish();
                    break;
                default:
                    Log.d(TAG, "Event not handled");
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mLibVLC != null) {
            mLibVLC.stop();
        }
        EventHandler em = EventHandler.getInstance();
        em.removeHandler(eventHandler);
        super.onDestroy();
    }

    public void setSurfaceSize(int width, int height, int sar_num, int sar_den) {
        if (width * height == 0)
            return;

        mVideoHeight = height;
        mVideoWidth = width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width,
                               int visible_height, int sar_num, int sar_den) {
        mVideoHeight = height;
        mVideoWidth = width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    private String getSDPath() {
        boolean hasSDCard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
        if (hasSDCard) {
            return Environment.getExternalStorageDirectory().toString();
        } else
            return Environment.getDownloadCacheDirectory().toString();
    }

    private Bitmap file2Bitmap(File file) {
        if (file == null) {
            return null;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            return BitmapFactory.decodeStream(fis);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
