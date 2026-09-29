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

    private TMetpFixCard card(int stage, int status) {
        TMetpFixCard r = new TMetpFixCard();
        r.setId(1L);
        r.setStage(stage);
        r.setStatus(status);
        return r;
    }

    @Test
    void advanceMovesExactlyOneStageAndStaysActive() {
        when(mapper.selectById(1L)).thenReturn(card(2, 0));
        TMetpFixCard r = service.advance(1L, "go");
        assertEquals(3, r.getStage());
        assertEquals(0, r.getStatus());
        assertEquals("go", r.getLastAction());
    }

    @Test
    void advanceToLastStageResolvesButDoesNotArchive() {
        when(mapper.selectById(1L)).thenReturn(card(4, 0));
        TMetpFixCard r = service.advance(1L, null);
        assertEquals(5, r.getStage());
        assertEquals(1, r.getStatus());
    }

    @Test
    void advanceBeyondLastStageRejected() {
        when(mapper.selectById(1L)).thenReturn(card(5, 1));
        assertNull(service.advance(1L, null));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void advanceArchivedCardRejected() {
        when(mapper.selectById(1L)).thenReturn(card(0, 2));
        assertNull(service.advance(1L, null));
    }

    @Test
    void rollbackMovesExactlyOneStage() {
        when(mapper.selectById(1L)).thenReturn(card(3, 0));
        TMetpFixCard r = service.rollback(1L, "back");
        assertEquals(2, r.getStage());
        assertEquals(0, r.getStatus());
    }

    @Test
    void rollbackAtFirstStageRejected() {
        when(mapper.selectById(1L)).thenReturn(card(0, 0));
        assertNull(service.rollback(1L, null));
    }

    @Test
    void archivedCardCannotBeEditedOrRemoved() {
        when(mapper.selectById(1L)).thenReturn(card(5, 2));
        assertFalse(service.updateContent(1L, "x"));
        assertFalse(service.remove(1L));
        verify(mapper, never()).updateById(any());
        verify(mapper, never()).deleteById(1L);
    }

    @Test
    void activeCardCanBeEditedAndRemoved() {
        when(mapper.selectById(1L)).thenReturn(card(1, 0));
        when(mapper.updateById(any())).thenReturn(1);
        when(mapper.deleteById(1L)).thenReturn(1);
        assertTrue(service.updateContent(1L, "x"));
        assertTrue(service.remove(1L));
    }
}
