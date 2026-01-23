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

package com.ecat.core.Repository;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.JSONReader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 云端仓库客户端 - 连接 ECAT 云平台
 *
 * <p>Java 8 兼容实现，使用 HttpURLConnection 和 fastjson2
 * 公开访问设计，无需认证</p>
 *
 * @author coffee
 * @version 1.0.0
 */
public class CloudRepositoryClient {

    private final String cloudApiBaseUrl;
    private final int connectTimeout;
    private final int readTimeout;

    /**
     * 构造函数
     *
     * @param cloudApiBaseUrl 云平台 API 基础 URL，例如：http://localhost:8000
     */
    public CloudRepositoryClient(String cloudApiBaseUrl) {
        // 移除末尾的斜杠
        this.cloudApiBaseUrl = cloudApiBaseUrl.endsWith("/")
            ? cloudApiBaseUrl.substring(0, cloudApiBaseUrl.length() - 1)
            : cloudApiBaseUrl;
        this.connectTimeout = 10000;  // 10秒
        this.readTimeout = 30000;     // 30秒
    }

    /**
     * 搜索包（公开接口）
     *
     * @param keyword 搜索关键词
     * @return 搜索结果
     * @throws IOException 网络或解析异常
     */
    public PackageSearchResult searchPackages(String keyword) throws IOException {
        String url = cloudApiBaseUrl + "/api/packages?keyword=" + URLEncoder.encode(keyword, "UTF-8");
        return executeGet(url, PackageSearchResult.class);
    }

    /**
     * 获取包详细信息（公开接口）
     *
     * 支持两种格式：
     * 1. groupId:artifactId (Maven 坐标格式)
     * 2. artifactId (仅构件ID，会搜索并自动选择)
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @return 包详细信息
     * @throws IOException 网络或解析异常
     */
    public PackageInfo getPackageInfo(String coordinate) throws IOException {
        // 解析坐标
        String[] parts = coordinate.split(":");
        String groupId = null;
        String artifactId;

        if (parts.length == 2) {
            // 完整坐标：groupId:artifactId
            groupId = parts[0];
            artifactId = parts[1];
        } else if (parts.length == 1) {
            // 仅 artifactId：需要搜索
            artifactId = parts[0];
            // 搜索获取候选包
            PackageSearchResult result = searchPackages(artifactId);
            List<PackageItem> candidates = result.getItems();

            if (candidates.isEmpty()) {
                throw new IOException("Package not found: " + artifactId);
            } else if (candidates.size() > 1) {
                // 多个同名包，返回错误让用户指定完整坐标
                StringBuilder sb = new StringBuilder();
                sb.append("Multiple packages found for '").append(artifactId).append("'. ");
                sb.append("Please specify full coordinate (groupId:artifactId):\n");
                for (PackageItem p : candidates) {
                    sb.append("  - ").append(p.getGroupId()).append(":").append(p.getArtifactId()).append("\n");
                }
                throw new IOException(sb.toString());
            }
            // 只有一个，使用它的 groupId
            groupId = candidates.get(0).getGroupId();
        } else {
            throw new IOException("Invalid coordinate format: " + coordinate + ". Expected 'groupId:artifactId' or 'artifactId'");
        }

        // 使用新 v2.0.0 API 获取详情
        // 注意：API 使用 groupId 本身（含点号），不是路径格式
        String url = cloudApiBaseUrl + "/api/packages/groups/"
                   + groupId + "/artifacts/" + artifactId;
        PackageDetailResponse response = executeGet(url, PackageDetailResponse.class);
        PackageInfo info = response.getInfo();

        // 从 releases 中提取版本列表和依赖关系
        if (response.getReleases() != null) {
            List<String> versions = new ArrayList<>(response.getReleases().keySet());
            // 按版本号降序排序
            versions.sort((v1, v2) -> {
                try {
                    return com.ecat.core.Version.Version.parse(v2)
                            .compareTo(com.ecat.core.Version.Version.parse(v1));
                } catch (Exception e) {
                    return v2.compareTo(v1);
                }
            });
            info.setVersions(versions);
            // 设置最新版本
            if (!versions.isEmpty()) {
                info.setLatestVersion(versions.get(0));
            }

            // 构建 dependencies Map（从 releases 中提取每个版本的依赖）
            java.util.Map<String, List<DependencyItem>> dependencies = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, java.util.List<ReleaseInfo>> entry : response.getReleases().entrySet()) {
                String version = entry.getKey();
                java.util.List<ReleaseInfo> releaseList = entry.getValue();
                if (releaseList != null && !releaseList.isEmpty()) {
                    ReleaseInfo releaseInfo = releaseList.get(0);
                    List<DependencyItem> deps = releaseInfo.getRequiresDist();
                    if (deps != null) {
                        dependencies.put(version, deps);
                    }
                }
            }
            info.setDependencies(dependencies);
        }

        return info;
    }

    /**
     * 获取包详细信息（旧接口，仅 artifactId，已废弃）
     *
     * @deprecated 使用 getPackageInfo(coordinate) 代替，支持 groupId:artifactId 格式
     * @param artifactId 构件ID，例如：integration-modbus
     * @return 包详细信息
     * @throws IOException 网络或解析异常
     */
    @Deprecated
    public PackageInfo getPackageInfoByArtifactId(String artifactId) throws IOException {
        return getPackageInfo(artifactId);
    }

    /**
     * 获取可用版本列表（公开接口）
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @return 版本列表
     * @throws IOException 网络或解析异常
     */
    public List<String> getAvailableVersions(String coordinate) throws IOException {
        // 解析坐标获取 groupId 和 artifactId
        String[] parts = coordinate.split(":");
        String groupId;
        String artifactId;

        if (parts.length == 2) {
            groupId = parts[0];
            artifactId = parts[1];
        } else if (parts.length == 1) {
            // 仅 artifactId：先获取包信息来确定 groupId
            PackageInfo info = getPackageInfo(coordinate);
            groupId = info.getGroupId();
            artifactId = info.getArtifactId();
        } else {
            throw new IOException("Invalid coordinate format: " + coordinate);
        }

        // 使用新 v2.0.0 API
        // 注意：API 使用 groupId 本身（含点号），不是路径格式
        String url = cloudApiBaseUrl + "/api/packages/groups/"
                   + groupId + "/artifacts/" + artifactId + "/versions";
        // fastjson2 解析数组
        HttpURLConnection conn = createConnection(url);
        try {
            String response = readResponse(conn, url);
            return JSON.parseArray(response, String.class);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 批量查询包信息（公开接口）
     * <p>
     * 一次查询多个包的详细信息，用于版本求解场景。
     * </p>
     *
     * @param coordinates 包坐标列表（Maven 坐标格式 "groupId:artifactId"）
     * @return 批量查询结果，key 为 coordinate
     * @throws IOException 网络或解析异常
     */
    public java.util.Map<String, PackageInfo> batchQueryPackages(java.util.List<String> coordinates)
            throws IOException {
        // 构建请求体
        java.util.Map<String, java.util.List<String>> requestBody = new java.util.HashMap<>();
        requestBody.put("coordinates", coordinates);

        // 执行 POST 请求
        String url = cloudApiBaseUrl + "/api/packages/batch-query";
        String response = executePost(url, requestBody);

        // 解析响应
        java.util.Map<String, java.util.Map<String, Object>> resultMap =
            JSON.parseObject(response, new com.alibaba.fastjson2.TypeReference<
                java.util.Map<String, java.util.Map<String, Object>>>() {});

        java.util.Map<String, PackageInfo> result = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry : resultMap.entrySet()) {
            String coordinate = entry.getKey();
            Object infoObj = entry.getValue();

            if (infoObj != null) {
                // 将 infoObj 转为 JSON 再解析为 PackageInfo
                String json = JSON.toJSONString(infoObj);
                PackageInfo info = JSON.parseObject(json, PackageInfo.class);
                result.put(coordinate, info);
            }
        }

        return result;
    }

    /**
     * 获取依赖关系（公开接口）
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @param version 版本号
     * @return 依赖关系图数据
     * @throws IOException 网络或解析异常
     */
    public DependencyGraph getDependencies(String coordinate, String version) throws IOException {
        // 解析坐标获取 groupId 和 artifactId
        String[] parts = coordinate.split(":");
        String groupId;
        String artifactId;

        if (parts.length == 2) {
            groupId = parts[0];
            artifactId = parts[1];
        } else if (parts.length == 1) {
            // 仅 artifactId：先获取包信息来确定 groupId
            PackageInfo info = getPackageInfo(coordinate);
            groupId = info.getGroupId();
            artifactId = info.getArtifactId();
        } else {
            throw new IOException("Invalid coordinate format: " + coordinate);
        }

        // 使用新 v2.0.0 API
        // 注意：API 使用 groupId 本身（含点号），不是路径格式
        String url = cloudApiBaseUrl + "/api/packages/groups/"
                   + groupId + "/artifacts/" + artifactId + "/dependencies?version=" + version;
        return executeGet(url, DependencyGraph.class);
    }

    /**
     * 下载 JAR 包到本地 Maven 仓库（Maven 协议，公开访问）
     *
     * @param coordinate Maven 坐标格式 "groupId:artifactId" 或仅 "artifactId"
     * @param version 版本号
     * @return 下载的文件路径
     * @throws IOException 网络或IO异常
     */
    public Path downloadPackage(String coordinate, String version) throws IOException {
        // 解析坐标获取 groupId 和 artifactId
        String[] parts = coordinate.split(":");
        String groupId;
        String artifactId;

        if (parts.length == 2) {
            groupId = parts[0];
            artifactId = parts[1];
        } else if (parts.length == 1) {
            // 仅 artifactId：先获取包信息来确定 groupId
            PackageInfo info = getPackageInfo(coordinate);
            groupId = info.getGroupId();
            artifactId = info.getArtifactId();
        } else {
            throw new IOException("Invalid coordinate format: " + coordinate);
        }

        // Maven 协议路径（使用实际的 groupId）
        String url = cloudApiBaseUrl + "/repository/public/"
                   + groupId.replace('.', '/') + "/"
                   + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";

        // 本地存储路径：~/.m2/repository/{groupId_path}/{artifactId}/{version}/
        Path localRepo = Paths.get(System.getProperty("user.home"), ".m2", "repository",
                                   groupId.replace('.', '/'),
                                   artifactId, version);
        Files.createDirectories(localRepo);

        Path targetFile = localRepo.resolve(artifactId + "-" + version + ".jar");

        // 下载文件（Java 8 方式）
        HttpURLConnection conn = createConnection(url);
        try {
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP " + responseCode + "下载失败: " + conn.getResponseMessage());
            }

            try (InputStream in = conn.getInputStream();
                 OutputStream out = Files.newOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }

            return targetFile;
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 通用 GET 请求（Java 8 兼容，无需认证）
     *
     * @param url 请求URL
     * @param clazz 响应类型
     * @param <T> 泛型类型
     * @return 响应对象
     * @throws IOException 网络或解析异常
     */
    private <T> T executeGet(String url, Class<T> clazz) throws IOException {
        HttpURLConnection conn = createConnection(url);
        try {
            String response = readResponse(conn, url);

            // 配置支持 snake_case
            JSONReader.Feature[] features = {
                JSONReader.Feature.SupportSmartMatch
            };

            return JSON.parseObject(response, clazz, features);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 通用 POST 请求（Java 8 兼容，无需认证）
     *
     * @param url 请求URL
     * @param requestBody 请求体（会被转为 JSON）
     * @return 响应字符串
     * @throws IOException 网络或解析异常
     */
    private String executePost(String url, Object requestBody) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            // 写入请求体
            String jsonBody = JSON.toJSONString(requestBody);
            try (OutputStream out = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes("UTF-8");
                out.write(input, 0, input.length);
            }

            return readResponse(conn, url);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * 创建 HTTP 连接
     */
    private HttpURLConnection createConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }

    /**
     * 读取 HTTP 响应
     */
    private String readResponse(HttpURLConnection conn, String url) throws IOException {
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errorMsg = null;
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, "UTF-8"))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    errorMsg = sb.toString();
                }
            }
            throw new IOException("HTTP " + responseCode + ": URL=" + url + " " +
                (errorMsg != null ? errorMsg : conn.getResponseMessage()));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ==================== 内部数据类 ====================

    /**
     * 包搜索结果
     */
    public static class PackageSearchResult {
        private int total;
        private int page;
        private int pageSize;
        private List<PackageItem> items;

        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }

        public List<PackageItem> getItems() { return items; }
        public void setItems(List<PackageItem> items) { this.items = items; }
    }

    /**
     * 包项
     */
    public static class PackageItem {
        private String groupId;
        private String artifactId;
        private String name;
        private String description;
        private String latestVersion;
        private List<String> versions;
        private String author;
        private String homepage;

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getLatestVersion() { return latestVersion; }
        public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

        public List<String> getVersions() { return versions; }
        public void setVersions(List<String> versions) { this.versions = versions; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getHomepage() { return homepage; }
        public void setHomepage(String homepage) { this.homepage = homepage; }
    }

    /**
     * 包详细信息
     */
    public static class PackageInfo {
        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        private String name;
        private String description;
        private String author;

        @JSONField(name = "home_page")
        private String homepage;

        private String license;
        private List<String> versions;
        private String latestVersion;

        /**
         * 依赖关系（按版本索引的 Map）
         * key: 版本号, value: 该版本的依赖列表
         * 注意：此字段不由 JSON 直接反序列化，而是从 releases 字段手动构建
         */
        private java.util.Map<String, List<DependencyItem>> dependencies;

        private String gitUrl;
        private int downloadCount;

        @JSONField(name = "requires_core")
        private String requiresCore;  // 对 ECAT Core 的版本要求

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getHomepage() { return homepage; }
        public void setHomepage(String homepage) { this.homepage = homepage; }

        public String getLicense() { return license; }
        public void setLicense(String license) { this.license = license; }

        public List<String> getVersions() { return versions; }
        public void setVersions(List<String> versions) { this.versions = versions; }

        public String getLatestVersion() { return latestVersion; }
        public void setLatestVersion(String latestVersion) { this.latestVersion = latestVersion; }

        public java.util.Map<String, List<DependencyItem>> getDependencies() { return dependencies; }
        public void setDependencies(java.util.Map<String, List<DependencyItem>> dependencies) { this.dependencies = dependencies; }

        public String getGitUrl() { return gitUrl; }
        public void setGitUrl(String gitUrl) { this.gitUrl = gitUrl; }

        public int getDownloadCount() { return downloadCount; }
        public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

        public String getRequiresCore() { return requiresCore; }
        public void setRequiresCore(String requiresCore) { this.requiresCore = requiresCore; }
    }

    /**
     * 依赖项
     */
    public static class DependencyItem {
        @JSONField(name = "group_id")
        private String groupId;

        @JSONField(name = "artifact_id")
        private String artifactId;

        @JSONField(name = "version_constraint")
        private String version;

        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }

        public String getArtifactId() { return artifactId; }
        public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        /**
         * 获取完整的 Maven 坐标
         */
        public String getCoordinate() {
            return groupId + ":" + artifactId;
        }
    }

    /**
     * 依赖关系图
     */
    public static class DependencyGraph {
        private List<Node> nodes;
        private List<Edge> edges;

        public List<Node> getNodes() { return nodes; }
        public void setNodes(List<Node> nodes) { this.nodes = nodes; }

        public List<Edge> getEdges() { return edges; }
        public void setEdges(List<Edge> edges) { this.edges = edges; }
    }

    /**
     * 图节点
     */
    public static class Node {
        private String id;
        private String version;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    /**
     * 图边
     */
    public static class Edge {
        private String from;
        private String to;
        private String version;

        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }

        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    /**
     * 发布信息（单个版本的发布数据）
     */
    public static class ReleaseInfo {
        @JSONField(name = "requires_core")
        private String requiresCore;

        @JSONField(name = "requires_dist")
        private List<DependencyItem> requiresDist;

        @JSONField(name = "upload_time")
        private String uploadTime;

        public String getRequiresCore() { return requiresCore; }
        public void setRequiresCore(String requiresCore) { this.requiresCore = requiresCore; }

        public List<DependencyItem> getRequiresDist() { return requiresDist; }
        public void setRequiresDist(List<DependencyItem> requiresDist) { this.requiresDist = requiresDist; }

        public String getUploadTime() { return uploadTime; }
        public void setUploadTime(String uploadTime) { this.uploadTime = uploadTime; }
    }

    /**
     * 包详情响应包装类
     */
    public static class PackageDetailResponse {
        private PackageInfo info;
        private java.util.Map<String, java.util.List<ReleaseInfo>> releases;

        public PackageInfo getInfo() { return info; }
        public void setInfo(PackageInfo info) { this.info = info; }

        public java.util.Map<String, java.util.List<ReleaseInfo>> getReleases() { return releases; }
        public void setReleases(java.util.Map<String, java.util.List<ReleaseInfo>> releases) { this.releases = releases; }
    }
}
