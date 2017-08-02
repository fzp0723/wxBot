package com.yg84;

import com.yg84.weixin.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by fangzhipeng on 2017/7/16.
 */
@RestController
public class Login {

    public static WeChat weChat;
    ArrayBlockingQueue<Message> messageQueue = new ArrayBlockingQueue<Message>(10000);

    private void initWeChat() {
        if (weChat != null) {
            weChat.stopProcessThread();
        }
        MessageHandler handler = new MessageHandler() {
            @Override
            public void handleMsg(List<Message> messages) throws Exception {
                messageQueue.addAll(messages);
                System.out.println("成功加入" + messages.size() + "条信息");
            }
        };
        weChat = new WeChat(handler);
    }

    @RequestMapping(value = "/login")
    public String login() throws Exception {
        initWeChat();
        return weChat.run();
    }

    @RequestMapping(value = "/sendMsg")
    public String sendMsg(String name, String content) throws Exception{
        return weChat.sendMsg(name, content);
    }

    @RequestMapping(value = "/getContact")
    public List<Contact> getContact() {
        return weChat.getContacts();
    }

    @RequestMapping(value = "/loadMessage")
    public List<Message> loadMessage() {
        List<Message> messages = new ArrayList<>();
        Message message;
        while ((message = messageQueue.poll()) != null) {
            messages.add(message);
        }
        return messages;
    }

    @RequestMapping(value = "/syncLoadMessage")
    public List<Message> syncLoadMessage() throws Exception{
        List<Message> messages = new ArrayList<>();
        Message message = messageQueue.poll(5, TimeUnit.SECONDS);
        if (message != null) {
            messages.add(message);
            while ((message = messageQueue.poll()) != null) {
                messages.add(message);
            }
        }
        return messages;
    }

    @RequestMapping(value = "/getContactIcon")
    public void getContactIcon(String userName, HttpServletResponse response) throws Exception{
        File file = weChat.getContactIcon(userName);
        if (file != null) {
            response.addHeader("Content-Type", "image/x-png");
            OutputStream out = response.getOutputStream();
            InputStream in = new FileInputStream(file);
            byte[] buf = new byte[2014];
            int b = -1;
            while ((b = in.read(buf)) != -1) {
                out.write(buf, 0, b);
            }
            in.close();
            out.close();
        }
    }

    @RequestMapping(value = "/getMyAcount")
    public MyAcount getMyAcount() {
        return weChat.getMyAcount();
    }
}
