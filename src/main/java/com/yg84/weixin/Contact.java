package com.yg84.weixin;

import com.alibaba.fastjson.JSONObject;

import javax.sound.midi.Soundbank;

/**
 * Created by fangzhipeng on 2017/7/22.
 */
public class Contact {
    private String userName;
    private String nickName;
    private String remarkName;
    private String headImgUrl;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getRemarkName() {
        return remarkName;
    }

    public void setRemarkName(String remarkName) {
        this.remarkName = remarkName;
    }

    public String getHeadImgUrl() {
        return headImgUrl;
    }

    public void setHeadImgUrl(String headImgUrl) {
        this.headImgUrl = headImgUrl;
    }
}
