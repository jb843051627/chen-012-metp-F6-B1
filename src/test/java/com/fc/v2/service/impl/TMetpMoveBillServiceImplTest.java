package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpMoveBillMapper;
import com.fc.v2.model.auto.TMetpMoveBill;

@ExtendWith(MockitoExtension.class)
class TMetpMoveBillServiceImplTest {

    @Mock
    private TMetpMoveBillMapper mapper;

    @InjectMocks
    private TMetpMoveBillServiceImpl service;

    private TMetpMoveBill bill(int node, Integer mode, Integer need, Integer signed, int status) {
        TMetpMoveBill r = new TMetpMoveBill();
        r.setId(1L);
        r.setBillNo("B1");
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        return r;
    }

    /** 任一人模式：一票即升入下一层；两层签过才核讫 */
    @Test
    void orMode_advancesNodeOnSingleSign() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        TMetpMoveBill first = service.approve(1L, "zhang", "ok");
        assertNotNull(first);
        assertEquals(1, first.getNodeNo());
        assertEquals(0, first.getSignCount());
        assertEquals(0, first.getStatus()); // 第一层出来仍是在核

        when(mapper.selectById(1L)).thenReturn(bill(1, 0, null, 0, 0));
        TMetpMoveBill done = service.approve(1L, "zhang", "ok");
        assertEquals(2, done.getNodeNo());
        assertEquals(1, done.getStatus()); // 走出末层才核讫
    }

    /** 两名点齐：第一票留在本层，第二票才升层；两层皆如此 */
    @Test
    void andMode_requiresBothSignatures() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, null, 0, 0));
        TMetpMoveBill first = service.approve(1L, "zhang", "ok");
        assertEquals(0, first.getNodeNo());
        assertEquals(1, first.getSignCount());
        assertEquals(0, first.getStatus());

        when(mapper.selectById(1L)).thenReturn(bill(0, 1, null, 1, 0));
        TMetpMoveBill second = service.approve(1L, "wang", "ok");
        assertEquals(1, second.getNodeNo());
        assertEquals(0, second.getSignCount());
        assertEquals(0, second.getStatus());

        // 第二层同样要两名点齐
        when(mapper.selectById(1L)).thenReturn(bill(1, 1, null, 0, 0));
        TMetpMoveBill third = service.approve(1L, "zhang", "ok");
        assertEquals(1, third.getNodeNo());
        assertEquals(1, third.getSignCount());
        when(mapper.selectById(1L)).thenReturn(bill(1, 1, null, 1, 0));
        TMetpMoveBill fourth = service.approve(1L, "wang", "ok");
        assertEquals(2, fourth.getNodeNo());
        assertEquals(1, fourth.getStatus());
    }

    /** 应签人数以 needCount 为准：三人缺一不升层，点齐才升 */
    @Test
    void needCountThree_staysUntilAllSigned() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 3, 1, 0));
        TMetpMoveBill r = service.approve(1L, "li", "ok");
        assertEquals(0, r.getNodeNo());
        assertEquals(2, r.getSignCount());
        assertEquals(0, r.getStatus());

        when(mapper.selectById(1L)).thenReturn(bill(0, 1, 3, 3, 0));
        TMetpMoveBill done = service.approve(1L, "zhao", "ok");
        assertEquals(1, done.getNodeNo());
        assertEquals(0, done.getSignCount());
    }

    /** 否决：落打回态，层不前进；已打回的不得再签 */
    @Test
    void reject_setsVeto_andBlocksFlow() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        TMetpMoveBill r = service.reject(1L, "zhang", "no");
        assertEquals(2, r.getStatus());
        assertEquals(0, r.getNodeNo());

        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 2));
        assertNull(service.approve(1L, "zhang", "ok"));
        assertNull(service.reject(1L, "zhang", "no"));
        verify(mapper, org.mockito.Mockito.atMost(1)).updateById(any());
    }

    /** 无签批人视为无效签批 */
    @Test
    void approve_blankApprover_rejected() {
        when(mapper.selectById(1L)).thenReturn(bill(0, 0, null, 0, 0));
        assertNull(service.approve(1L, "  ", "ok"));
        verify(mapper, never()).updateById(any());
    }

    /** 退回一挡：层减一，本层已点的签一并抹掉 */
    @Test
    void rollback_oneNodeBack_andClearsSigns() {
        when(mapper.selectById(1L)).thenReturn(bill(1, 1, 2, 2, 0));
        TMetpMoveBill r = service.rollback(1L, "back");
        assertEquals(0, r.getNodeNo());
        assertEquals(0, r.getSignCount());
        assertEquals(0, r.getStatus());
    }
}
