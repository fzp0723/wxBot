package com.yg84;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Created by fangzhipeng on 2017/8/2.
 */
public class CommonTest {

    @Test
    public void testHomeDir() throws Exception{
        System.out.println(System.getProperty("user.home"));
        File file = new File("C:\\Users\\Paul0\\.wxBot\\qrcode\\qrcode.txt");
        OutputStream out = new FileOutputStream(file);
        out.write("hello world".getBytes("utf-8"));
        out.close();
    }

}
