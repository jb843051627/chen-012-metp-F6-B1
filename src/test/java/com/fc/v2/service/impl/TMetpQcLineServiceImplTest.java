package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;

@ExtendWith(MockitoExtension.class)
class TMetpQcLineServiceImplTest {

    @Mock
    private TMetpQcLineMapper mapper;

    @InjectMocks
    private TMetpQcLineServiceImpl service;

    private static final SimpleDateFormat F = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static Date d(String s) {
        try {
            return F.parse(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TMetpQcLine rule(long id, String code, Integer pri, Integer status,
                            String start, String end, String t1, String t2, String t3) {
        TMetpQcLine r = new TMetpQcLine();
        r.setId(id);
        r.setRuleCode(code);
        r.setPriority(pri);
        r.setStatus(status);
        r.setDelFlag(0);
        r.setEffStart(start == null ? null : d(start));
        r.setEffEnd(end == null ? null : d(end));
        r.setTh1Max(t1 == null ? null : new BigDecimal(t1));
        r.setTh2Max(t2 == null ? null : new BigDecimal(t2));
        r.setTh3Max(t3 == null ? null : new BigDecimal(t3));
        return r;
    }

    /** 生效窗口：启用日之前不可用；交棒日当天（不含）已不可用 */
    @Test
    void usable_respectsEffectiveWindow() {
        TMetpQcLine r = rule(1L, "R", 0, 0, "2026-01-01 00:00:00", "2026-06-01 00:00:00", "10", "20", "30");
        when(mapper.selectById(1L)).thenReturn(r);
        assertFalse(service.usable(1L, d("2025-12-31 23:59:59")));
        assertTrue(service.usable(1L, d("2026-01-01 00:00:00")));
        assertTrue(service.usable(1L, d("2026-05-31 23:59:59")));
        assertFalse(service.usable(1L, d("2026-06-01 00:00:00")));
    }

    /** 停用 / 删除的规则不可用 */
    @Test
    void usable_stoppedOrDeleted_unusable() {
        when(mapper.selectById(2L)).thenReturn(rule(2L, "R", 0, 1, null, null, "10", "20", "30"));
        assertFalse(service.usable(2L, d("2026-06-01 00:00:00")));
        TMetpQcLine deleted = rule(3L, "R", 0, 0, null, null, "10", "20", "30");
        deleted.setDelFlag(1);
        when(mapper.selectById(3L)).thenReturn(deleted);
        assertFalse(service.usable(3L, d("2026-06-01 00:00:00")));
    }

    /** evaluate 只用该时刻生效的版本；等于上限算高一档 */
    @Test
    void evaluate_picksVersionEffectiveAtTime() {
        TMetpQcLine oldV = rule(1L, "R", 0, 0, "2025-01-01 00:00:00", "2026-01-01 00:00:00", "1", "2", "3");
        TMetpQcLine newV = rule(2L, "R", 0, 0, "2026-01-01 00:00:00", null, "10", "20", "30");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(oldV, newV));

        assertEquals(4, service.evaluate("R", new BigDecimal("25"), d("2025-06-01 00:00:00")));
        assertEquals(3, service.evaluate("R", new BigDecimal("20"), d("2026-01-01 00:00:00")));
        // 旧版阈值 1/2/3：20 已超三档封顶，归 4 档，不会错用新版阈值
        assertEquals(4, service.evaluate("R", new BigDecimal("20"), d("2025-06-01 00:00:00")));
        // 交棒日当天（不含）旧版已失效；此刻正好启用新版
        assertEquals(4, service.evaluate("R", new BigDecimal("35"), d("2026-01-01 00:00:00")));
    }

    /** evaluateTop：取生效规则中优先级最高者判档；入参越界返 0 */
    @Test
    void evaluateTop_highestPriorityAndRange() {
        TMetpQcLine low = rule(1L, "A", 1, 0, null, null, "10", "20", "30");
        TMetpQcLine high = rule(2L, "B", 9, 0, null, null, "50", "60", "70");
        TMetpQcLine stopped = rule(3L, "C", 99, 1, null, null, "1", "2", "3");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(low, high, stopped));

        assertEquals(1, service.evaluateTop(new BigDecimal("10"), d("2026-06-01 00:00:00"))); // high: 10<=50 ->1
        assertEquals(0, service.evaluateTop(new BigDecimal("-1"), d("2026-06-01 00:00:00")));
        assertEquals(0, service.evaluateTop(new BigDecimal("100.01"), d("2026-06-01 00:00:00")));
    }

    /** countAvailable 只数生效+现行+未删的规则 */
    @Test
    void countAvailable_filtersWindowAndStatus() {
        TMetpQcLine inWin = rule(1L, "A", 1, 0, "2026-01-01 00:00:00", "2027-01-01 00:00:00", "10", "20", "30");
        TMetpQcLine notYet = rule(2L, "B", 1, 0, "2027-01-01 00:00:00", null, "10", "20", "30");
        TMetpQcLine stopped = rule(3L, "C", 1, 1, null, null, "10", "20", "30");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(inWin, notYet, stopped));
        assertEquals(1, service.countAvailable(d("2026-06-01 00:00:00")));
    }
}
