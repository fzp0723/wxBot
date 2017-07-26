package com.yg84.weixin;

/**
 * Created by fangzhipeng on 2017/7/22.
 */
public class Message {

    private Integer msgType;
    private String fromUserName;
    private String content;

    public Integer getMsgType() {
        return msgType;
    }

    public void setMsgType(Integer msgType) {
        this.msgType = msgType;
    }

    public String getFromUserName() {
        return fromUserName;
    }

    public void setFromUserName(String fromUserName) {
        this.fromUserName = fromUserName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
