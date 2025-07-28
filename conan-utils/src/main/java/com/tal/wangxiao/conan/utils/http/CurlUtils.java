package com.tal.wangxiao.conan.utils.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * curl请求工具类
 *
 * @author huyaoguo
 * @date 2021/2/22
 **/
public class CurlUtils {

    public static String getBodyByCurl(String curlUrl) {
        curlUrl = "curl " + curlUrl;
        String[] cmd = curlUrl.split(" ");
        ProcessBuilder process = new ProcessBuilder(cmd);
        Process p;
        try {
            p = process.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
            }
            return builder.toString();

        } catch (IOException e) {
            System.out.print("error，" + e.getMessage());
        }
        return null;
    }

    public static String getCookieByCurl(String curlUrl, String headerKey) {
        String cookieFromHead = "";
        curlUrl = "curl " + curlUrl;
        String[] cmd = curlUrl.split(" ");
        ProcessBuilder process = new ProcessBuilder(cmd);
        Process p;
        try {
            p = process.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
                builder.append(System.getProperty("line.separator"));
                if (line.contains(":")) {
                    String[] str = line.split(":");
                    if (headerKey.equals(str[0])) {
                        cookieFromHead += str[1];
                    }
                }
            }
            return cookieFromHead.trim();

        } catch (IOException e) {
            System.out.print("error，" + e.getMessage());
        }
        return null;
    }
    /**
     * @Description TODO 通过curl命令获取请求响应头中的指定字段
     * @author 彭俊华
     * @date 14:39 2025/1/14
     * @param curlUrl
     * @param headerKey
     * @return java.lang.String
     **/
    public static String getHeaderByCurl(String curlUrl, String headerKey) {
        // 初始化存储提取值的字符串
        String cookieFromHead = "";
        // 构建完整的 curl 命令
        String[] cmd = {"/bin/sh", "-c", "curl -s -D - " + curlUrl + " | grep '^" + headerKey + ":' | awk '{print $2}'"};

        // 创建 ProcessBuilder 对象
        ProcessBuilder process = new ProcessBuilder(cmd);
        Process p;
        try {
            // 启动进程
            p = process.start();
            // 读取进程的输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            // 逐行读取输出内容
            while ((line = reader.readLine()) != null) {
                cookieFromHead += line;
            }
            // 打印提取的值
            return cookieFromHead.trim();

        } catch (IOException e) {
            // 处理异常，打印错误信息
            System.out.print("error，" + e.getMessage());
        }
        return null;
    }

}
