package com.example.idcardidentify;

import android.util.Log;

import androidx.annotation.NonNull;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;

public class SendRequest {
    private static final String TAG = "SendRequest";
    private static final String BASE_URL = "https://ocr.tencentcloudapi.com/"; //身份证识别接口域名
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int DEFAULT_TIMEOUT = 30; //默认超时时间
    private static final int MAX_RETRY_COUNT = 1; //最大重试次数

    //OkHttpClient
    private OkHttpClient getOkHttpClient() {
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build();
        List<ConnectionSpec> connectionSpecs = new ArrayList<>();
        connectionSpecs.add(spec);
        connectionSpecs.add(ConnectionSpec.CLEARTEXT);

        return new OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .connectionSpecs(connectionSpecs)
                .build();
    }

    public void send(GenerateV3.Result signResult, String requestBody, Callback callback) {
        if (signResult == null || requestBody == null || callback == null) {
            callback.onError("参数异常：签名结果、请求体或回调不能为空");
            return;
        }

        // 构建请求头
        Request request = buildRequest(signResult, requestBody);
        // 发起请求
        sendRequestWithRetry(request, callback, 0);
    }

    private Request buildRequest(GenerateV3.Result signResult, String requestBody) {
        RequestBody body = RequestBody.create(JSON, requestBody);
        return new Request.Builder()
                .url(BASE_URL)
                //签名相关请求头从GenerateV3.Result
                .addHeader("Authorization", signResult.authorization)
                .addHeader("X-TC-Timestamp", signResult.timestamp)
                .addHeader("X-TC-Region", signResult.region)
                .addHeader("X-TC-Action", signResult.action)
                .addHeader("X-TC-Version", signResult.version)
                .addHeader("Content-Type", signResult.contentType)
                .addHeader("Host", "ocr.tencentcloudapi.com")
                .post(body)
                .build();
    }

    private void sendRequestWithRetry(Request request, Callback callback, int retryCount) {
        getOkHttpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                //连接异常、超时等情况，触发重试
                if (retryCount < MAX_RETRY_COUNT) {
                    Log.w(TAG, "请求失败，正在重试（第 " + (retryCount + 1) + " 次）：" + e.getMessage());
                    sendRequestWithRetry(request, callback, retryCount + 1);
                } else {
                    callback.onError("请求失败：" + e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    //请求成功，返回响应体字符串
                    String responseBody = response.body() != null ? response.body().string() : "";
                    callback.onSuccess(responseBody);
                } else {
                    //HTTP状态码非 2xx，解析错误信息
                    String errorMsg = "请求异常（状态码：" + response.code() + "）：" +
                            (response.body() != null ? response.body().string() : "无详细信息");
                    callback.onError(errorMsg);
                }
                // 关闭响应体
                if (response.body() != null) {
                    response.body().close();
                }
            }
        });
    }

    public interface Callback {
        /**
         * 请求成功回调
         * @param response 服务器返回的 JSON 响应字符串
         */
        void onSuccess(String response);

        /**
         * 请求失败回调
         * @param error 错误描述（网络异常、签名失效、接口错误等）
         */
        void onError(String error);
    }

    public OkHttpClient getOkHttpClientWithTimeout(int timeout) {
        int validTimeout = Math.max(timeout, 5);
        // 适配TLS 1.2
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .build();
        List<ConnectionSpec> connectionSpecs = new ArrayList<>();
        connectionSpecs.add(spec);
        connectionSpecs.add(ConnectionSpec.CLEARTEXT);

        return new OkHttpClient.Builder()
                .connectTimeout(validTimeout, TimeUnit.SECONDS)
                .readTimeout(validTimeout, TimeUnit.SECONDS)
                .writeTimeout(validTimeout, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .connectionSpecs(connectionSpecs)
                .build();
    }

    public void setMaxRetryCount(int maxRetryCount) {
        // 如需动态配置，可将 MAX_RETRY_COUNT 改为成员变量
    }
}