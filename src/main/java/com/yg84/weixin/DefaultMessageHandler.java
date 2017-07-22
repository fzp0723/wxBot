package com.yg84.weixin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by fangzhipeng on 2017/7/22.
 */
public class DefaultMessageHandler implements MessageHandler {

    private WeChat weChat;

    public DefaultMessageHandler(WeChat weChat) {
        this.weChat = weChat;
    }

    @Override
    public void handleMsg(List<Message> messages) throws Exception {
        List<Contact> contacts = weChat.getContacts();
        Map<String, Contact> contactByUserName = new HashMap<>();
        for (Contact contact : contacts) {
            contactByUserName.put(contact.getUserName(), contact);
        }
        for (Message message : messages) {
            Contact contact = contactByUserName.get(message.getFromUserName());
            System.out.println(contact.getNickName() + "发来消息：" + message.getContent());
        }
    }

}
