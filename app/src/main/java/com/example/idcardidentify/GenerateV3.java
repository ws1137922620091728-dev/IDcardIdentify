package com.example.idcardidentify;

import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class GenerateV3 {
    private static final String TAG = "GenerateV3";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final String TERMINATION_STRING = "tc3_request";
    private static final String CHARSET = "UTF-8";

    private static final String FIXED_SECRET_ID = "";
    private static final String FIXED_SECRET_KEY = "";
    private static final String FIXED_REGION = "ap-guangzhou";
    private static final String FIXED_ACTION = "IDCardOCR";
    private static final String FIXED_VERSION = "2018-11-19";

    private String secretId;
    private String secretKey;
    private String region;
    private String action;
    private String version;
    private String contentType;

    public GenerateV3() {
        this.secretId = FIXED_SECRET_ID;
        this.secretKey = FIXED_SECRET_KEY;
        this.region = FIXED_REGION;
        this.action = FIXED_ACTION;
        this.version = FIXED_VERSION;
        this.contentType = "application/json; charset=utf-8";
    }

    public GenerateV3(String secretId, String secretKey, String region, String action, String version) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.action = action;
        this.version = version;
        this.contentType = "application/json; charset=utf-8";
    }

    public Result generateSignHeaders(String requestBody) {
        try {
            //获取当前时间戳
            long timestamp = System.currentTimeMillis() / 1000;
            String date = getUtcDate(timestamp);
            //拼接规范请求串
            String canonicalRequest = buildCanonicalRequest(requestBody, date);
            //拼接待签名字符串
            String stringToSign = buildStringToSign(canonicalRequest, timestamp, date);
            //计算派生签名密钥
            byte[] secretSigning = generateSecretSigning(date);
            //计算签名摘要
            String signature = calculateSignature(stringToSign, secretSigning);
            //生成Authorization头
            String authorization = buildAuthorization(date, signature);
            //组装最终请求头
            return new Result(
                    authorization,
                    timestamp + "",
                    date,
                    region,
                    action,
                    version,
                    contentType
            );
        } catch (Exception e) {
            Log.e(TAG, "签名生成失败：" + e.getMessage(), e);
            return null;
        }
    }

    private String buildCanonicalRequest(String requestBody, String date) throws Exception {
        String httpMethod = "POST";
        String canonicalUri = "/";
        String canonicalQueryString = "";
        String host = "ocr.tencentcloudapi.com";

        //拼接CanonicalHeaders
        String canonicalHeaders = String.format(
                "content-type:%s\nhost:%s\nx-tc-action:%s\n",
                contentType.toLowerCase(),
                host.toLowerCase(),
                action.toLowerCase()
        );

        //拼接SignedHeaders
        String signedHeaders = "content-type;host;x-tc-action";

        //计算请求体哈希
        String hashedRequestPayload = sha256Hex(requestBody);

        //组装规范请求串
        return String.format(
                "%s\n%s\n%s\n%s\n%s\n%s",
                httpMethod,
                canonicalUri,
                canonicalQueryString,
                canonicalHeaders,
                signedHeaders,
                hashedRequestPayload
        );
    }

    private String buildStringToSign(String canonicalRequest, long timestamp, String date) throws Exception {
        // CredentialScope：日期/服务/tc3_request（服务名：ocr，从域名 ocr.tencentcloudapi.com 提取）
        String credentialScope = String.format("%s/ocr/%s", date, TERMINATION_STRING);
        // 规范请求串哈希
        String hashedCanonicalRequest = sha256Hex(canonicalRequest);

        return String.format(
                "%s\n%d\n%s\n%s",
                ALGORITHM,
                timestamp,
                credentialScope,
                hashedCanonicalRequest
        );
    }

    private byte[] generateSecretSigning(String date) throws Exception {
        byte[] secretDate = hmac256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmac256(secretDate, "ocr"); // 服务名：ocr（身份证识别属于 OCR 服务）
        return hmac256(secretService, TERMINATION_STRING);
    }

    private String calculateSignature(String stringToSign, byte[] secretSigning) throws Exception {
        byte[] signatureBytes = hmac256(secretSigning, stringToSign);
        return bytesToHex(signatureBytes).toLowerCase();
    }

    private String buildAuthorization(String date, String signature) {
        String credentialScope = String.format("%s/ocr/%s", date, TERMINATION_STRING);
        String signedHeaders = "content-type;host;x-tc-action";
        return String.format(
                "%s Credential=%s/%s, SignedHeaders=%s, Signature=%s",
                ALGORITHM,
                secretId,
                credentialScope,
                signedHeaders,
                signature
        );
    }

    private String getUtcDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(timestamp * 1000));
    }

    private String sha256Hex(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(digest);
    }

    private byte[] hmac256(byte[] key, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key, mac.getAlgorithm());
        mac.init(secretKeySpec);
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append("0");
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    public static class Result {
        public String authorization;  //Authorization
        public String timestamp;     //X-TC-Timestamp
        public String date;         //UTC
        public String region;       //X-TC-Region
        public String action;       //X-TC-Action
        public String version;      //X-TC-Version
        public String contentType;  //Content-Type

        public Result(String authorization, String timestamp, String date, String region,
                      String action, String version, String contentType) {
            this.authorization = authorization;
            this.timestamp = timestamp;
            this.date = date;
            this.region = region;
            this.action = action;
            this.version = version;
            this.contentType = contentType;
        }
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}