package com.yg84.weixin;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;
import com.squareup.okhttp.*;
import org.apache.commons.lang.SystemUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by fangzhipeng on 2017/7/18.
 */
public class WeChat {
    //项目临时文件目录
    private File tempRootDir = new File(System.getProperty("user.home") + "/.wxBot");;
    //http请求client
    private OkHttpClient client = new OkHttpClient();

    private String redirect_uri;
    //微信身份验证参数
    private String wxuin, wxsid, skey, pass_ticket;

    private String BASE_URL;

    private String BASE_HOST;
    //登录微信的设备Id
    private String DeviceID = "e" + (Math.random() + "").substring(2, 17);

    private String INIT_JSON="{\"BaseRequest\":{\"Sid\": \"%s\", \"Skey\": \"%s\", \"DeviceID\": \"%s\", \"Uin\": \"%s\"}}";
    //我的账号信息
    private MyAcount myAcount;
    //联系人信息
    private List<Contact> contacts;

    private JSONObject SyncKeyObject;

    private String SyncKey = "";

    private String SYNC_HOST;

    private MessageHandler handler;

    private boolean dealMsg = true;

    public WeChat(MessageHandler handler) {
        System.setProperty("jsse.enableSNIExtension", "false");
        if (handler == null)
            this.handler = new DefaultMessageHandler(this);
        else
            this.handler = handler;
        MyCookieStore cookieStore = new MyCookieStore();
        client.setCookieHandler(new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL));
        client.setReadTimeout(2, TimeUnit.MINUTES);
        initTempDir();
    }


    public String run() throws Exception{
        login();
        init();
        return "启动成功！";
    }

    public MyAcount getMyAcount() {
        return myAcount;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public String sendMsg(String name, String content) throws Exception{
        Contact target = null;
        for (Contact contact : contacts) {
            if (name.equals(contact.getNickName()) || name.equals(contact.getRemarkName())) {
                target = contact;
                break;
            }
        }
        if (target != null) {
            sendMsg(content, myAcount.getUserName(), target.getUserName());
            return "发送成功！";
        }
        else {
            return "没有找到对应的联系人！";
        }
    }

    public String sendMsg(String content, String fromUserName, String toUserName) throws Exception {
        String msgId = (getTimestamp() * 1000) + ((Math.random() + "").substring(0, 5).replace(".", ""));
        String paramTemplate = "{\"Msg\": {\"FromUserName\": \"%s\", \"LocalID\": \"%s\", \"Type\": 1, \"ToUserName\": \"%s\", \"Content\": \"%s\", \"ClientMsgId\": \"%S\"}, \"BaseRequest\": {\"Sid\": \"%s\", \"Skey\": \"%s\", \"DeviceID\": \"%s\", \"Uin\": %s}}";
        String json = String.format(paramTemplate, fromUserName, msgId, toUserName, content, msgId, wxsid, skey, DeviceID, wxuin);
        String url = String.format(BASE_URL + "/webwxsendmsg?pass_ticket=%s", pass_ticket);
        Response response = postJson(url, json);
        if (!response.isSuccessful())
            return "发送失败！";
        return "发送成功!";
    }

    public File getContactIcon(String userName) {
        File file = new File(tempRootDir, "/icon/" + userName + ".png");
        if (file.exists())
            return file;
        return null;
    }

    /**
     * 获取缓存的媒体文件
     * @param msgId 消息id
     * @param type 媒体文件类型，0-图片，1-声音
     * @return
     */
    public File getMediaFile(String msgId, Integer type) {
        File file = new File(tempRootDir, "/temp/" + msgId + (type == 0 ? ".jpg" : ".mp3"));
        if (file.exists())
            return file;
        return null;
    }

    public void stopProcessThread() {
        dealMsg = false;
    }

    private String login() throws Exception{
        String url = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&fun=new&lang=zh_CN&_=" + getTimestamp();
        Response response = get(url);
        if(!response.isSuccessful())
            return "登录异常！";
        String uuid = getUUID(response.body().string());
        genQRCode(uuid);
        while (true) {
            url = String.format("https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?uuid=%s&tip=1&_=%s", uuid, getTimestamp());
            response = get(url);
            if(!response.isSuccessful())
                return "登录异常！";
            String msg = response.body().string();
            System.out.println(msg);
            if (getRedirect_uri(msg)) {
                break;
            }
            Thread.sleep(500);
        }
        url = redirect_uri;
        response = get(url);
        if (!response.isSuccessful())
            return "登录失败！";
        String msg = response.body().string();
        if (!parseMsg(msg))
            return "登录失败！";
        return "登录成功！";
    }



    private String init() throws Exception{
        String paramTemplate = "/webwxinit?r=%s&lang=en_US&pass_ticket=%s&skey=%s";
        String url = String.format(BASE_URL + paramTemplate, getTimestamp(), pass_ticket, skey);
        String json = String.format(INIT_JSON, wxsid, skey, DeviceID, wxuin);
        Response response = postJson(url, json);
        if (!response.isSuccessful())
            return "微信初始化失败！";
        String msg = response.body().string();
        JSONObject msgObject = JSONObject.parseObject(msg);
        initMyAcount(msgObject);
        initSyncKey(msgObject);
        notifyWeixin();
        testSyncAndInitTimer();
        initContact();
        return "微信初始化成功！";
    }

    private String notifyWeixin() throws Exception{
        String msgId = (getTimestamp() * 1000) + ((Math.random() + "").substring(0, 5).replace(".", ""));
        String url = BASE_URL + "/webwxstatusnotify?lang=zh_CN&pass_ticket=" + pass_ticket;
        String jsonTemplate = "{\"BaseRequest\": {\"Sid\": \"%s\", \"Skey\": \"%s\", \"DeviceID\": \"%s\", \"Uin\": %s},\"Code\": 3,\"FromUserName\": \"%s\",\"ToUserName\": \"%s\",\"ClientMsgId\": %s}";
        String json = String.format(jsonTemplate, wxsid, skey, DeviceID, wxuin, myAcount.getUserName(), myAcount.getUserName(), msgId);
        Response response = postJson(url, json);
        if (!response.isSuccessful())
            return "微信服务器唤醒失败";
        String msg = response.body().string();
        return "微信唤醒成功！";
    }


    private String sync() throws Exception {
        String params = "r=%s&sid=%s&uin=%s&skey=%s&deviceid=%s&synckey=%s&_=%s";
        String url = "https://" + SYNC_HOST + "/cgi-bin/mmwebwx-bin/synccheck?" + String.format(params, getTimestamp(), wxsid, wxuin, skey, DeviceID, SyncKey, getTimestamp());
        Response response = get(url);
        if (!response.isSuccessful())
            return "同步失败！";
        String msg = response.body().string();
        Pattern pattern = Pattern.compile("retcode:\"(.*)\",selector:\"(.*)\"");
        Matcher m = pattern.matcher(msg);
        m.find();
        if (!"0".equals(m.group(2))) {
            receiveMsg();
        }
        return m.group(1);
    }

    private String receiveMsg() throws Exception{
        String params = "sid=%s&skey=%s&lang=en_US&pass_ticket=%s";
        String postMsg = "{\"BaseRequest\" : {\"Uin\":%s,\"Sid\":\"%s\"},\"SyncKey\" : %s,\"rr\" :%s}";
        String url = BASE_URL + "/webwxsync?" + String.format(params, wxsid, skey, pass_ticket);
        String json = String.format(postMsg, wxuin, wxsid, SyncKeyObject.toJSONString(), getTimestamp());
        Response response = postJson(url, json);
        if (!response.isSuccessful())
            return "接收消息失败！";
        String msg = response.body().string();
        System.out.println(msg);
        JSONObject msgObject = JSONObject.parseObject(msg);
        if (0 == msgObject.getJSONObject("BaseResponse").getInteger("Ret")) {
            List<Message> messages = JSONObject.parseArray(msgObject.getString("AddMsgList"), Message.class);
            for (Message message : messages) {
                if (message.getMsgType() == 3) {  //图片消息
                    dealPicMsg(message);
                }else if (message.getMsgType() == 34) { //语音消息
                    dealVoiceMsg(message);
                }
            }
            handler.handleMsg(messages);
            SyncKeyObject = msgObject.getJSONObject("SyncCheckKey");
            genSyncKey();
        }
        return "消息处理成功！";
    }

    private String dealVoiceMsg(Message message) throws Exception{
        String url = BASE_URL + "/webwxgetvoice?";
        String params = "msgid=%s&skey=%s";
        Response response = get(url + String.format(params, message.getMsgId(), skey));
        if (!response.isSuccessful())
            return "处理语音消息失败！";
        byte[] buf = new byte[1024];
        int len = -1;
        InputStream in = response.body().byteStream();
        File file = new File(tempRootDir, "/temp/" + message.getMsgId() + ".mp3");
        OutputStream out = new FileOutputStream(file);
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        return "处理成功！";
    }

    private String dealPicMsg(Message message) throws Exception{
        String url = BASE_URL + "/webwxgetmsgimg?&MsgID=%s&skey=%s&type=slave";
        Response response = get(String.format(url, message.getMsgId(), skey));
        if (!response.isSuccessful())
            return "处理图片消息失败！";
        byte[] buf = new byte[1024];
        int len = -1;
        InputStream in = response.body().byteStream();
        File file = new File(tempRootDir, "/temp/" + message.getMsgId() + ".jpg");
        OutputStream out = new FileOutputStream(file);
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
        return "处理成功！";
    }

    private Response get(String url) throws Exception{
        Request request = genBuilder().url(url).build();
        return client.newCall(request).execute();
    }

    private Response postJson(String url, String json) throws Exception{
        MediaType jsonType = MediaType.parse("application/json; charset=utf-8");
        Request request = genBuilder().url(url).post(RequestBody.create(jsonType, json)).build();
        return client.newCall(request).execute();
    }

    private String getUUID(String str) {
        String uuid = str.substring(str.indexOf("\"") + 1, str.lastIndexOf("\""));
        return uuid;
    }

    private Request.Builder genBuilder() {
        return new Request.Builder().header("User-Agent", "Mozilla/5.0 (X11; Linux i686; U;) Gecko/20070322 Kazehakase/0.4.5");
    }

    private boolean getRedirect_uri(String str) {
        if (str.indexOf("window.redirect_uri=") != -1) {
            redirect_uri = str.substring(str.indexOf("\"") + 1, str.lastIndexOf("\"")) + "&fun=new";
            BASE_URL = redirect_uri.substring(0, redirect_uri.lastIndexOf("/"));
            String tempHost = BASE_URL.substring(8);
            BASE_HOST = tempHost.substring(0, tempHost.indexOf("/"));
            return true;
        }
        return false;
    }

    long getTimestamp() {
        return Timestamp.valueOf(LocalDateTime.now()).getTime();
    }

    private void genQRCode(String uuid) throws Exception{
        String text = String.format("https://login.weixin.qq.com/l/%s", uuid); // 二维码内容
        Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");   // 内容所使用字符集编码
        QRCode qrCode = Encoder.encode(text, ErrorCorrectionLevel.L, hints);
        File outputFile = new File(tempRootDir,"/qrcode/qrcode.png");
        if (SystemUtils.IS_OS_LINUX) {
            byte[][] matrix = qrCode.getMatrix().getArray();
            System.out.print("\033[47;30m  \033[0m");
            for (int i = 0;i < matrix.length;i++) {
                System.out.print("\033[47;30m  \033[0m");
            }
            System.out.println("\033[47;30m  \033[0m");
            for (int i = 0;i < matrix.length; i++) {
                System.out.print("\033[47;30m  \033[0m");
                for (int j = 0;j < matrix[i].length; j++) {
                    if (matrix[i][j] == 1) {
                        System.out.print("\033[40;37m  \033[0m");
                    }else {
                        System.out.print("\033[47;30m  \033[0m");
                    }
                }
                System.out.println("\033[47;30m  \033[0m");
            }
            System.out.print("\033[47;30m  \033[0m");
            for (int i = 0;i < matrix.length;i++) {
                System.out.print("\033[47;30m  \033[0m");
            }
            System.out.println("\033[47;30m  \033[0m");
        }else if (SystemUtils.IS_OS_WINDOWS){
            int width = 300; // 二维码图片宽度
            int height = 300; // 二维码图片高度
            String format = "png";// 二维码的图片格式
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            // 生成二维码
            MatrixToImageWriter.writeToFile(bitMatrix, format, outputFile);
            Runtime run = Runtime.getRuntime();
            run.exec("cmd /c start " + outputFile.getAbsolutePath().replace(".\\", "").replace(":\\", ":\\\\"));
        }else if (SystemUtils.IS_OS_MAC) {
            int width = 300; // 二维码图片宽度
            int height = 300; // 二维码图片高度
            String format = "png";// 二维码的图片格式
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            // 生成二维码
            MatrixToImageWriter.writeToFile(bitMatrix, format, outputFile);
            Runtime run = Runtime.getRuntime();
            run.exec("open " + outputFile.getAbsolutePath());
        }else {
            throw new RuntimeException("未知操作系统平台，无法生成二维码");
        }

    }

    private void genSyncKey() {
        SyncKey = "";
        JSONArray array = SyncKeyObject.getJSONArray("List");
        for (int i = 0;i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String key = item.getString("Key");
            String value = item.getString("Val");
            SyncKey += "|" + key + "_" + value;
        }
        SyncKey = SyncKey.substring(1);
    }

    private boolean parseMsg(String msg) {
        Pattern pattern = Pattern.compile("<skey>(.*)</skey><wxsid>(.*)</wxsid><wxuin>(.*)</wxuin><pass_ticket>(.*)</pass_ticket>");
        Matcher m = pattern.matcher(msg);
        if (!m.find())
            return false;
        skey = m.group(1);
        wxsid = m.group(2);
        wxuin = m.group(3);
        pass_ticket = m.group(4);
        return true;
    }

    private void testSyncAndInitTimer() throws Exception{
        String[] hosts = new String[]{"webpush."};
        for (String host : hosts) {
            SYNC_HOST = host + BASE_HOST;
            String retcode = sync();
            if ("0".equals(retcode)) {
                break;
            }
        }
        new ReceiveMsgThread().start();
    }

    private void initMyAcount(JSONObject msgObject) {
        myAcount = JSONObject.parseObject(msgObject.getJSONObject("User").toJSONString(), MyAcount.class);
    }

    private void initSyncKey(JSONObject msgObject) {
        SyncKeyObject = msgObject.getJSONObject("SyncKey");
        SyncKey = "";
        genSyncKey();
    }

    private String initContact() throws Exception{
        String url = BASE_URL + "/webwxgetcontact?r=" + getTimestamp();
        String json = "{}";
        Response response = postJson(url, json);
        if (!response.isSuccessful())
            return "获取朋友列表失败！";
        String msg = response.body().string();
        contacts = JSONObject.parseArray(JSONObject.parseObject(msg).getString("MemberList"), Contact.class);
        contacts.forEach(contact -> {
            contact.setHeadImgUrl(BASE_URL.replace("/cgi-bin/mmwebwx-bin", "") + contact.getHeadImgUrl() + skey);
        });
        cacheContactIcon(contacts);
        return "获取朋友列表成功！";
    }


    public static void main(String[] args) {
        String msg = "window.synccheck={retcode:\"0\",selector:\"2\"}";
        Pattern pattern = Pattern.compile("retcode:\"(.*)\",selector:\"(.*)\"");
        Matcher m = pattern.matcher(msg);
        System.out.println(m.find());
    }

    private void initTempDir() {
        if (!tempRootDir.exists())
            tempRootDir.mkdirs();
        File qrcodedir = new File(tempRootDir, "/qrcode");
        if (!qrcodedir.exists())
            qrcodedir.mkdirs();
        File icondir = new File(tempRootDir, "/icon");
        if (!icondir.exists())
            icondir.mkdirs();
        File tempdir = new File(tempRootDir, "/temp");
        if (!tempdir.exists())
            tempdir.mkdirs();
    }

    private void cacheContactIcon(List<Contact> contacts) {
        File iconDir = new File(tempRootDir, "/icon");
        File[] files = iconDir.listFiles();
        for (File file1 : files) {
            file1.delete();
        }
        ExecutorService executor = Executors.newFixedThreadPool(20);
        for (Contact contact : contacts) {
            executor.submit(new CacheIcon(contact));
        }
    }

    class ReceiveMsgThread extends Thread {
        @Override
        public void run() {
            while (dealMsg) {
                try {
                    sync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CacheIcon implements Runnable {

        private Contact contact;

        public CacheIcon(Contact contact) {
            this.contact = contact;
        }

        @Override
        public void run() {
            try {
                Response response = get(contact.getHeadImgUrl());
                if (!response.isSuccessful())
                    return;
                byte[] buf = new byte[2014];
                int b = -1;
                FileOutputStream out = new FileOutputStream(new File(tempRootDir, "/icon/" + contact.getUserName() + ".png"));
                InputStream in = response.body().byteStream();
                while ((b = in.read(buf)) != -1) {
                    out.write(buf, 0, b);
                }
                in.close();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
