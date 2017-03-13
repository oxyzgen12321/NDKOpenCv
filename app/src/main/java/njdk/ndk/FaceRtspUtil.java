package njdk.ndk;

import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.ArrayList;
import java.util.List;


/**
 * 人脸检测工具
 */
public class FaceRtspUtil {

    private static final String TAG = "FaceUtil";
    private Mat grayscaleImage;
    private CascadeClassifier cascadeClassifier = null;

    public FaceRtspUtil(CascadeClassifier cascadeClassifier, int width, int height) {
        this.cascadeClassifier = cascadeClassifier;
        //人脸的宽高最小也要是原图的height的 10%
        grayscaleImage = new Mat(height, width, CvType.CV_8UC4);
    }

    /**
     * 给一个图片，检测这张图片里是否有人脸
     *
     * @param oldBitmap 图片
     * @return 返回一个List集合，里面存放所有检测到的人脸
     */
    public List<Bitmap> detectFrame(Bitmap oldBitmap) {

        Mat aInputFrame = new Mat();

        if (oldBitmap == null) {
            return null;
        }
        Utils.bitmapToMat(oldBitmap, aInputFrame);
        if (grayscaleImage == null) {
            Log.i(TAG, "detectFrame: aInputFrame == null || grayscaleImage == null");
            return null;
        }
        Imgproc.cvtColor(aInputFrame, grayscaleImage, Imgproc.COLOR_RGBA2RGB);

        MatOfRect faces = new MatOfRect();

        // 使用级联分类器 检测人脸
        if (cascadeClassifier != null) {
            //不获取60*60以下的人脸
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(60, 60), new Size());
        }
        //facesArray里保存所有检测到的人脸的位置及大小
        Rect[] facesArray = faces.toArray();
        if (facesArray == null || facesArray.length == 0) {
            //如果没有人脸，直接退出
            Log.i(TAG, "detectFrame: 该图片中没有人脸");
            return null;
        }
        //保存该帧中的所有人脸
        List<Bitmap> bitmaps = new ArrayList<>();

        Bitmap tmpBitmap = Bitmap.createBitmap(aInputFrame.width(), aInputFrame.height(), Bitmap.Config.RGB_565);
        Utils.matToBitmap(aInputFrame, tmpBitmap);

        for (Rect aFacesArray : facesArray) {
            Bitmap bitmap = Bitmap.createBitmap(tmpBitmap, aFacesArray.x, aFacesArray.y,
                    aFacesArray.width, aFacesArray.height);
            bitmaps.add(bitmap);
        }
        //回收帧图片
        tmpBitmap.recycle();
        return bitmaps;
    }


}
