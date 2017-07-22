package com.yg84;

import com.yg84.weixin.WeChat;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableAutoConfiguration
@SpringBootApplication
public class WeixinApplication {


	public static WeChat weChat = new WeChat(null);

	public static void main(String[] args) {
		System.setProperty("jsse.enableSNIExtension", "false");
		SpringApplication.run(WeixinApplication.class, args);
	}
}
