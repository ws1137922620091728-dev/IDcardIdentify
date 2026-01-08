package com.example.idcardidentify;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class ConvertBase64 {

    private static final String TAG = "ConvertBase64";

    public static String convertImageToBase64(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) {
            Log.e(TAG, "图片路径为空");
            return null;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            Log.e(TAG, "图片文件不存在: " + imagePath);
            return null;
        }

        try {
            // 方法1：直接读取文件字节流（适用于较小的图片）
            return convertFileToBase64(imageFile);
        } catch (Exception e) {
            Log.e(TAG, "直接转换失败: " + e.getMessage());

            try {
                // 方法2：通过Bitmap压缩后转换（适用于大图片）
                return convertBitmapToBase64(imageFile);
            } catch (Exception e2) {
                Log.e(TAG, "Bitmap转换失败: " + e2.getMessage());
                return null;
            }
        }
    }

    private static String convertFileToBase64(File imageFile) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(imageFile);
        byte[] bytes = new byte[(int) imageFile.length()];
        fileInputStream.read(bytes);
        fileInputStream.close();

        // 转换为Base64字符串
        String base64String = Base64.encodeToString(bytes, Base64.DEFAULT);

        // 去除换行符
        base64String = base64String.replace("\n", "");

        Log.d(TAG, "图片Base64转换成功，长度: " + base64String.length());
        return base64String;
    }

    private static String convertBitmapToBase64(File imageFile) {
        // 获取图片尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);

        // 计算合适的采样率
        int reqWidth = 1024;  // 最大宽度
        int reqHeight = 1024; // 最大高度
        int inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // 使用采样率加载图片
        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;

        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), options);
        if (bitmap == null) {
            Log.e(TAG, "无法加载图片为Bitmap");
            return null;
        }

        // 将Bitmap转换为字节数组
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int quality = 85;

        String fileName = imageFile.getName().toLowerCase();
        Bitmap.CompressFormat compressFormat = Bitmap.CompressFormat.JPEG;

        if (fileName.endsWith(".png")) {
            compressFormat = Bitmap.CompressFormat.PNG;
        } else if (fileName.endsWith(".webp")) {
            compressFormat = Bitmap.CompressFormat.WEBP;
        }

        bitmap.compress(compressFormat, quality, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();

        // 转换为Base64
        String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);
        base64String = base64String.replace("\n", ""); // 去除换行符

        // 回收Bitmap内存
        bitmap.recycle();

        Log.d(TAG, "图片Base64转换成功，采样率: " + inSampleSize +
                ", 质量: " + quality + "%, 长度: " + base64String.length());

        return base64String;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // 原始图片尺寸
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // 计算最大的采样率，保证图片尺寸大于要求尺寸
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static Bitmap convertBase64ToBitmap(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }

        try {
            // 解码Base64字符串
            byte[] decodedBytes = Base64.decode(base64String, Base64.DEFAULT);

            // 将字节数组转换为Bitmap
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "Base64转Bitmap失败: " + e.getMessage());
            return null;
        }
    }

    public static String getBase64Summary(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return "Base64字符串为空";
        }

        int length = base64String.length();
        String prefix = "";

        if (length > 100) {
            prefix = base64String.substring(0, 100) + "...";
        } else {
            prefix = base64String;
        }

        return String.format("Base64字符串:\n" +
                        "长度: %d 字符\n" +
                        "前100字符:\n%s\n\n" +
                        "图片大小估算: %.2f KB",
                length, prefix, (length * 0.75) / 1024);
    }

    public static boolean isValidBase64(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return false;
        }

        try {
            // 尝试解码
            byte[] decoded = Base64.decode(base64String, Base64.DEFAULT);

            // 简单验证：解码后的数据不应为空
            return decoded != null && decoded.length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static String cleanBase64String(String base64String) {
        if (base64String == null) {
            return "";
        }

        // 移除Base64字符串中可能的换行符、空格等
        String cleaned = base64String
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "");

        return cleaned;
    }
}