package com.example.idcardidentify;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TakePhoto {
    public static final int REQUEST_CAMERA = 1001;
    private static final int REQUEST_CAMERA_PERMISSION = 1002;
    private static final int REQUEST_STORAGE_PERMISSION = 1003;

    private static String currentPhotoPath;

    // 回调接口，用于返回图片路径
    public interface TakePhotoCallback {
        void onPhotoTaken(String imagePath);
        void onError(String error);
        void onPermissionDenied(); //权限被拒绝的回调
    }

    public static void takePhoto(Activity activity, TakePhotoCallback callback) {
        // 检查相机权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请相机权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
            // 这里通过回调通知Activity权限被拒绝
            if (callback != null) {
                callback.onPermissionDenied();
            }
            return;
        }

        // 检查存储权限（Android 10以下需要）
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
                return;
            }
        }

        // 所有权限都已授予，打开相机
        dispatchTakePictureIntent(activity, callback);
    }

    private static void dispatchTakePictureIntent(Activity activity, TakePhotoCallback callback) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 确保有相机应用
        if (takePictureIntent.resolveActivity(activity.getPackageManager()) != null) {
            // 创建图片文件
            File photoFile = null;
            try {
                photoFile = createImageFile(activity);
            } catch (IOException ex) {
                if (callback != null) {
                    callback.onError("创建图片文件失败: " + ex.getMessage());
                }
                return;
            }
            // 继续执行
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(activity,
                        activity.getPackageName() + ".provider",
                        photoFile);
                currentPhotoPath = photoFile.getAbsolutePath();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                // 设置拍照最大分辨率（优先使用设备支持的最高分辨率）
                takePictureIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, Long.MAX_VALUE);
                takePictureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                takePictureIntent.putExtra("android.intent.extras.CAMERA_FACING", 1);
                takePictureIntent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.name());
                takePictureIntent.putExtra("quality", 100);
                // 授予URI权限
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                activity.startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            }
        } else {
            if (callback != null) {
                callback.onError("没有找到相机应用");
            }
        }
    }

    private static File createImageFile(Activity activity) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        } else {
            storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }

        // 创建目录
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs();
        }

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
        );

        return image;
    }

    public static void handleActivityResult(Activity activity, int requestCode, int resultCode, Intent data, TakePhotoCallback callback) {
        if (requestCode == REQUEST_CAMERA && resultCode == Activity.RESULT_OK) {
            if (currentPhotoPath != null) {
                File photoFile = new File(currentPhotoPath);
                if (photoFile.exists()) {
                    // 文件已由相机应用保存，直接返回原图路径（核心：不使用缩略图）
                    if (callback != null) {
                        callback.onPhotoTaken(currentPhotoPath);
                    }
                } else {
                    // 降级处理：仅当原图不存在时才用缩略图（并提示质量问题）
                    if (data != null && data.getExtras() != null) {
                        Bitmap imageBitmap = (Bitmap) data.getExtras().get("data");
                        if (imageBitmap != null) {
                            String imagePath = ImageSaver.saveBitmap(activity, imageBitmap);
                            if (imagePath != null) {
                                if (callback != null) {
                                    callback.onError("警告：仅获取到缩略图，识别可能不准确");
                                    callback.onPhotoTaken(imagePath);
                                }
                            } else {
                                if (callback != null) {
                                    callback.onError("保存图片失败");
                                }
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("未找到图片数据");
                        }
                    }
                }
            } else {
                if (callback != null) {
                    callback.onError("图片路径为空");
                }
            }
        }
    }

    public static void handlePermissionResult(Activity activity, int requestCode, String[] permissions, int[] grantResults, TakePhotoCallback callback) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 相机权限已授予，再次尝试拍照
                takePhoto(activity, callback);
            } else {
                if (callback != null) {
                    callback.onError("相机权限被拒绝");
                }
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 存储权限已授予，再次尝试拍照
                takePhoto(activity, callback);
            } else {
                if (callback != null) {
                    callback.onError("存储权限被拒绝");
                }
            }
        }
    }
}