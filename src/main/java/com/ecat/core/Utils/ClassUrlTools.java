/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Utils;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

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
     * 构造本地 jar 的规范 URL：file 部分来自 {@link File#toURI()}。
     *
     * <p>{@link File#getAbsolutePath()} 裸拼在 Windows 会产出
     * {@code jar:file:C:\...!/} 这类含盘符/反斜杠的非规范 URL。统一经
     * {@link File#toURI()} 可得到正斜杠与 percent-encode 形态（Windows 盘符为 {@code /C:/}）。</p>
     *
     * @param jarFile 本地 jar 文件
     * @return {@code jar:<file-uri>!/} 形态 URL
     * @throws MalformedURLException URL 构造失败
     */
    public static URL canonicalJarUrl(File jarFile) throws MalformedURLException {
        Objects.requireNonNull(jarFile, "jarFile");
        return new URL("jar:" + jarFile.toURI() + "!/");
    }

    /**
     * 将类代码源 URL 转为本机物理路径。
     *
     * <p>支持两类生产形态：</p>
     * <ul>
     *   <li>{@code file:...}：IDE/classes 或本地文件；</li>
     *   <li>{@code jar:file:...!/}：core 以本地 jar 装载集成类的生产形态。</li>
     * </ul>
     *
     * <p>jar 形态必须经 {@link JarURLConnection#getJarFileURL()} 取官方内层 URL，
     * 再由 {@link Paths#get(java.net.URI)} 完成 URI 到平台路径的转换。禁止调用方手剥
     * {@code file:}/!{/} 字符串：Windows 规范 URL {@code jar:file:/C:/...!/} 被剥成
     * {@code /C:/...} 后交给 NIO 会因盘符冒号抛 {@code InvalidPathException}。</p>
     *
     * @param location 类 ProtectionDomain CodeSource location
     * @return 本机物理路径（文件或目录由调用方按需校验）
     * @throws IOException jar 连接建立失败
     * @throws URISyntaxException 内层 file URL 无法转 URI
     */
    public static Path physicalCodeSourcePath(URL location)
            throws IOException, URISyntaxException {
        Objects.requireNonNull(location, "location");

        if ("file".equalsIgnoreCase(location.getProtocol())) {
            return Paths.get(location.toURI());
        }
        if ("jar".equalsIgnoreCase(location.getProtocol())) {
            URLConnection connection = location.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                throw new IllegalArgumentException(
                    "jar 代码源不是标准 JarURLConnection: " + location);
            }
            URL jarFileUrl = ((JarURLConnection) connection).getJarFileURL();
            if (!"file".equalsIgnoreCase(jarFileUrl.getProtocol())) {
                throw new IllegalArgumentException(
                    "jar 代码源内层不是 file 协议: " + jarFileUrl);
            }
            return Paths.get(jarFileUrl.toURI());
        }
        throw new IllegalArgumentException("不支持的代码源协议: " + location.getProtocol());
    }

    /**
     * 解码URL中的中文部分并返回新的URL对象
     * <p>按 URL path 语义解码：字面 "+" 保持加号（path 中空格只编码为 %20），
     * 与表单语义（"+"=空格）区分。</p>
     * @param url 需要解码的URL对象
     * @return 解码后的URL对象，如果解码失败则返回原始URL
     */
    public static URL decodeUrlPath(URL url) {
        if (url == null) {
            return null;
        }

        try {
            // 解码URL字符串。URLDecoder 是表单语义（"+"=空格），而 jar:/file: URL 的
            // path 中 "+" 是字面加号——先转义为 %2B 再解码，%XX（中文/空格）行为不变。
            String decodedUrlStr = URLDecoder.decode(url.toString().replace("+", "%2B"), StandardCharsets.UTF_8.name());
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
