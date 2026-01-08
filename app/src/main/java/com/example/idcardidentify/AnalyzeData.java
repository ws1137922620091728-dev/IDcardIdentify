package com.example.idcardidentify;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class AnalyzeData {
    private static final String TAG = "AnalyzeData";
    //主线程Handler
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void parseIdCardResponse(String jsonResponse, String cardSide, ParseCallback callback) {
        // 空值校验
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            mainHandler.post(() -> callback.onParseError("解析失败：响应数据为空"));
            return;
        }
        // 子线程解析JSON
        new Thread(() -> {
            try {
                //解析顶层JSON结构
                JSONObject rootObj = new JSONObject(jsonResponse);
                //检查是否有错误码
                if (rootObj.has("Response") && !rootObj.isNull("Response")) {
                    JSONObject responseObj = rootObj.getJSONObject("Response");
                    //处理接口返回错误
                    if (responseObj.has("Error") && !responseObj.isNull("Error")) {
                        JSONObject errorObj = responseObj.getJSONObject("Error");
                        String code = errorObj.optString("Code", "未知错误码");
                        String message = errorObj.optString("Message", "未知错误信息");
                        mainHandler.post(() -> callback.onParseError(
                                String.format("接口调用失败：%s - %s", code, message)
                        ));
                        return;
                    }
                    //区分正反面解析字段
                    if ("FRONT".equals(cardSide)) {
                        // 解析正面字段
                        String name = responseObj.optString("Name", "");          // 姓名
                        String idCardNo = responseObj.optString("IdNum", "");     // 身份证号
                        String gender = responseObj.optString("Sex", "");         // 性别
                        String nation = responseObj.optString("Nation", "");      // 民族
                        String birth = responseObj.optString("Birth", "");        // 出生日期
                        String address = responseObj.optString("Address", "");    // 地址
                        //校验核心字段
                        if (name.isEmpty() && idCardNo.isEmpty()) {
                            mainHandler.post(() -> callback.onParseError("解析失败：未识别到身份证正面核心信息"));
                            return;
                        }
                        //回调正面结果
                        mainHandler.post(() -> callback.onParseSuccess(
                                name, idCardNo, gender, nation, birth, address, "", ""
                        ));
                    } else if ("BACK".equals(cardSide)) {
                        //解析反面字段
                        String issueAuthority = responseObj.optString("Authority", ""); //签发机关
                        String validDate = responseObj.optString("ValidDate", "");     //有效期限
                        //校验核心字段
                        if (issueAuthority.isEmpty() && validDate.isEmpty()) {
                            mainHandler.post(() -> callback.onParseError("解析失败：未识别到身份证反面核心信息"));
                            return;
                        }
                        //回调反面结果
                        mainHandler.post(() -> callback.onParseSuccess(
                                "", "", "", "", "", "", issueAuthority, validDate
                        ));
                    }
                } else {
                    //若无Response字段，JSON 格式异常
                    mainHandler.post(() -> callback.onParseError("解析失败：响应格式异常（缺失 Response 字段）"));
                }
            } catch (JSONException e) {
                //JSON格式错误
                Log.e(TAG, "JSON 解析异常：", e);
                mainHandler.post(() -> callback.onParseError(
                        String.format("解析失败：JSON 格式错误 - %s", e.getMessage())
                ));
            }
        }).start();
    }

    public interface ParseCallback {
        /**
         * 解析成功回调
         * @param name 姓名（正面）
         * @param idCardNo 身份证号（正面）
         * @param gender 性别（正面）
         * @param nation 民族（正面）
         * @param birth 出生日期（正面）
         * @param address 地址（正面）
         * @param issueAuthority 签发机关（反面）
         * @param validDate 有效期限（反面）
         */
        void onParseSuccess(
                String name, String idCardNo, String gender, String nation,
                String birth, String address, String issueAuthority, String validDate
        );

        /**
         * 解析失败回调
         * @param errorMsg 失败原因（友好提示）
         */
        void onParseError(String errorMsg);
    }

    public void parseIdCardResponse(String jsonResponse, ParseCallback callback) {
        parseIdCardResponse(jsonResponse, "FRONT", callback);
    }

    public void parseSimpleIdCardInfo(String jsonResponse, String cardSide, SimpleParseCallback callback) {
        parseIdCardResponse(jsonResponse, cardSide, new ParseCallback() {
            @Override
            public void onParseSuccess(
                    String name, String idCardNo, String gender, String nation,
                    String birth, String address, String issueAuthority, String validDate
            ) {
                if ("FRONT".equals(cardSide)) {
                    callback.onSimpleParseSuccess(name, idCardNo, address);
                } else {
                    callback.onSimpleParseSuccess(issueAuthority, validDate, "");
                }
            }

            @Override
            public void onParseError(String errorMsg) {
                callback.onParseError(errorMsg);
            }
        });
    }

    public interface SimpleParseCallback {
        void onSimpleParseSuccess(String field1, String field2, String field3);
        void onParseError(String errorMsg);
    }
}