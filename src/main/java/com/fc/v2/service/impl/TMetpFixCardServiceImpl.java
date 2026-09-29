package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpFixCardMapper;
import com.fc.v2.model.auto.TMetpFixCard;
import com.fc.v2.service.ITMetpFixCardService;

/**
 * 探测环境违规整改事务卡 Service业务层处理（state-machine 形状：单据流转）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpFixCardServiceImpl implements ITMetpFixCardService {

    /** 走到该关口即解除（关口 0..MAX_STAGE，每次只过一道口） */
    private static final int MAX_STAGE = 3;
    /** 卡的落定 0在办 1已解除 2已归档；1、2 皆为办结态 */
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_ARCHIVED = 2;

    @javax.annotation.Resource
    private TMetpFixCardMapper metpFixCardMapper;

    @Override
    public TMetpFixCard selectTMetpFixCardById(Long id) {
        return this.metpFixCardMapper.selectById(id);
    }

    @Override
    public List<TMetpFixCard> selectTMetpFixCardList(QueryWrapper<TMetpFixCard> queryWrapper) {
        return this.metpFixCardMapper.selectList(queryWrapper);
    }

    @Override
    public TMetpFixCard advance(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已办结（已解除 / 已归档）的卡不得再往下去
        if (isFinished(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        int next = Math.min(st + 1, MAX_STAGE);
        r.setStage(next);
        // 走到末梢才解除，半路不得提前亮绿灯
        r.setStatus(next >= MAX_STAGE ? STATUS_RESOLVED : STATUS_RUNNING);
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpFixCard rollback(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return null;
        }
        // 已办结的卡不得回退
        if (isFinished(r)) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 只退一道口，不一头打回起点；退回后仍是在办
        r.setStage(Math.max(0, st - 1));
        r.setStatus(STATUS_RUNNING);
        r.setLastAction(remark);
        this.metpFixCardMapper.updateById(r);
        return r;
    }

    @Override
    public boolean updateContent(Long id, String remark) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结不得再改
        if (isFinished(r)) {
            return false;
        }
        r.setContent(remark);
        return this.metpFixCardMapper.updateById(r) > 0;
    }

    @Override
    public boolean remove(Long id) {
        TMetpFixCard r = this.metpFixCardMapper.selectById(id);
        if (r == null) {
            return false;
        }
        // 已办结不得删除
        if (isFinished(r)) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

    private boolean isFinished(TMetpFixCard r) {
        return r.getStatus() != null
                && (r.getStatus() == STATUS_RESOLVED || r.getStatus() == STATUS_ARCHIVED);
    }

}
