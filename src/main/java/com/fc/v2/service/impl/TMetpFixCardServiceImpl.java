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

    /** 末道关口：stage 取值 0..5 */
    private static final int MAX_STAGE = 5;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_RESOLVED = 1;
    private static final int STATUS_TERMINAL = 2;

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
        // 已归档不得再推进
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 已到末道关口，不得再往下走
        if (st >= MAX_STAGE) {
            return null;
        }
        st = st + 1;
        r.setStage(st);
        // 未走到末道关口始终在办；走满全程才算解除
        r.setStatus(st >= MAX_STAGE ? STATUS_RESOLVED : STATUS_RUNNING);
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
        // 已归档不得回退
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return null;
        }
        int st = r.getStage() == null ? 0 : r.getStage();
        // 已在头道关口，无处可退
        if (st <= 0) {
            return null;
        }
        r.setStage(st - 1);
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
        // 已办结（归档）不得再改
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
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
        // 已办结（归档）不得删除
        if (r.getStatus() != null && r.getStatus() == STATUS_TERMINAL) {
            return false;
        }
        return this.metpFixCardMapper.deleteById(id) > 0;
    }

}
