package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private TMetpSubmitRow row(Integer rowNo, String code, String qty) {
        TMetpSubmitRow r = new TMetpSubmitRow();
        r.setRowNo(rowNo);
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    @Test
    void nullOrEmpty_returnsZero_noInsert() {
        assertEquals(0, service.submitBatch("B", null));
        assertEquals(0, service.submitBatch("B", new ArrayList<>()));
        verify(mapper, never()).insert(any());
    }

    /** 超出单批上限：-1 且整批不入库 */
    @Test
    void overLimit_returnsMinusOne_noInsert() {
        List<TMetpSubmitRow> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(row(i, "I", "1"));
        }
        assertEquals(-1, service.submitBatch("B", rows));
        verify(mapper, never()).insert(any());
        verify(mapper, never()).selectCount(any());
    }

    /** 一条不过：只挂驳回落库（保留原行次），合法行也不得转存 */
    @Test
    void oneInvalid_onlyErrorPersisted() {
        TMetpSubmitRow good = row(1, "A", "10");
        TMetpSubmitRow bad = row(2, "B", null);
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);

        int n = service.submitBatch("B7", java.util.Arrays.asList(good, bad));
        assertEquals(0, n);
        assertEquals(2, bad.getStatus());
        assertEquals(2, bad.getRowNo());
        assertEquals("B7", bad.getBatchNo());
        verify(mapper, times(1)).insert(bad);
        verify(mapper, never()).insert(good);
    }

    /** 全部合法：册号统一回写入参，整批转存 */
    @Test
    void allValid_allStoredWithParamBatchNo() {
        TMetpSubmitRow a = row(1, "A", "10");
        TMetpSubmitRow b = row(2, "B", "20");
        b.setBatchNo("OLD");
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.insert(any())).thenReturn(1);

        int n = service.submitBatch("B8", java.util.Arrays.asList(a, b));
        assertEquals(2, n);
        assertEquals(1, a.getStatus());
        assertEquals(1, b.getStatus());
        assertEquals("B8", a.getBatchNo());
        assertEquals("B8", b.getBatchNo());
    }

    /** 已成功转存过的批次不得重复入库 */
    @Test
    void duplicateBatch_rejected() {
        when(mapper.selectCount(any())).thenReturn(3);
        assertEquals(0, service.submitBatch("B9", java.util.Arrays.asList(row(1, "A", "1"))));
        verify(mapper, never()).insert(any());
    }

    /** 上回仅挂驳回：软清旧驳回明细后本次可正常转存 */
    @Test
    void resubmitAfterFailure_clearsOldErrorsAndStores() {
        when(mapper.selectCount(any())).thenReturn(0);
        when(mapper.update(any(), any())).thenReturn(1);
        when(mapper.insert(any())).thenReturn(1);

        int n = service.submitBatch("B10", java.util.Arrays.asList(row(1, "A", "1")));
        assertEquals(1, n);
        verify(mapper, times(1)).update(any(), any());
    }
}
