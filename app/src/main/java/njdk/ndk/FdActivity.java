package njdk.ndk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.Util;

import java.util.List;


public class FdActivity extends Activity {

    private static final String TAG = "MainActivity";
    private VideoPlayerFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main1);
        final LinearLayout ll_faces = (LinearLayout) findViewById(R.id.ll_faces);
        fragment = new VideoPlayerFragment();
        fragment.setRtspCallBack(new VideoPlayerFragment.RtspCallBack() {
            @Override
            public void pushData(final List<Bitmap> faces) {
                //清除所有的子View
                ll_faces.removeAllViews();
                for (int i = 0; i < faces.size(); i++) {
                    ImageView image = new ImageView(FdActivity.this);
                    image.setImageBitmap(faces.get(i));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                    ll_faces.addView(image, params);
                }
            }
        });
        try {
            EventHandler em = EventHandler.getInstance();
            em.addHandler(handler);
            LibVLC mLibVLC = Util.getLibVlcInstance();
            if (mLibVLC != null) {
                mLibVLC.setSubtitlesEncoding("");
                mLibVLC.setTimeStretching(false);
                mLibVLC.setFrameSkip(true);
                mLibVLC.setChroma("RV32");
                mLibVLC.setVerboseMode(true);
                mLibVLC.setAout(-1);
                mLibVLC.setDeblocking(4);
                mLibVLC.setNetworkCaching(1500);
                //测试地址
//                String pathUri = "rtsp://218.204.223.237:554/live/1/66251FC11353191F/e7ooqwcfbqjoo80j.sdp";
//                String pathUri = "rtsp://184.72.239.149/vod/mp4://BigBuckBunny_175k.mov";
                String pathUri = "rtmp://live.hkstv.hk.lxdns.com/live/hks";
                mLibVLC.playMyMRL(pathUri);
            }
        } catch (LibVlcException e) {
        e.printStackTrace();
    }
    }

    Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            Log.d(TAG, "Event = " + msg.getData().getInt("event"));
            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaPlayerPlaying:
                case EventHandler.MediaPlayerPaused:
                    break;
                case EventHandler.MediaPlayerStopped:
                    break;
                case EventHandler.MediaPlayerEndReached:
                    break;
                case EventHandler.MediaPlayerVout:
                    if (msg.getData().getInt("data") > 0) {
                        FragmentTransaction transaction = getFragmentManager().beginTransaction();
                        transaction.add(R.id.frame_layout, fragment);
                        transaction.commit();
                    }
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    AlertDialog dialog = new AlertDialog.Builder(FdActivity.this)
                            .setTitle("提示信息")
                            .setMessage("无法连接到网络摄像头，请确保手机已经连接到摄像头所在的wifi热点")
                            .setNegativeButton("知道了", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    finish();
                                }
                            }).create();
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.show();
                    break;
                default:
                    Log.d(TAG, "Event not handled ");
                    break;
            }
        }
    };
}
