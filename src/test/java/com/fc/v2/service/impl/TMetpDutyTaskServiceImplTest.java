package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpDutyTaskMapper;
import com.fc.v2.model.auto.TMetpDutyTask;

@ExtendWith(MockitoExtension.class)
class TMetpDutyTaskServiceImplTest {

    @Mock
    private TMetpDutyTaskMapper mapper;

    @InjectMocks
    private TMetpDutyTaskServiceImpl service;

    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Date t(String s) {
        try {
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpDutyTask task(String itemNo, int delFlag, int status, String dueAt, String amount) {
        TMetpDutyTask r = new TMetpDutyTask();
        r.setItemNo(itemNo);
        r.setDelFlag(delFlag);
        r.setStatus(status);
        if (dueAt != null) {
            r.setDueAt(t(dueAt));
        }
        r.setAmount(amount == null ? null : new BigDecimal(amount));
        return r;
    }

    @Test
    void listDueIncludesExactlyDueAndWindowDaysAndExcludesOthers() {
        TMetpDutyTask edge = task("EDGE", 0, 0, "2026-09-29 00:00:00", "3");
        TMetpDutyTask from = task("FROM", 0, 0, "2026-09-28 00:00:00", "2");
        TMetpDutyTask to = task("TO", 0, 0, "2026-09-27 00:00:00", "5");
        TMetpDutyTask notYet = task("FUTURE", 0, 0, "2026-09-30 00:00:00", "3");
        TMetpDutyTask outWin = task("OUTWIN", 0, 0, "2026-09-20 00:00:00", "6");
        TMetpDutyTask noAmount = task("NOAMT", 0, 0, "2026-09-29 00:00:00", null);
        TMetpDutyTask done = task("DONE", 0, 1, "2026-09-29 00:00:00", "3");
        TMetpDutyTask deleted = task("DEL", 1, 0, "2026-09-29 00:00:00", "3");
        when(mapper.selectList(any()))
                .thenReturn(Arrays.asList(edge, from, to, notYet, outWin, noAmount, done, deleted));

        List<TMetpDutyTask> due = service.listDue(t("2026-09-29 12:00:00"));
        assertEquals(3, due.size());
        assertEquals(Arrays.asList("EDGE", "FROM", "TO"), Arrays.asList(
                due.get(0).getItemNo(), due.get(1).getItemNo(), due.get(2).getItemNo()));
    }

    @Test
    void runOnceOnEmptyListReturnsZero() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.runOnce(t("2026-09-29 12:00:00")));
    }

    @Test
    void runOnceMarksAllDueRowsDone() {
        TMetpDutyTask ok = task("OK", 0, 0, "2026-09-29 00:00:00", "3");
        TMetpDutyTask ok2 = task("OK2", 0, 0, "2026-09-29 00:00:00", "5");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(ok, ok2));

        int n = service.runOnce(t("2026-09-29 12:00:00"));
        assertEquals(2, n);
        assertEquals(1, ok.getStatus());
        assertEquals(1, ok2.getStatus());
        verify(mapper, times(2)).updateById(any());
    }

    @Test
    void runOnceKeepsGoingWhenOneRowThrows() {
        TMetpDutyTask ok = task("OK", 0, 0, "2026-09-29 00:00:00", "3");
        TMetpDutyTask boom = task("BOOM", 0, 0, "2026-09-29 00:00:00", "4");
        TMetpDutyTask ok2 = task("OK2", 0, 0, "2026-09-29 00:00:00", "5");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(ok, boom, ok2));
        // 循环会先以 ok 调用 updateById，严格桩会把“参数不匹配”误判为误用，
        // 故对“中间一条落库抛错”用 lenient 打桩
        lenient().when(mapper.updateById(boom)).thenThrow(new RuntimeException("db down"));

        int n = service.runOnce(t("2026-09-29 12:00:00"));
        assertEquals(2, n);
        assertEquals(1, ok.getStatus());
        assertEquals(1, ok2.getStatus());
        verify(mapper, times(3)).updateById(any());
    }

    @Test
    void listDueNullAtReturnsEmpty() {
        assertEquals(0, service.listDue(null).size());
        verify(mapper, never()).selectList(any());
    }
}
