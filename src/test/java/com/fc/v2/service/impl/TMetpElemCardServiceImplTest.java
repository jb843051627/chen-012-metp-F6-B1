package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TMetpElemCardMapper;
import com.fc.v2.mapper.auto.TMetpStnDocMapper;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.model.auto.TMetpStnDoc;

@ExtendWith(MockitoExtension.class)
class TMetpElemCardServiceImplTest {

    @Mock
    private TMetpElemCardMapper baseMapper;
    @Mock
    private TMetpStnDocMapper metpStnDocMapper;

    @InjectMocks
    private TMetpElemCardServiceImpl service;

    private TMetpStnDoc arch(long id, int status) {
        TMetpStnDoc a = new TMetpStnDoc();
        a.setId(id);
        a.setSiteNo("TQ00");
        a.setStatus(status);
        a.setTh1Max(new BigDecimal("12"));
        a.setTh2Max(new BigDecimal("40"));
        a.setTh3Max(new BigDecimal("120"));
        a.setDelFlag(0);
        return a;
    }

    private TMetpElemCard card(long siteId, String billNo, String qty) {
        TMetpElemCard r = new TMetpElemCard();
        r.setSiteId(siteId);
        r.setBillNo(billNo);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        return r;
    }

    /** 等于上限算高一档：12→2、40→3、120→4、11→1、121→4 */
    @Test
    void insert_gradeBoundaries_equalUpperGoesHigher() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0L, 0));
        when(baseMapper.selectCount(any())).thenReturn(0);

        TMetpElemCard r1 = card(0L, "C1", "11");
        when(baseMapper.insert(any())).thenReturn(1);
        service.insertTMetpElemCard(r1);
        assertEquals(1, r1.getGradeLevel());

        assertEquals(2, gradeOf("12"));
        assertEquals(3, gradeOf("40"));
        assertEquals(4, gradeOf("120"));
    }

    private int gradeOf(String qty) {
        TMetpElemCard r = card(0L, "C" + qty, qty);
        service.insertTMetpElemCard(r);
        return r.getGradeLevel();
    }

    /** 停测档案上不得建卡 */
    @Test
    void insert_stoppedArchive_rejected() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(1L, 1));
        assertEquals(0, service.insertTMetpElemCard(card(1L, "C2", "10")));
        verify(baseMapper, never()).insert(any());
    }

    /** 查无档案 / 档案被删，不得建卡 */
    @Test
    void insert_missingArchive_rejected() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(null);
        assertEquals(0, service.insertTMetpElemCard(card(99L, "C3", "10")));
        verify(baseMapper, never()).insert(any());
    }

    /** 同号卡已存在不得再建 */
    @Test
    void insert_duplicateBillNo_rejected() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0L, 0));
        when(baseMapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.insertTMetpElemCard(card(0L, "DUP", "10")));
        verify(baseMapper, never()).insert(any());
    }

    /** 建卡人走自动填充，不得被卡号顶替；台站编号随档案并拢 */
    @Test
    void insert_siteNoCopiedFromArchive_andCreateByNotBillNo() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0L, 0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);
        TMetpElemCard r = card(0L, "B99", "5");
        r.setCreateBy("B99");
        service.insertTMetpElemCard(r);
        assertEquals("TQ00", r.getSiteNo());
        assertNull(r.getCreateBy());
    }

    /** 修改时换数量，按原档案上限重算档位 */
    @Test
    void update_recomputesGradeFromExistingArchive() {
        TMetpElemCard exists = card(0L, "C1", "5");
        exists.setId(10L);
        when(baseMapper.selectOne(any())).thenReturn(exists);
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0L, 0));
        when(baseMapper.update(any(TMetpElemCard.class), any(Wrapper.class))).thenReturn(1);

        TMetpElemCard patch = new TMetpElemCard();
        patch.setId(10L);
        patch.setQty(new BigDecimal("40")); // 等于二档上限 -> 3 档
        assertEquals(1, service.updateTMetpElemCard(patch));
        assertEquals(3, patch.getGradeLevel());
    }

    /** 删除一律软删，不得物理删除 */
    @Test
    void delete_isSoftDelete() {
        when(baseMapper.update(any(TMetpElemCard.class), any(Wrapper.class))).thenReturn(1);
        service.deleteTMetpElemCardById(10L);
        verify(baseMapper, never()).deleteById(any());
        service.deleteTMetpElemCardByIds("10,11");
        verify(baseMapper, never()).deleteBatchIds(any());
    }
}
