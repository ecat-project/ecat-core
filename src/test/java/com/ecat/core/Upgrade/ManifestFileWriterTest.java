/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSON;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * manifest 原子落盘器单测(§7 T9):写读一致、覆盖写、零 tmp 残留、
 * 失败传播+残留清理。临时目录直驱,同分区真实文件系统行为(不 mock 文件系统;
 * ATOMIC_MOVE 降级分支在常规文件系统不可自然触发,该分支语义由 T-2-3 契约
 * 同款实现与既有 YamlAtomicFileWriter 用例族背书)。
 *
 * @author coffee
 */
public class ManifestFileWriterTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    /** 写→读回一致;tmp 零残留;null 键位保留(WriteMapNullValue,两产源键位同形) */
    @Test
    public void writeThenReadBackIdenticalNoTmpResidue() throws IOException {
        Path target = temp.getRoot().toPath().resolve("plans").resolve("p1").resolve("manifest.json");
        UpgradeManifest manifest = manifestOf("first-content");

        ManifestFileWriter.write(manifest, target);

        UpgradeManifest readBack = JSON.parseObject(
                Files.readAllBytes(target), UpgradeManifest.class);
        assertEquals("first-content", readBack.getPlanId());
        assertEquals(manifest.getCoreVersion(), readBack.getCoreVersion());
        assertEquals(manifest.getSource(), readBack.getSource());
        assertTrue("落盘为 PrettyFormat 人可读 JSON",
                new String(Files.readAllBytes(target), "UTF-8").contains("\n"));
        assertEquals("零 tmp 残留", 0, tmpFilesOf(target.getParent()).size());
    }

    /** 语义性 null 键位落盘保形:新装坐标 installed_version=null 不因缺省序列化丢键 */
    @Test
    public void nullKeysPreservedForBothProvenanceShapes() throws IOException {
        Path target = temp.getRoot().toPath().resolve("manifest.json");
        UpgradeManifest manifest = manifestOf("plan-null-keys");
        UpgradeManifest.UpgradeItem item = new UpgradeManifest.UpgradeItem();
        item.setGroupId("com.ecat");
        item.setArtifactId("fresh-install");
        item.setTargetVersion("4.0.0");
        manifest.getItems().add(item);

        ManifestFileWriter.write(manifest, target);

        String json = new String(Files.readAllBytes(target), "UTF-8");
        assertTrue("installed_version=null 键位须保留: " + json,
                json.contains("\"installed_version\":null"));
        UpgradeManifest readBack = JSON.parseObject(json, UpgradeManifest.class);
        assertEquals(null, readBack.getItems().get(0).getInstalledVersion());
        assertEquals("UPGRADE", readBack.getType());
    }

    /** 二次覆盖写→内容为第二次(同 planId 重试=原子替换覆盖) */
    @Test
    public void secondWriteOverwritesAtomically() throws IOException {
        Path target = temp.getRoot().toPath().resolve("manifest.json");

        ManifestFileWriter.write(manifestOf("first"), target);
        ManifestFileWriter.write(manifestOf("second"), target);

        UpgradeManifest readBack = JSON.parseObject(
                Files.readAllBytes(target), UpgradeManifest.class);
        assertEquals("second", readBack.getPlanId());
        assertEquals("覆盖写后零 tmp 残留", 0, tmpFilesOf(target.getParent()).size());
    }

    /** 目标为非空目录(move 不可达)→IOException 传播+tmp 残留被清理+目标目录原样 */
    @Test
    public void writeFailurePropagatesAndCleansTmp() throws IOException {
        Path occupied = temp.getRoot().toPath().resolve("occupied");
        Files.createDirectories(occupied);
        Files.write(occupied.resolve("keep.txt"), "keep".getBytes("UTF-8"));

        // 目标路径=occupied(非空目录):createTempFile 同目录成功、move 到非空目录必败
        Path target = occupied;
        assertThrows(IOException.class, () -> ManifestFileWriter.write(manifestOf("x"), target));

        assertTrue("失败不动目标既有形态", Files.isDirectory(occupied));
        assertTrue("目标子内容原样", Files.isRegularFile(occupied.resolve("keep.txt")));
        assertEquals("失败后 tmp 残留被清理", 0, tmpFilesOf(occupied).size());
    }

    private static UpgradeManifest manifestOf(String planId) {
        UpgradeManifest manifest = new UpgradeManifest();
        manifest.setPlanId(planId);
        manifest.setCreatedAt("2026-10-04T08:30:00Z");
        manifest.setCoreVersion("4.0.0");
        manifest.setSource(UpgradeManifestVerifier.SOURCE_RESOLVE);
        return manifest;
    }

    private static List<Path> tmpFilesOf(Path dir) {
        if (!Files.isDirectory(dir)) {
            fail("目录不存在: " + dir);
        }
        try (Stream<Path> walked = Files.walk(dir, 1)) {
            return walked.filter(p -> p.getFileName().toString().endsWith(".tmp"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            fail("目录遍历失败: " + e.getMessage());
            return null;
        }
    }
}
