package com.example.idcardidentify;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class IDidentifyActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "IDidentifyActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final int REQUEST_IMAGE_CAPTURE = 1002;

    // UI组件
    private ImageView ivPreview;
    private TextView tvResult;
    private TextView tvStatus;
    private RadioGroup rgCardSide; // 正反面选择RadioGroup

    // 流程数据存储
    private String imagePath; // 拍照后的图片路径
    private String base64Image; // Base64编码后的图片
    private String v3Signature; // V3签名（仅用于展示）
    private String responseJson; // 网络请求返回的JSON数据
    private GenerateV3.Result signResult; // 标准签名结果（核心）
    private String requestBody; // 腾讯云标准请求体
    private String cardSide = "FRONT"; // 默认识别身份证正面

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ididentify);

        // 适配状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 初始化UI组件
        initView();

        // 设置按钮点击事件
        setButtonClickListener();

        // 设置正反面选择监听
        setCardSideListener();
    }

    private void initView() {
        ivPreview = findViewById(R.id.iv_preview);
        tvResult = findViewById(R.id.tv_result);
        tvStatus = findViewById(R.id.tv_status);
        rgCardSide = findViewById(R.id.rg_card_side);

        // 按钮组件
        Button btnCameraCapture = findViewById(R.id.btn_camera_capture);
        Button btnImageToBase64 = findViewById(R.id.btn_image_to_base64);
        Button btnGenerateV3Signature = findViewById(R.id.btn_generate_v3_signature);
        Button btnSendRequest = findViewById(R.id.btn_send_request);
        Button btnParseJson = findViewById(R.id.btn_parse_json);
    }

    private void setCardSideListener() {
        rgCardSide.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_front) {
                cardSide = "FRONT"; // 正面
                updateStatus("已选择：身份证正面");
            } else if (checkedId == R.id.rb_back) {
                cardSide = "BACK"; // 反面
                updateStatus("已选择：身份证反面");
            }
        });
        // 默认选正面
        rgCardSide.check(R.id.rb_front);
    }

    private void setButtonClickListener() {
        findViewById(R.id.btn_camera_capture).setOnClickListener(this);
        findViewById(R.id.btn_image_to_base64).setOnClickListener(this);
        findViewById(R.id.btn_generate_v3_signature).setOnClickListener(this);
        findViewById(R.id.btn_send_request).setOnClickListener(this);
        findViewById(R.id.btn_parse_json).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_camera_capture) {
            // 摄像头拍照
            captureImageFromCamera();
        } else if (id == R.id.btn_image_to_base64) {
            // 图片转Base64
            convertImageToBase64();
        } else if (id == R.id.btn_generate_v3_signature) {
            // 生成V3签名（改用标准GenerateV3）
            generateV3Signature();
        } else if (id == R.id.btn_send_request) {
            // 发送POST请求（改用标准SendRequest）
            sendPostRequest();
        } else if (id == R.id.btn_parse_json) {
            // 解析JSON数据
            parseJsonResponse();
        }
    }

    private void captureImageFromCamera() {
        // 检查相机权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
                return;
            }
        }
        // 启动相机Intent
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "未找到相机应用", Toast.LENGTH_SHORT).show();
            updateStatus("错误：未找到相机应用");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 获取拍照后的位图
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            ivPreview.setImageBitmap(imageBitmap);

            // 保存图片并转换为Base64（修复原代码未实际写入文件的问题）
            imagePath = saveBitmapToFile(imageBitmap);
            // 自动转换为Base64（简化操作）
            base64Image = bitmapToBase64(imageBitmap);
            updateStatus("拍照成功，已自动转换为Base64（当前选择：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
            updateResult("拍照成功，图片Base64（前100字符）：" +
                    (base64Image != null ? base64Image.substring(0, Math.min(100, base64Image.length())) + "..." : "转换失败"));
        } else {
            updateStatus("拍照取消或失败");
            Toast.makeText(this, "拍照取消或失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String saveBitmapToFile(Bitmap bitmap) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes); // 降低压缩质量，减少数据量
            File tempFile = new File(getCacheDir(), "temp_idcard_" + UUID.randomUUID() + ".jpg");
            tempFile.createNewFile();
            // 写入文件
            java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile);
            fos.write(bytes.toByteArray());
            fos.flush();
            fos.close();
            return tempFile.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "保存图片失败", e);
            return null;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes);
        byte[] byteArray = bytes.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private void convertImageToBase64() {
        if (imagePath == null && ivPreview.getDrawable() == null) {
            Toast.makeText(this, "请先拍照获取图片", Toast.LENGTH_SHORT).show();
            updateStatus("错误：未获取到图片");
            return;
        }

        // 如果已有Bitmap直接转换，避免文件读取失败
        if (ivPreview.getDrawable() != null) {
            Bitmap bitmap = ((android.graphics.drawable.BitmapDrawable) ivPreview.getDrawable()).getBitmap();
            base64Image = bitmapToBase64(bitmap);
            updateStatus("图片转Base64成功（从预览图转换）（当前选择：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
            updateResult("Base64编码结果（前100字符）：" + base64Image.substring(0, Math.min(100, base64Image.length())) + "...");
            Toast.makeText(this, "图片转Base64成功", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File file = new File(imagePath);
            InputStream inputStream = new FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            inputStream.read(bytes);
            base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);

            updateStatus("图片转Base64成功（从文件读取）（当前选择：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
            updateResult("Base64编码结果（前100字符）：" + base64Image.substring(0, Math.min(100, base64Image.length())) + "...");
            Toast.makeText(this, "图片转Base64成功", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "图片文件未找到", e);
            updateStatus("错误：图片文件未找到");
            Toast.makeText(this, "图片文件未找到", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "读取图片失败", e);
            updateStatus("错误：读取图片失败");
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void generateV3Signature() {
        if (base64Image == null) {
            Toast.makeText(this, "请先将图片转换为Base64", Toast.LENGTH_SHORT).show();
            updateStatus("错误：未获取到Base64数据");
            return;
        }
        try {
            // 1. 构建腾讯云身份证识别接口的标准请求体
            JSONObject requestBodyJson = new JSONObject();
            requestBodyJson.put("ImageBase64", base64Image);
            // 使用选择的正反面参数
            requestBodyJson.put("CardSide", cardSide);
            requestBody = requestBodyJson.toString();
            // 2. 使用标准GenerateV3生成签名
            GenerateV3 generateV3 = new GenerateV3(); // 使用硬编码的SecretId/SecretKey
            signResult = generateV3.generateSignHeaders(requestBody);

            if (signResult == null) {
                updateStatus("错误：签名生成失败");
                Toast.makeText(this, "签名生成失败", Toast.LENGTH_SHORT).show();
                return;
            }
            // 保存签名用于展示
            v3Signature = signResult.authorization;
            updateStatus("V3签名生成成功（腾讯云标准）（当前选择：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
            updateResult("Authorization（前100字符）：" + v3Signature.substring(0, Math.min(100, v3Signature.length())) + "...");
            Toast.makeText(this, "V3签名生成成功", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Log.e(TAG, "请求体构建失败", e);
            updateStatus("错误：请求体格式错误");
            Toast.makeText(this, "请求体构建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPostRequest() {
        if (signResult == null || requestBody == null) {
            Toast.makeText(this, "请先完成Base64转换和标准签名生成", Toast.LENGTH_SHORT).show();
            updateStatus("错误：缺少标准签名或请求体");
            return;
        }
        updateStatus("正在发送请求...（当前识别：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
        // 使用封装的SendRequest发送请求
        SendRequest sendRequest = new SendRequest();
        sendRequest.send(signResult, requestBody, new SendRequest.Callback() {
            @Override
            public void onSuccess(String response) {
                // 请求成功，主线程更新UI
                runOnUiThread(() -> {
                    responseJson = response;
                    updateStatus("POST请求发送成功（当前识别：" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
                    updateResult("接口返回JSON：" + response);
                    Toast.makeText(IDidentifyActivity.this, "请求发送成功", Toast.LENGTH_SHORT).show();
                });
            }
            @Override
            public void onError(String error) {
                // 请求失败，主线程更新UI
                runOnUiThread(() -> {
                    updateStatus("请求失败：" + error);
                    updateResult("错误详情：" + error);
                    Toast.makeText(IDidentifyActivity.this, "请求失败：" + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void parseJsonResponse() {
        if (responseJson == null) {
            Toast.makeText(this, "请先发送POST请求获取JSON数据", Toast.LENGTH_SHORT).show();
            updateStatus("错误：未获取到JSON响应");
            return;
        }
        // 改用AnalyzeData工具类解析（适配正反面）
        AnalyzeData analyzeData = new AnalyzeData();
        analyzeData.parseIdCardResponse(responseJson, cardSide, new AnalyzeData.ParseCallback() {
            @Override
            public void onParseSuccess(String name, String idCardNo, String gender, String nation,
                                       String birth, String address, String issueAuthority, String validDate) {
                StringBuilder resultBuilder = new StringBuilder();
                if (cardSide.equals("FRONT")) {
                    // 正面解析结果
                    resultBuilder.append("【身份证正面解析结果】\n");
                    resultBuilder.append("姓名：").append(name).append("\n");
                    resultBuilder.append("性别：").append(gender).append("\n");
                    resultBuilder.append("民族：").append(nation).append("\n");
                    resultBuilder.append("出生日期：").append(birth).append("\n");
                    resultBuilder.append("住址：").append(address).append("\n");
                    resultBuilder.append("身份证号：").append(idCardNo).append("\n");
                } else {
                    // 反面解析结果
                    resultBuilder.append("【身份证反面解析结果】\n");
                    resultBuilder.append("签发机关：").append(issueAuthority).append("\n");
                    resultBuilder.append("有效期限：").append(validDate).append("\n");
                }
                updateStatus("JSON解析成功（" + (cardSide.equals("FRONT") ? "正面" : "反面") + "）");
                updateResult(resultBuilder.toString());
                Toast.makeText(IDidentifyActivity.this, "JSON解析成功", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onParseError(String errorMsg) {
                updateStatus("错误：JSON解析失败");
                updateResult("解析失败：" + errorMsg);
                Toast.makeText(IDidentifyActivity.this, "JSON解析失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStatus(String status) {
        tvStatus.setText("状态: " + status);
    }

    private void updateResult(String result) {
        tvResult.setText(result);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureImageFromCamera();
            } else {
                Toast.makeText(this, "相机权限被拒绝，无法拍照", Toast.LENGTH_SHORT).show();
                updateStatus("错误：相机权限被拒绝");
            }
        }
    }
}