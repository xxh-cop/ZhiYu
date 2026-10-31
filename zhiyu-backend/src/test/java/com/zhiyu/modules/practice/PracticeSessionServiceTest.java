package com.zhiyu.modules.practice;

import com.zhiyu.common.exception.BusinessException;
import com.zhiyu.modules.practice.dto.CreatePracticeSessionDTO;
import com.zhiyu.modules.practice.entity.PracticeSession;
import com.zhiyu.modules.practice.mapper.PracticeSessionMapper;
import com.zhiyu.modules.practice.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionServiceTest {

    private PracticeSessionMapper mapper;
    private PracticeSessionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PracticeSessionMapper.class);
        service = new PracticeSessionService(mapper);
    }

    private CreatePracticeSessionDTO request() {
        CreatePracticeSessionDTO request = new CreatePracticeSessionDTO();
        request.setKind("heritage");
        request.setMoodCode("tired");
        request.setMinutesBudget(10);
        request.setHeritagePatternId("971201");
        return request;
    }

    @Test
    void createsCreatedSessionWithNormalizedKind() {
        when(mapper.insert(any(PracticeSession.class))).thenAnswer(invocation -> {
            PracticeSession session = invocation.getArgument(0);
            session.setId(99L);
            return 1;
        });

        var created = service.create(7L, request());

        assertEquals(99L, created.getPracticeSessionId());
        assertEquals("HERITAGE", created.getKind());
        assertEquals(0, created.getStatus());
        assertEquals("已创建", created.getStatusLabel());
        assertEquals(10, created.getMinutesBudget());
        assertEquals(1, created.getCurrentStep());
        assertEquals("971201", created.getHeritagePatternId());
        verify(mapper).insert(any(PracticeSession.class));
    }

    @Test
    void rejectsUnknownKindAndInvalidMinutes() {
        var badKind = request();
        badKind.setKind("UNKNOWN");
        var kindError = assertThrows(BusinessException.class, () -> service.create(7L, badKind));
        assertEquals(1001, kindError.getCode());

        var badMinutes = request();
        badMinutes.setMinutesBudget(0);
        var minutesError = assertThrows(BusinessException.class, () -> service.create(7L, badMinutes));
        assertEquals(1001, minutesError.getCode());

        verify(mapper, never()).insert(any(PracticeSession.class));
    }

    @Test
    void readsOnlyOwnedSession() {
        PracticeSession session = session(7L, 0);
        when(mapper.selectById(99L)).thenReturn(session);
        assertEquals(99L, service.get(7L, 99L).getPracticeSessionId());

        var forbidden = assertThrows(BusinessException.class, () -> service.get(8L, 99L));
        assertEquals(1003, forbidden.getCode());

        when(mapper.selectById(100L)).thenReturn(null);
        var missing = assertThrows(BusinessException.class, () -> service.get(7L, 100L));
        assertEquals(1004, missing.getCode());
    }

    @Test
    void startPauseResumeCompleteAndSaveArtifactFollowStateMachine() {
        PracticeSession session = session(7L, 0);
        when(mapper.selectById(99L)).thenReturn(session);

        var started = service.transition(7L, 99L, "START");
        assertEquals(3, started.getStatus());
        assertNotNull(started.getStartedAt());
        assertNull(started.getCompletedAt());

        var paused = service.transition(7L, 99L, "PAUSE");
        assertEquals(4, paused.getStatus());

        var resumed = service.transition(7L, 99L, "RESUME");
        assertEquals(3, resumed.getStatus());

        var completed = service.transition(7L, 99L, "COMPLETE");
        assertEquals(5, completed.getStatus());
        assertNotNull(completed.getCompletedAt());

        var saved = service.transition(7L, 99L, "SAVE_ARTIFACT");
        assertEquals(6, saved.getStatus());
        verify(mapper, org.mockito.Mockito.times(5)).updateById(any(PracticeSession.class));
    }

    @Test
    void listsOwnRecentSessionsWithClampedLimit() {
        when(mapper.selectList(any())).thenReturn(List.of(session(7L, 3), session(7L, 5)));

        var list = service.list(7L, 50);

        assertEquals(2, list.size());
        assertEquals(3, list.get(0).getStatus());
        verify(mapper).selectList(any());
    }

    @Test
    void rejectsIllegalTransition() {
        when(mapper.selectById(99L)).thenReturn(session(7L, 0));
        var error = assertThrows(BusinessException.class, () -> service.transition(7L, 99L, "COMPLETE"));
        assertEquals(3001, error.getCode());
        verify(mapper, never()).updateById(any(PracticeSession.class));
    }

    @Test
    void abandonIsTerminalButCanResumePractice() {
        PracticeSession session = session(7L, 3);
        when(mapper.selectById(99L)).thenReturn(session);

        var abandoned = service.transition(7L, 99L, "ABANDON");
        assertEquals(7, abandoned.getStatus());
        assertNotNull(abandoned.getCompletedAt());

        var resumed = service.transition(7L, 99L, "START");
        assertEquals(3, resumed.getStatus());
        assertNull(resumed.getCompletedAt());
    }

    private PracticeSession session(Long userId, int status) {
        PracticeSession session = new PracticeSession();
        session.setId(99L);
        session.setUserId(userId);
        session.setKind("HERITAGE");
        session.setStatus(status);
        session.setMinutesBudget(10);
        session.setCurrentStep(1);
        return session;
    }
}
