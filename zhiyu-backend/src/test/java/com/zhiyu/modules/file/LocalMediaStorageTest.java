package com.zhiyu.modules.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link LocalMediaStorage} key 校验（AU3：音频扩展名准入——曾因 key 正则不含
 * mp3/wav/m4a/ogg 导致音频上传在真实存储上必失败，H2 mock 测试覆盖不到）。
 */
class LocalMediaStorageTest {

    @TempDir
    Path dir;

    @Test
    void audioKeysAcceptedAndPathTraversalRejected() throws Exception {
        LocalMediaStorage storage = new LocalMediaStorage(dir.toString());
        for (String ext : new String[]{"mp3", "wav", "m4a", "ogg", "mp4", "jpg", "png", "webp"}) {
            String key = "a".repeat(32) + "." + ext;
            storage.put(key, new ByteArrayInputStream(new byte[]{1, 2, 3}));
            assertTrue(storage.read(key).exists(), ext + " 应可写入并读回");
            storage.remove(key);
        }
        // 非法 key（路径穿越 / 未知扩展名）一律拒绝
        assertThrows(IllegalArgumentException.class, () -> storage.read("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.read("a".repeat(32) + ".exe"));
    }
}
