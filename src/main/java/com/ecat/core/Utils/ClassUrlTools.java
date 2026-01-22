package com.ecat.core.Utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;

/**
 * URL工具类
 * 提供URL解码功能，特别是处理包含中文路径的URL
 * 
 * @author coffee
 */
@Slf4j
public class ClassUrlTools {
    /**
     * 解码URL中的中文部分并返回新的URL对象
     * @param url 需要解码的URL对象
     * @return 解码后的URL对象，如果解码失败则返回原始URL
     */
    public static URL decodeUrlPath(URL url) {
        if (url == null) {
            return null;
        }

        try {
            // 解码URL字符串
            String decodedUrlStr = URLDecoder.decode(url.toString(), StandardCharsets.UTF_8.name());
            // 用解码后的字符串重新构建URL对象
            return new URL(decodedUrlStr);
        } catch (MalformedURLException e) {
            log.error("URL格式错误: " + e.getMessage());
            return url; // 格式错误时返回原始URL
        } catch (Exception e) {
            log.error("解码失败: " + e.getMessage());
            return url; // 其他错误时返回原始URL
        }
    }
}
