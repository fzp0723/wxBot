package com.yg84.weixin;

import java.util.List;

/**
 * Created by fangzhipeng on 2017/7/22.
 */
public interface MessageHandler {

    public void handleMsg(List<Message> messages) throws Exception;

}
