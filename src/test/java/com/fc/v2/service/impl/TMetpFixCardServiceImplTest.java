package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fc.v2.mapper.auto.TMetpFixCardMapper;
import com.fc.v2.model.auto.TMetpFixCard;

@ExtendWith(MockitoExtension.class)
class TMetpFixCardServiceImplTest {

    @Mock
    private TMetpFixCardMapper mapper;

    @InjectMocks
    private TMetpFixCardServiceImpl service;

    private TMetpFixCard card(Integer stage, Integer status) {
        TMetpFixCard r = new TMetpFixCard();
        r.setId(1L);
        r.setStage(stage);
        r.setStatus(status);
        return r;
    }

    /** 每次只过一道口；未到末梢仍是在办，不得提前解除 */
    @Test
    void advance_oneGateAtATime_staysActiveBeforeEnd() {
        when(mapper.selectById(1L)).thenReturn(card(0, 0));
        TMetpFixCard r = service.advance(1L, "go");
        assertEquals(1, r.getStage());
        assertEquals(0, r.getStatus());

        when(mapper.selectById(1L)).thenReturn(card(1, 0));
        r = service.advance(1L, "go");
        assertEquals(2, r.getStage());
        assertEquals(0, r.getStatus());
    }

    /** 走到最后一道口才落定为已解除 */
    @Test
    void advance_toLastGate_resolves() {
        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        TMetpFixCard r = service.advance(1L, "go");
        assertEquals(3, r.getStage());
        assertEquals(1, r.getStatus());
    }

    /** 已办结（已解除/已归档）不得再推进 */
    @Test
    void advance_finished_rejected() {
        when(mapper.selectById(1L)).thenReturn(card(3, 1));
        assertNull(service.advance(1L, "go"));
        when(mapper.selectById(1L)).thenReturn(card(3, 2));
        assertNull(service.advance(1L, "go"));
        verify(mapper, never()).updateById(any());
    }

    /** 回退只退一道口，不一头打回起点；第 0 道再退不动 */
    @Test
    void rollback_oneGateOnly() {
        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        TMetpFixCard r = service.rollback(1L, "back");
        assertEquals(1, r.getStage());
        assertEquals(0, r.getStatus());

        when(mapper.selectById(1L)).thenReturn(card(0, 0));
        r = service.rollback(1L, "back");
        assertEquals(0, r.getStage());
    }

    /** 已办结不得回退 */
    @Test
    void rollback_finished_rejected() {
        when(mapper.selectById(1L)).thenReturn(card(3, 1));
        assertNull(service.rollback(1L, "back"));
    }

    /** 已办结不得改内容、不得删除 */
    @Test
    void finished_contentLockedAndNotRemovable() {
        when(mapper.selectById(1L)).thenReturn(card(3, 1));
        assertFalse(service.updateContent(1L, "x"));
        assertFalse(service.remove(1L));
        verify(mapper, never()).deleteById(any());

        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        when(mapper.updateById(any())).thenReturn(1);
        assertTrue(service.updateContent(1L, "x"));
        when(mapper.deleteById(1L)).thenReturn(1);
        assertTrue(service.remove(1L));
    }
}
