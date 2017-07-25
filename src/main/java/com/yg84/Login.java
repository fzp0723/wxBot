package com.yg84;

import com.yg84.weixin.Contact;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @RequestMapping
    public List<Contact> getContact() {
        return WeixinApplication.weChat.getContacts();
    }
}
