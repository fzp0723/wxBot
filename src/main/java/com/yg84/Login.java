package com.yg84;

import com.squareup.okhttp.OkHttpClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by fangzhipeng on 2017/7/16.
 */
@RestController
public class Login {

    @RequestMapping(value = "/login")
    public String login() throws Exception {
        return WeixinApplication.weChat.run();
    }

    @RequestMapping(value = "/sendMsg")
    public String sendMsg(String name, String content) throws Exception{
        return WeixinApplication.weChat.sendMsg(name, content);
    }

}
