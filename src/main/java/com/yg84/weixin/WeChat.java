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

import java.io.File;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by fangzhipeng on 2017/7/18.
 */
public class WeChat {

    private OkHttpClient client = new OkHttpClient();

    private final String ID_URL = "https://login.weixin.qq.com/jslogin?appid=wx782c26e4c19acffb&fun=new&lang=zh_CN&_=";

    private final String QR_CODE_URL = "https://login.weixin.qq.com/l/%s";

    //https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?tip=%s&uuid=%s&_=%s

    private final String QUERY_LOGIN_URL = "https://login.weixin.qq.com/cgi-bin/mmwebwx-bin/login?uuid=%s&tip=1&_=%s";

    //https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=AZIGwFAhLwYpsl2Vb58f8a_d@qrticket_0&uuid=AajL59ccVA==&lang=zh_CN&scan=1500473302&fun=new
    //https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxnewloginpage?ticket=AWa4aLOGudDEUnKxXhPGBnVf@qrticket_0&uuid=4eS6FygDDQ==&lang=zh_CN&scan=1500475502

    private String redirect_uri;

    private String wxuin;

    private String wxsid;

    private String skey;

    private String pass_ticket;

    private String BASE_URL;

    private String BASE_HOST;

    private String DeviceID = "e" + (Math.random() + "").substring(2, 17);

    private String INIT_JSON="{\"BaseRequest\":{\"Sid\": \"%s\", \"Skey\": \"%s\", \"DeviceID\": \"%s\", \"Uin\": \"%s\"}}";

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private MyAcount myAcount;

    private List<Contact> contacts;

    private Map<String, Contact> contactByUserName;

    private JSONArray member;

    private JSONObject MY_ACOUNT;

    private JSONObject SyncKeyObject;

    private String SyncKey = "";

    private String SYNC_HOST;

    private String SYNC_URL = "https://webpush.weixin.qq.com/cgi-bin/mmwebwx-bin/synccheck?";

    private Timer SYNC_TIMER = new Timer();

    private MessageHandler handler;

    public WeChat(MessageHandler handler) {
        System.setProperty("jsse.enableSNIExtension", "false");
        if (handler == null)
            this.handler = new DefaultMessageHandler(this);
        else
            this.handler = handler;
        MyCookieStore cookieStore = new MyCookieStore();
        client.setCookieHandler(new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL));
        client.setReadTimeout(2, TimeUnit.MINUTES);
    }

    public String run() throws Exception{
        login();
        init();
        return "启动成功！";
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

    private String login() throws Exception{
        String url = ID_URL + getTimestamp();
        Response response = get(url);
        if(!response.isSuccessful())
            return "登录异常！";
        String uuid = getUUID(response.body().string());
        genQRCode(uuid);
        while (true) {
            url = String.format(QUERY_LOGIN_URL, uuid, getTimestamp());
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
        //https://wx2.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1500802067&lang=en_US&pass_ticket=nUdHkjHvQdDvTjkKgwXvWKxhUnaHRVkEThSlewnfN96PrCKNz7BzfnHsJMeGEDIv'
        //https://wx.qq.com/cgi-bin/mmwebwx-bin/webwxinit?r=1500802122287&lang=en_US&pass_ticket=OHko%2FnZBuD7LEo263JXXkoDfDqQPrTyV%2FzVScFha0TjoGUYrOwAPUpW2kudYrYWy&skey=@crypt_1f56ee6a_6635b93ce4b5e19d2b7ce05344cb71bb
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
        initContact();
        testSyncAndInitTimer();
        return "微信初始化成功！";
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
        JSONObject msgObject = JSONObject.parseObject(msg);
        if (0 == msgObject.getJSONObject("BaseResponse").getInteger("Ret")) {
            List<Message> messages = JSONObject.parseArray(msgObject.getString("AddMsgList"), Message.class);
            handler.handleMsg(messages);
            SyncKeyObject = msgObject.getJSONObject("SyncCheckKey");
            genSyncKey();
        }
        return "消息处理成功！";
    }

    private Response get(String url) throws Exception{
        Request request = genBuilder().url(url).build();
        return client.newCall(request).execute();
    }

    private Response postJson(String url, String json) throws Exception{
        Request request = genBuilder().url(url).post(RequestBody.create(JSON, json)).build();
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
        String text = String.format(QR_CODE_URL, uuid); // 二维码内容
        Hashtable<EncodeHintType, String> hints = new Hashtable<EncodeHintType, String>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");   // 内容所使用字符集编码
        QRCode qrCode = Encoder.encode(text, ErrorCorrectionLevel.L, hints);
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
            File outputFile = new File("./result.png");
            MatrixToImageWriter.writeToFile(bitMatrix, format, outputFile);
            Runtime run = Runtime.getRuntime();
            System.out.println("cmd /c start " + outputFile.getAbsolutePath().replace(".\\", "").replace(":\\", ":\\\\"));
            run.exec("cmd /c start " + outputFile.getAbsolutePath().replace(".\\", "").replace(":\\", ":\\\\"));
        }else if (SystemUtils.IS_OS_MAC) {
            int width = 300; // 二维码图片宽度
            int height = 300; // 二维码图片高度
            String format = "png";// 二维码的图片格式
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);
            // 生成二维码
            File outputFile = new File("./result.png");
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
//        def test_sync_check(self):
//        for host1 in ['webpush.', 'webpush2.']:
//        self.sync_host = host1+self.base_host
//        try:
//        retcode = self.sync_check()[0]
//        except:
//        retcode = -1
//        if retcode == '0':
//        return True
//        return False
        String[] hosts = new String[]{"webpush."};
        for (String host : hosts) {
            SYNC_HOST = host + BASE_HOST;
            String retcode = sync();
            if ("0".equals(retcode)) {
                break;
            }
        }
        new ReceiveMsgThread().start();
//        SYNC_TIMER.schedule(new TimerTask() {
//            @Override
//            public void run() {
//                try {
//                    sync();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//        }, 1000, 2000);
    }

    private void initMyAcount(JSONObject msgObject) {
        MY_ACOUNT = msgObject.getJSONObject("User");
        myAcount = JSONObject.parseObject(MY_ACOUNT.toJSONString(), MyAcount.class);
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
        member = JSONObject.parseObject(msg).getJSONArray("MemberList");
        contacts = JSONObject.parseArray(JSONObject.parseObject(msg).getString("MemberList"), Contact.class);
        contactByUserName = new HashMap<>();
        contacts.forEach(contact -> {
            contactByUserName.put(contact.getUserName(), contact);
        });
        return "获取朋友列表成功！";
    }


    public static void main(String[] args) {
        String msg = "window.synccheck={retcode:\"0\",selector:\"2\"}";
        Pattern pattern = Pattern.compile("retcode:\"(.*)\",selector:\"(.*)\"");
        Matcher m = pattern.matcher(msg);
        System.out.println(m.find());
    }

    class ReceiveMsgThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    sync();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}
