# Maven 配置规范

相关文档：[项目结构与核心文件](project-structure.md) | [开发工作流程](workflow.md)

## Maven Assembly Plugin 配置

maven-assembly-plugin 是 ECAT 集成构建的核心组件，负责将集成打包成正确的结构并写入 I18n 系统识别信息。

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <!-- 指定assembly配置文件位置 -->
        <descriptors>
            <descriptor>src/main/assembly/fatjar.xml</descriptor>
        </descriptors>

        <!-- 避免在JAR文件名后添加assembly id -->
        <appendAssemblyId>false</appendAssemblyId>

        <!-- 关键：ECAT系统要求关闭压缩 -->
        <archiverConfig>
            <compress>false</compress>
        </archiverConfig>

        <!-- 关键：写入I18n系统识别信息 -->
        <archive>
            <manifestEntries>
                <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
            </manifestEntries>
        </archive>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>single</goal></goals>
        </execution>
    </executions>
</plugin>
```

---

## ecat-config.yml 配置

**ecat-config.yml** 定义了集成间的依赖关系，位于 `src/main/resources/`。

**配置格式**:
```yaml
# 此文件为ecat集成的配置文件，无此文件则下面均为无。
dependencies:
  - artifactId: integration-modbus
```

**配置示例**:
```yaml
# 多依赖集成
dependencies:
  - artifactId: integration-modbus
  - artifactId: integration-serial

# 无依赖集成
dependencies: []
```

**作用说明**:
- 系统会确保依赖集成先于当前集成加载
- 影响I18n资源的fallback查找顺序
- 定义集成的加载顺序和依赖关系

---

## Assembly 配置文件

**fatjar.xml** 位于 `src/main/assembly/`，定义JAR包的组装结构。新集成项目直接从现有集成复制即可。

```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.1.0 http://maven.apache.org/xsd/assembly-2.1.0.xsd">
  <id>fatjar</id>
  <formats>
    <format>jar</format>
  </formats>
  <includeBaseDirectory>false</includeBaseDirectory>
  <fileSets>
    <fileSet>
      <directory>${project.build.outputDirectory}</directory>
      <outputDirectory>/</outputDirectory>
    </fileSet>
  </fileSets>
  <dependencySets>
    <dependencySet>
      <outputDirectory>/lib</outputDirectory>
      <useProjectArtifact>false</useProjectArtifact>
      <unpack>false</unpack>
    </dependencySet>
  </dependencySets>
</assembly>
```

**配置说明**:
- **formats**: 打包格式为JAR
- **includeBaseDirectory=false**: 不包含基础目录
- **fileSets**: 包含项目编译输出到根目录
- **dependencySets**: 依赖JAR包放在/lib目录下

---

## pom.xml 完整示例

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ecat</groupId>
        <artifactId>integrations</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>integration-{name}</artifactId>
    <version>1.0.0</version>
    <name>integration-{name}</name>

    <dependencies>
        <dependency>
            <groupId>com.ecat</groupId>
            <artifactId>integration-modbus</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.6.0</version>
                <configuration>
                    <descriptors>
                        <descriptor>src/main/assembly/fatjar.xml</descriptor>
                    </descriptors>
                    <appendAssemblyId>false</appendAssemblyId>
                    <archiverConfig>
                        <compress>false</compress>
                    </archiverConfig>
                    <archive>
                        <manifestEntries>
                            <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
                        </manifestEntries>
                    </archive>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>single</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
