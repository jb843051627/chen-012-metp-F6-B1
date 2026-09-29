package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;

@ExtendWith(MockitoExtension.class)
class TMetpSubmitRowServiceImplTest {

    @Mock
    private TMetpSubmitRowMapper mapper;

    @InjectMocks
    private TMetpSubmitRowServiceImpl service;

    private TMetpSubmitRow row(String code, String qty) {
        TMetpSubmitRow r = new TMetpSubmitRow();
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void emptyBatchReturnsZeroAndTouchesNothing() {
        assertEquals(0, service.submitBatch("B0", Collections.<TMetpSubmitRow>emptyList()));
        verify(mapper, never()).insert(any());
    }

    @Test
    void oversizedBatchRejectedWholesale() {
        List<TMetpSubmitRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row("I" + i, "1"));
        }
        assertEquals(-1, service.submitBatch("BIG", rows));
        verify(mapper, never()).insert(any());
    }

    @Test
    void validRowsStoredAndInvalidRowsKeptWithOriginalRowNo() {
        when(mapper.selectCount(any())).thenReturn(0);
        TMetpSubmitRow ok1 = row("T", "1");
        ok1.setRowNo(1);
        TMetpSubmitRow bad = row(" ", "2");
        bad.setRowNo(7);
        TMetpSubmitRow ok2 = row("T", "3");
        ok2.setRowNo(9);

        int n = service.submitBatch("B1", new ArrayList<>(Arrays.asList(ok1, bad, ok2)));
        assertEquals(2, n);

        ArgumentCaptor<TMetpSubmitRow> captor = ArgumentCaptor.forClass(TMetpSubmitRow.class);
        verify(mapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        List<TMetpSubmitRow> stored = captor.getAllValues();
        assertEquals("B1", stored.get(0).getBatchNo());
        assertEquals(1, stored.get(0).getStatus());
        assertEquals(2, stored.get(1).getStatus());
        // 原始行号 7 保留，不得重排成 2
        assertEquals(7, stored.get(1).getRowNo());
        assertEquals(1, stored.get(2).getStatus());
        assertTrue(stored.stream().allMatch(r -> r.getDelFlag() == 0));
    }

    @Test
    void resubmitSameBatchIsIdempotent() {
        when(mapper.selectCount(any())).thenReturn(3);
        TMetpSubmitRow dup = row("T", "1");
        int n = service.submitBatch("B1", new ArrayList<>(Arrays.asList(dup)));
        assertEquals(3, n);
        verify(mapper, never()).insert(any());
    }
}
