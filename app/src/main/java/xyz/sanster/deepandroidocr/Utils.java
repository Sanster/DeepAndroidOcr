package xyz.sanster.deepandroidocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;

import static org.opencv.android.Utils.matToBitmap;

public class Utils {
    public static final String TAG = "cwq";
    private static Context appContext;
    static File pictureDir;
    public static String ARC_APP_ID = "93mAjhrwGLMEkNsehor5FeX9Zim8kMhd1odeQob57fwe";
    public static String ARC_FACE_DETECT = "A6U3AYbadfp6G2779RZwuHuZZqfg52dDBPFH3NNqZhnY";
    public static String ARC_FACE_RECO = "A6U3AYbadfp6G2779RZwuHv4DSiP9VeYPpUi2ove6LYd";

    static byte[] rotateNV21(
            final byte[] yuv, final int width, final int height, final int rotation) {
        if (rotation == 0) return yuv;
        if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
            throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
        }

        final byte[] output = new byte[yuv.length];
        final int frameSize = width * height;
        final boolean swap = rotation % 180 != 0;
        final boolean xflip = rotation % 270 != 0;
        final boolean yflip = rotation >= 180;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                final int yIn = j * width + i;
                final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                final int vIn = uIn + 1;

                final int wOut = swap ? height : width;
                final int hOut = swap ? width : height;
                final int iSwapped = swap ? j : i;
                final int jSwapped = swap ? i : j;
                final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                final int yOut = jOut * wOut + iOut;
                final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                final int vOut = uOut + 1;

                output[yOut] = (byte) (0xff & yuv[yIn]);
                output[uOut] = (byte) (0xff & yuv[uIn]);
                output[vOut] = (byte) (0xff & yuv[vIn]);
            }
        }
        return output;
    }

    public static Point getCorrectHeightAndWidth(int width, int height) {
        // 如果 width 和 height，一个为奇数，一个为偶数
        // encodeYUV420SP 会报错。
        // 处理方法为弃掉奇数的最后一行或最后一咧

        Point ret = new Point(width, height);

        if (width % 2 == 0 && height % 2 != 0) {
            ret.y = height - 1;
        }

        if (height % 2 == 0 && width % 2 != 0) {
            ret.x = width - 1;
        }
        return ret;
    }

    public static byte[] getNV21(Bitmap img) {
        int width = img.getWidth();
        int height = img.getHeight();

        Point size = getCorrectHeightAndWidth(width, height);
        width = size.x;
        height = size.y;

//        Log.d(Utils.TAG, "getNV21() width=" + width + " height=" + height);

        int[] argb = new int[width * height];

        try {
            img.getPixels(argb, 0, width, 0, 0, width, height);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(Utils.TAG, e.getMessage());
            return null;
        }

        byte[] yuv = new byte[width * height * 3 / 2];

        try {
            encodeYUV420SP(yuv, argb, width, height);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(Utils.TAG, e.getMessage());
            return null;
        }

        return yuv;
    }

    private static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is
                // every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    public static org.opencv.core.Rect rect2CVRect(Rect rect) {
        org.opencv.core.Rect ret = new org.opencv.core.Rect();
        ret.x = rect.left;
        ret.y = rect.top;
        ret.height = rect.height();
        ret.width = rect.width();
        return ret;
    }

    public static Bitmap cropBitmap(Bitmap img) {
        if (img.getHeight() % 2 > 0 || img.getWidth() % 2 > 0) {
            return Bitmap.createBitmap(img, 0, 0, img.getWidth() / 2 * 2, img.getHeight() / 2 * 2);
        }
        return img;
    }

    public static Rect cvRect2Rect(org.opencv.core.Rect rect) {
        Rect ret = new Rect();
        ret.top = rect.y;
        ret.bottom = rect.y + rect.height;
        ret.left = rect.x;
        ret.right = rect.x + rect.width;
        return ret;
    }

    static void init(Context context) {
        appContext = context;
        pictureDir = appContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
    }

    public static int getScreenWidth(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels;
    }

    public static int getScreenHeight(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.heightPixels;
    }
}
