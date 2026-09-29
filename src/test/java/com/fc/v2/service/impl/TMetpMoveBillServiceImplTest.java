package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
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

    private TMetpMoveBill bill(int node, int mode, Integer need, int signed, int status) {
        TMetpMoveBill r = new TMetpMoveBill();
        r.setId(1L);
        r.setNodeNo(node);
        r.setSignMode(mode);
        r.setNeedCount(need);
        r.setSignCount(signed);
        r.setStatus(status);
        return r;
    }

    @BeforeEach
    void setUp() {
        lenient().when(mapper.selectById(1L)).thenAnswer(inv -> current);
    }

    private TMetpMoveBill current;

    @Test
    void approveOrModeAdvancesOneNodeAndClearsSigns() {
        current = bill(0, 0, null, 5, 0);
        TMetpMoveBill r = service.approve(1L, "zhang", null);
        assertEquals(1, r.getNodeNo());
        assertEquals(0, r.getSignCount());
        assertEquals(0, r.getStatus());
    }

    @Test
    void approveAtLastNodePasses() {
        current = bill(3, 0, null, 0, 0);
        TMetpMoveBill r = service.approve(1L, "zhang", null);
        assertEquals(3, r.getNodeNo());
        assertEquals(1, r.getStatus());
    }

    @Test
    void andModeNeedsTwoSignaturesBeforeAdvancing() {
        current = bill(1, 1, 2, 0, 0);
        TMetpMoveBill first = service.approve(1L, "zhang", null);
        assertEquals(1, first.getNodeNo());
        assertEquals(1, first.getSignCount());
        assertEquals(0, first.getStatus());

        current = first;
        TMetpMoveBill second = service.approve(1L, "li", null);
        assertEquals(2, second.getNodeNo());
        assertEquals(0, second.getSignCount());
        assertEquals(0, second.getStatus());
    }

    @Test
    void andModeDefaultsNeedToTwoWhenNeedCountMissing() {
        current = bill(0, 1, null, 0, 0);
        TMetpMoveBill first = service.approve(1L, "zhang", null);
        assertEquals(0, first.getNodeNo());
        assertEquals(1, first.getSignCount());
    }

    @Test
    void rejectVetoesAndStopsFurtherApprove() {
        current = bill(1, 0, null, 0, 0);
        TMetpMoveBill rejected = service.reject(1L, "wang", null);
        assertEquals(2, rejected.getStatus());

        current = rejected;
        assertNull(service.approve(1L, "zhang", null));
        assertNull(service.rollback(1L, null));
    }

    @Test
    void rejectRejectsBlankApprover() {
        current = bill(0, 0, null, 0, 0);
        assertNull(service.reject(1L, "  ", null));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void rollbackGoesBackOneNodeAndWipesSigns() {
        current = bill(2, 1, 2, 2, 0);
        TMetpMoveBill r = service.rollback(1L, null);
        assertEquals(1, r.getNodeNo());
        assertEquals(0, r.getSignCount());
        assertEquals(0, r.getStatus());
    }

    @Test
    void rollbackAtFirstNodeRejected() {
        current = bill(0, 0, null, 0, 0);
        assertNull(service.rollback(1L, null));
    }

    @Test
    void approveMissingBillReturnsNull() {
        when(mapper.selectById(9L)).thenReturn(null);
        assertNull(service.approve(9L, "zhang", null));
    }
}
