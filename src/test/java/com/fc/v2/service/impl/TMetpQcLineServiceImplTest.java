package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;

@ExtendWith(MockitoExtension.class)
class TMetpQcLineServiceImplTest {

    @Mock
    private TMetpQcLineMapper mapper;

    @InjectMocks
    private TMetpQcLineServiceImpl service;

    private final SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Date t(String s) {
        try {
            return f.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpQcLine rule(String code, Integer status, String start, String end, Integer priority,
                            String th1, String th2, String th3) {
        TMetpQcLine r = new TMetpQcLine();
        r.setId(1L);
        r.setRuleCode(code);
        r.setStatus(status);
        if (start != null) {
            r.setEffStart(t(start));
        }
        if (end != null) {
            r.setEffEnd(t(end));
        }
        r.setPriority(priority);
        r.setTh1Max(th1 == null ? null : new BigDecimal(th1));
        r.setTh2Max(th2 == null ? null : new BigDecimal(th2));
        r.setTh3Max(th3 == null ? null : new BigDecimal(th3));
        return r;
    }

    @Test
    void listAvailableFiltersByHalfOpenWindowAndStatusAndSortsByPriority() {
        TMetpQcLine disabled = rule("R1", 1, null, null, 9, "10", "50", "90");
        TMetpQcLine future = rule("R2", 0, "2026-10-01 00:00:00", null, 9, "10", "50", "90");
        TMetpQcLine ended = rule("R3", 0, null, "2026-09-01 00:00:00", 9, "10", "50", "90");
        TMetpQcLine low = rule("R4", 0, null, null, 1, "10", "50", "90");
        TMetpQcLine high = rule("R5", 0, "2026-09-01 00:00:00", "2026-10-01 00:00:00", 5, "10", "50", "90");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(disabled, future, ended, low, high));

        List<TMetpQcLine> avail = service.listAvailable(t("2026-09-29 12:00:00"));
        assertEquals(2, avail.size());
        assertEquals("R5", avail.get(0).getRuleCode());
        assertEquals("R4", avail.get(1).getRuleCode());
        assertEquals(2, service.countAvailable(t("2026-09-29 12:00:00")));

        // 交棒之日（不含）当天 R5 已失效，future 规则 R2 届时生效
        List<TMetpQcLine> atEndDay = service.listAvailable(t("2026-10-01 00:00:00"));
        assertEquals(2, atEndDay.size());
        assertEquals("R2", atEndDay.get(0).getRuleCode());
        assertEquals("R4", atEndDay.get(1).getRuleCode());
    }

    @Test
    void usableHonorsWindow() {
        TMetpQcLine r = rule("R5", 0, "2026-09-01 00:00:00", "2026-10-01 00:00:00", 5, "10", "50", "90");
        when(mapper.selectById(1L)).thenReturn(r);
        assertTrue(service.usable(1L, t("2026-09-01 00:00:00")));
        assertFalse(service.usable(1L, t("2026-10-01 00:00:00")));
    }

    @Test
    void evaluatePicksLatestStartedVersionAndBandsEqualUp() {
        TMetpQcLine oldV = rule("TMP", 0, "2026-01-01 00:00:00", "2026-09-01 00:00:00", 1, "10", "50", "90");
        TMetpQcLine newV = rule("TMP", 0, "2026-09-01 00:00:00", null, 1, "20", "60", "95");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(oldV, newV));

        // 等于一档上限算高二档
        assertEquals(2, service.evaluate("TMP", new BigDecimal("20"), t("2026-09-29 00:00:00")));
        assertEquals(4, service.evaluate("TMP", new BigDecimal("96"), t("2026-09-29 00:00:00")));
        // 旧版本窗口外：新版本起点当日（含）前走旧版
        assertEquals(2, service.evaluate("TMP", new BigDecimal("10"), t("2026-08-31 23:59:59")));
    }

    @Test
    void evaluateRejectsOutOfRangeOrMissingRule() {
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        assertEquals(0, service.evaluate("X", new BigDecimal("-1"), t("2026-09-29 00:00:00")));
        assertEquals(0, service.evaluate("X", new BigDecimal("101"), t("2026-09-29 00:00:00")));
        assertEquals(0, service.evaluate("X", new BigDecimal("5"), t("2026-09-29 00:00:00")));
        assertEquals(0, service.evaluateTop(new BigDecimal("5"), t("2026-09-29 00:00:00")));
    }

    @Test
    void evaluateTopUsesHighestPriorityRule() {
        TMetpQcLine low = rule("L", 0, null, null, 1, "10", "50", "90");
        TMetpQcLine high = rule("H", 0, null, null, 9, "30", "60", "90");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(low, high));
        // 20 在 low 判为二档、在 high 判为一档，取优先级最高的 high
        assertEquals(1, service.evaluateTop(new BigDecimal("20"), t("2026-09-29 00:00:00")));
    }
}
