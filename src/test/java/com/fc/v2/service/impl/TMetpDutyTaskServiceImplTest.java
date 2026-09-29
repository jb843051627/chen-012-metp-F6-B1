package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

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

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date d(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpDutyTask task(long id, String due, Integer leadDays, Integer status, Integer delFlag) {
        TMetpDutyTask t = new TMetpDutyTask();
        t.setId(id);
        t.setDueAt(due == null ? null : d(due));
        t.setAmount(leadDays == null ? null : new BigDecimal(leadDays));
        t.setStatus(status);
        t.setDelFlag(delFlag);
        return t;
    }

    private Date at() {
        return d("2026-09-20 12:00:00");
    }

    /** 窗口 [2,5]：距到期 1..5 天内、且提前量在 2..5 的待派条目才算到期；恰好到期也算 */
    @Test
    void listDue_windowAndBoundary() {
        TMetpDutyTask dueExactly = task(1, "2026-09-23 00:00:00", 3, 0, 0);   // 距 3 天 = 提前量
        TMetpDutyTask tooEarly   = task(2, "2026-09-30 00:00:00", 3, 0, 0);   // 距 10 天 > 3
        TMetpDutyTask leadBelow  = task(3, "2026-09-22 00:00:00", 1, 0, 0);   // 提前量 1 < 2
        TMetpDutyTask leadAbove  = task(4, "2026-09-25 00:00:00", 6, 0, 0);   // 提前量 6 > 5
        TMetpDutyTask already    = task(5, "2026-09-22 00:00:00", 3, 1, 0);   // 已派
        TMetpDutyTask removed    = task(6, "2026-09-22 00:00:00", 3, 0, 1);   // 已删
        TMetpDutyTask overdue    = task(7, "2026-09-19 00:00:00", 5, 0, 0);   // 已过期，仍在窗内可派
        TMetpDutyTask noDue      = task(8, null, 3, 0, 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(
                dueExactly, tooEarly, leadBelow, leadAbove, already, removed, overdue, noDue));

        java.util.List<TMetpDutyTask> due = service.listDue(at());
        assertEquals(2, due.size());
        assertEquals(1L, due.get(0).getId());
        assertEquals(7L, due.get(1).getId());
    }

    /** runOnce：逐条落账为已派，返回成功数；空窗不报错 */
    @Test
    void runOnce_marksDispatched_andEmptySafe() {
        when(mapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        assertEquals(0, service.runOnce(at()));
        verify(mapper, never()).updateById(any());

        TMetpDutyTask a = task(1, "2026-09-23 00:00:00", 3, 0, 0);
        TMetpDutyTask b = task(2, "2026-09-22 00:00:00", 5, 0, 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        when(mapper.updateById(any())).thenReturn(1);

        assertEquals(2, service.runOnce(at()));
        assertEquals(1, a.getStatus());
        assertEquals(1, b.getStatus());
    }

    /** 单条派不出去：挂失败后继续派下一条，成功数不含失败条 */
    @Test
    void runOnce_singleFailureSkipped() {
        TMetpDutyTask a = task(1, "2026-09-23 00:00:00", 3, 0, 0);
        TMetpDutyTask b = task(2, "2026-09-22 00:00:00", 5, 0, 0);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(a, b));
        when(mapper.updateById(a)).thenThrow(new RuntimeException("boom")).thenReturn(1);
        when(mapper.updateById(b)).thenReturn(1);

        assertEquals(1, service.runOnce(at()));
        assertEquals(2, a.getStatus()); // 派不出去
        assertEquals(1, b.getStatus()); // 已派
    }
}
