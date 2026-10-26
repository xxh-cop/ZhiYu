package com.zhiyu.modules;

import com.zhiyu.modules.file.FileCleanupWorker;
import com.zhiyu.modules.file.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileCleanupWorkerTest {
    @Test void failedDeletionStaysPendingAndNextRunCompletes() throws Exception {
        JdbcTemplate db = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of(
                "id", 17L, "storage_kind", "PRIVATE", "storage_key", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.mp4")));
        doThrow(new IOException("temporarily unavailable")).doNothing().when(storage).remove(anyString());
        FileCleanupWorker worker = new FileCleanupWorker(db, storage);
        worker.retry();
        verify(db).update("UPDATE file_cleanup_job SET attempts=attempts+1,update_time=CURRENT_TIMESTAMP WHERE id=?", 17L);
        verify(db, never()).update(contains("state='DONE'"), eq(17L));
        worker.retry();
        verify(db).update("UPDATE file_cleanup_job SET state='DONE',attempts=attempts+1,update_time=CURRENT_TIMESTAMP WHERE id=?", 17L);
        verify(storage, times(2)).remove("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.mp4");
    }

    @Test void unsafePublicPathNeverReachesStorage() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        MediaStorage storage = mock(MediaStorage.class);
        when(db.queryForList(anyString())).thenReturn(List.of(Map.of(
                "id", 18L, "storage_kind", "PUBLIC", "storage_key", "/uploads/../../outside.png")));
        new FileCleanupWorker(db, storage).retry();
        verifyNoInteractions(storage);
        verify(db, never()).update(contains("state='DONE'"), eq(18L));
        verify(db).update("UPDATE file_cleanup_job SET attempts=attempts+1,update_time=CURRENT_TIMESTAMP WHERE id=?", 18L);
    }
}
