package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private TMetpStnDoc arch(int status) {
        TMetpStnDoc a = new TMetpStnDoc();
        a.setId(0L);
        a.setSiteNo("TQ00");
        a.setStatus(status);
        a.setDelFlag(0);
        a.setTh1Max(new BigDecimal("12"));
        a.setTh2Max(new BigDecimal("40"));
        a.setTh3Max(new BigDecimal("120"));
        return a;
    }

    private TMetpElemCard card(String billNo, String qty) {
        TMetpElemCard c = new TMetpElemCard();
        c.setBillNo(billNo);
        c.setSiteId(0);
        c.setQty(qty == null ? null : new BigDecimal(qty));
        c.setCreateBy("tester");
        return c;
    }

    @Test
    void insertCopiesSiteNoAndBandsEqualThresholdUp() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);

        TMetpElemCard c = card("B001", "12");
        assertEquals(1, service.insertTMetpElemCard(c));
        assertEquals("TQ00", c.getSiteNo());
        // 等于一档上限算高二档
        assertEquals(2, c.getGradeLevel());
        // 创建者不得被卡号覆盖
        assertEquals("tester", c.getCreateBy());
    }

    @Test
    void insertBandsBoundaries() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0));
        when(baseMapper.selectCount(any())).thenReturn(0);
        when(baseMapper.insert(any())).thenReturn(1);

        assertEquals(1, bandOf("11.99"));
        assertEquals(3, bandOf("40"));
        assertEquals(4, bandOf("120"));
        assertEquals(4, bandOf("121"));
    }

    private int bandOf(String qty) {
        TMetpElemCard c = card("B-" + qty, qty);
        service.insertTMetpElemCard(c);
        return c.getGradeLevel();
    }

    @Test
    void insertRejectsStoppedArchive() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(1));
        assertEquals(0, service.insertTMetpElemCard(card("B002", "10")));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void insertRejectsDuplicateBillNo() {
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0));
        when(baseMapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.insertTMetpElemCard(card("DUP", "10")));
        verify(baseMapper, never()).insert(any());
    }

    @Test
    void updateResyncsSiteNoAndRecomputesBand() {
        TMetpElemCard old = card("B010", "10");
        old.setId(7L);
        old.setSiteNo("OLD");
        old.setGradeLevel(1);
        when(baseMapper.selectById(7L)).thenReturn(old);
        when(metpStnDocMapper.selectOne(any())).thenReturn(arch(0));
        when(baseMapper.update(any(), any())).thenReturn(1);

        TMetpElemCard changed = new TMetpElemCard();
        changed.setId(7L);
        changed.setBillNo("B010");
        changed.setSiteId(0);
        changed.setQty(new BigDecimal("40"));
        assertEquals(1, service.updateTMetpElemCard(changed));
        assertEquals("TQ00", changed.getSiteNo());
        assertEquals(3, changed.getGradeLevel());
    }
}
