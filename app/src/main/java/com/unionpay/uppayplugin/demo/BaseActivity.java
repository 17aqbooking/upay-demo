package com.unionpay.uppayplugin.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.github.kevinsawicki.http.HttpRequest;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class BaseActivity extends Activity implements Callback,
        Runnable {
    public static final String LOG_TAG = "PayDemo";
    private Context mContext = null;
    private int mGoodsIdx = 0;
    private Handler mHandler = null;
    private ProgressDialog mLoadingDialog = null;

    public static final int PLUGIN_VALID = 0;
    public static final int PLUGIN_NOT_INSTALLED = -1;
    public static final int PLUGIN_NEED_UPGRADE = 2;

    /*****************************************************************
     * mMode参数解释： "00" - 启动银联正式环境 "01" - 连接银联测试环境
     *****************************************************************/
    private final String mMode = "01";
//    private static final String TN_URL_01 = "http://101.231.204.84:8091/sim/getacptn";
//    private static final String TN_URL_01 = "http://10.0.2.2:8080/ACPSample_AppServer/form05_6_2_AppConsume";
//    private static final String TN_URL_01 = "http://10.236.52.134:8080/ACPSample_AppServer/form05_6_2_AppConsume";
    private static final String TN_URL_01 = "http://116.6.119.234:19082/easyLinkApi/Payment/Pay";

    private String url = null;

    private String orderId = null;
    private String orderCreateTime = null;

    private String amount = null;
    private String pin = null;
    private String secPin = null;
    private String channel = null;
    private String secretKey = null;
    private String callbackUrl = null;
    private String frontendUrl = null;
    private String customerIp = null;
    private String merchantCardNumber = null;
    private String merReserved = null;

    private final View.OnClickListener mClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            EditText orderIdt = (EditText) findViewById(R.id.orderId);
            orderId = orderIdt.getText().toString();
            EditText txnAmt = (EditText) findViewById(R.id.amount);
            amount = txnAmt.getText().toString();
            EditText orderCreateTimet = (EditText) findViewById(R.id.orderCreateTime);
            orderCreateTime = orderCreateTimet.getText().toString();

            url = String.valueOf(((EditText) findViewById(R.id.url)).getText());
            pin = String.valueOf(((EditText) findViewById(R.id.pin)).getText());
            secPin = String.valueOf(((EditText) findViewById(R.id.secPin)).getText());
            channel = String.valueOf(((EditText) findViewById(R.id.channel)).getText());
            secretKey = String.valueOf(((EditText) findViewById(R.id.secretKey)).getText());
            callbackUrl = String.valueOf(((EditText) findViewById(R.id.callbackUrl)).getText());
            frontendUrl = String.valueOf(((EditText) findViewById(R.id.frontendUrl)).getText());
            customerIp = String.valueOf(((EditText) findViewById(R.id.customerIp)).getText());
            merchantCardNumber = String.valueOf(((EditText) findViewById(R.id.merchantCardNumber)).getText());
            merReserved = String.valueOf(((EditText) findViewById(R.id.merReserved)).getText());

            Log.e(LOG_TAG, " " + v.getTag());
            mGoodsIdx = (Integer) v.getTag();
            mLoadingDialog = ProgressDialog.show(mContext, // context
                    "", // title
                    "正在努力的获取tn中,请稍候...", // message
                    true); // 进度是否是不确定的，这只和创建进度条有关

            /***********步骤**************************************
             * 步骤1：从网络开始,获取交易流水号即TN
             ************************************************/
            new Thread(BaseActivity.this).start();
        }
    };

    public abstract void doStartUnionPayPlugin(Activity activity, String tn,
            String mode);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        mHandler = new Handler(this);

        setContentView(R.layout.activity_main);

        EditText orderId = (EditText) findViewById(R.id.orderId);
        orderId.setText(DateFormat.format("yyyyMMddHHmmss",new Date()));

        EditText orderCreateTimet = (EditText) findViewById(R.id.orderCreateTime);
        orderCreateTimet.setText(DateFormat.format("yyyy-MM-dd HH:mm:ss",new Date()));

        Button btn0 = (Button) findViewById(R.id.btn0);
        btn0.setTag(0);
        btn0.setOnClickListener(mClickListener);

//        TextView tv = (TextView) findViewById(R.id.guide);
//        tv.setTextSize(16);
//        updateTextView(tv);
    }

    public abstract void updateTextView(TextView tv);

    @Override
    public boolean handleMessage(Message msg) {
        Log.e(LOG_TAG, " " + "" + msg.obj);
        if (mLoadingDialog.isShowing()) {
            mLoadingDialog.dismiss();
        }

        String tn;

//        if (msg.obj == null || ((String) msg.obj).length() == 0) {
        if (msg.obj == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("错误提示");
            builder.setMessage("网络连接失败,请重试!");
            builder.setNegativeButton("确定",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.create().show();
        } else {
            tn = (String) msg.obj;
            /*************************************************
             * 步骤2：通过银联工具类启动支付插件
             ************************************************/
            doStartUnionPayPlugin(this, tn, mMode);
        }

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /*************************************************
         * 步骤3：处理银联手机支付控件返回的支付结果
         ************************************************/
        if (data == null) {
            return;
        }

        String msg = "";
        /*
         * 支付控件返回字符串:success、fail、cancel 分别代表支付成功，支付失败，支付取消
         */
        String str = data.getExtras().getString("pay_result");
        if (str.equalsIgnoreCase("success")) {

            // 如果想对结果数据验签，可使用下面这段代码，但建议不验签，直接去商户后台查询交易结果
            // result_data结构见c）result_data参数说明
            if (data.hasExtra("result_data")) {
                String result = data.getExtras().getString("result_data");
                try {
                    JSONObject resultJson = new JSONObject(result);
                    String sign = resultJson.getString("sign");
                    String dataOrg = resultJson.getString("data");
                    // 此处的verify建议送去商户后台做验签
                    // 如要放在手机端验，则代码必须支持更新证书
                    boolean ret = verify(dataOrg, sign, mMode);
                    if (ret) {
                        // 验签成功，显示支付结果
                        msg = "支付成功！";
                    } else {
                        // 验签失败
                        msg = "支付失败！";
                    }
                } catch (JSONException e) {
                }
            }
            // 结果result_data为成功时，去商户后台查询一下再展示成功
            msg = "支付成功！";
        } else if (str.equalsIgnoreCase("fail")) {
            msg = "支付失败！";
        } else if (str.equalsIgnoreCase("cancel")) {
            msg = "用户取消了支付";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("支付结果通知");
        builder.setMessage(msg);
        builder.setInverseBackgroundForced(true);
        // builder.setCustomTitle();
        builder.setNegativeButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

    Map<String, Object> jsonToMap(String content) {
        content = content.trim();
        Map<String, Object> result = new HashMap<>();
        try {
            if (content.charAt(0) == '[') {
                JSONArray jsonArray = new JSONArray(content);
                for (int i = 0; i < jsonArray.length(); i++) {
                    Object value = jsonArray.get(i);
                    if (value instanceof JSONArray || value instanceof JSONObject) {
                        result.put(i + "", jsonToMap(value.toString().trim()));
                    } else {
                        result.put(i + "", jsonArray.getString(i));
                    }
                }
            } else if (content.charAt(0) == '{'){
                JSONObject jsonObject = new JSONObject(content);
                Iterator<String> iterator = jsonObject.keys();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    Object value = jsonObject.get(key);
                    if (value instanceof JSONArray || value instanceof JSONObject) {
                        result.put(key, jsonToMap(value.toString().trim()));
                    } else {
                        result.put(key, value.toString().trim());
                    }
                }
            }else {
                Log.e("异常", "json2Map: 字符串格式错误");
            }
        } catch (JSONException e) {
            Log.e("异常", "json2Map: ", e);
            result = null;
        }
        return result;
    }

    /**
     * 生成签名信息
     * <p>
     * 基础请求对象
     *
     * @return 签名信息
     */
    public String generateSignature(Object obj, String secretKey) {
        // 获取验签映射集
        Map<String, String> map = SignatureUtil.getSignatureMap(obj);
        List<String> encodeList = SignatureUtil.mapToListBySort(map);
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < encodeList.size(); i++) {
            sb.append(encodeList.get(i)).append("&");
        }
        sb.append(secretKey);
//        String signature = DigestUtils.sha256Hex(sb.toString().trim());
        byte[] bytes = DigestUtils.sha256(sb.toString().trim());
        String signature = new String(Hex.encodeHex(bytes));
        return signature;
    }

    @Override
    public void run() {
        String tn = null;


//        InputStream is;
//        try {
//
            String url = this.url;
//
//            URL myURL = new URL(url);
//            URLConnection ucon = myURL.openConnection();
//            ucon.setConnectTimeout(120000);
//            is = ucon.getInputStream();
//            int i = -1;
//            ByteArrayOutputStream baos = new ByteArrayOutputStream();
//            while ((i = is.read()) != -1) {
//                baos.write(i);
//            }
//
//            tn = baos.toString();
//            is.close();
//            baos.close();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        String resp = null;
        try {
            String json = "{\"amount\":\"1\",\"customerIp\":\"172.0.0.1\",\"orderId\":\"20200915150841\",\"orderCreateTime\":\" 2020-09-15 15:08:41 \",\"payMode\":\"null\",\"channel\":\"43962300\",\"reCodeType\":\"0\",\"merReserved\":\"\",\"qrCodeType\":\"null\",\"bankId\":\"\",\"expireTime\":\"null\",\"pin\":\"CVBAQaOTRTbaALOVZGMaKKOhcaHMeMNX\",\"accessKey\":\"53c7d4b33082b97866d18409204c2e471a1bdc62759e154a0b21ccdb138b2695\",\"frontendUrl\":\"http://172.16.2.24:8080/easylink-demo/payCompleted\",\"feeDeductWay\":\"\",\"callbackUrl\":\"http://172.16.2.24:8080/easylink-demo/payCompleted\",\"secPin\":\"dfVGZgQHYBiLiATJWPDXjeaLjhihTgEh\",\"merchantCardNumber\":\"5309900599078555\"}";
            PayRequest payRequest = new PayRequest();
            payRequest.setOrderId(this.orderId);
            payRequest.setAmount(this.amount);
            payRequest.setOrderCreateTime(this.orderCreateTime);
            payRequest.setPin(this.pin);
            payRequest.setSecPin(this.secPin);
            payRequest.setChannel(this.channel);
            payRequest.setCallbackUrl(this.callbackUrl);
            payRequest.setFrontendUrl(this.frontendUrl);
            payRequest.setCustomerIp(this.customerIp);
            payRequest.setMerchantCardNumber(this.merchantCardNumber);
            payRequest.setMerReserved(this.merReserved);
            String secretKey = this.secretKey;
            payRequest.setAccessKey(this.generateSignature(payRequest, secretKey));
            Map<String, String> data = SignatureUtil.beanToMap(payRequest);
            resp = HttpRequest.post(url).trustAllCerts().trustAllHosts().form(data).connectTimeout(60 * 1000)
                    .readTimeout(60 * 1000).body();
            Map<String,Object> map = jsonToMap(resp);
            tn = map.get("tn").toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Message msg = mHandler.obtainMessage();
        msg.obj = tn;
        System.out.println("我是tn："+tn);
        mHandler.sendMessage(msg);
    }

    int startpay(Activity act, String tn, int serverIdentifier) {
        return 0;
    }

    private boolean verify(String msg, String sign64, String mode) {
        // 此处的verify，商户需送去商户后台做验签
        return true;

    }

}
