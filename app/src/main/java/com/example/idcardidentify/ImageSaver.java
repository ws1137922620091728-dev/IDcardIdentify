package com.example.idcardidentify;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageSaver {
    public static String saveBitmap(Context context, Bitmap bitmap) {
        // 创建图片文件
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = null;
        try {
            imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            // 压缩质量设为100
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush(); // 强制刷新输出流，确保完整保存
            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}