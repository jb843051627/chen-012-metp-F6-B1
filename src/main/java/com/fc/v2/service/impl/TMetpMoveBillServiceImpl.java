package com.fc.v2.service.impl;

import org.springframework.stereotype.Service;

import com.fc.v2.mapper.auto.TMetpMoveBillMapper;
import com.fc.v2.model.auto.TMetpMoveBill;
import com.fc.v2.service.ITMetpMoveBillService;

/**
 * 台站迁移变更逐级核准单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpMoveBillServiceImpl implements ITMetpMoveBillService {

    /** 走出本层即视为核讫的层号（层 0..MAX_NODE-1 需逐层签齐） */
    private static final int MAX_NODE = 2;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;

    @javax.annotation.Resource
    private TMetpMoveBillMapper metpMoveBillMapper;

    @Override
    public TMetpMoveBill selectTMetpMoveBillById(Long id) {
        return this.metpMoveBillMapper.selectById(id);
    }

    @Override
    public TMetpMoveBill approve(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 已核讫 / 已打回的单子不得继续签批
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }

        int signed = r.getSignCount() == null ? 0 : r.getSignCount();
        int required = requiredSignCount(r);
        // 同层点签：未点齐前留在本层，只累加已签人数
        if (signed + 1 < required) {
            r.setSignCount(signed + 1);
            r.setStatus(STATUS_RUNNING);
            this.metpMoveBillMapper.updateById(r);
            return r;
        }

        // 本层签齐：已签人数清零，升入下一层；走出最后一层即核讫
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        int nextNode = node + 1;
        r.setNodeNo(nextNode);
        r.setSignCount(0);
        r.setStatus(nextNode >= MAX_NODE ? STATUS_PASS : STATUS_RUNNING);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        // 已核讫 / 已打回的单子不得再否决
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        // 否决即打回，流程到此止步，不得继续向下游流转
        r.setStatus(STATUS_VETO);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill rollback(Long id, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        r.setNodeNo(Math.max(0, node - 1));
        // 退层后前一层已点的签全部作废，回退后重新点齐
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    /** 本层应签人数：档案（need_count）为准，缺省时会签两人、单选一人 */
    private int requiredSignCount(TMetpMoveBill r) {
        if (r.getNeedCount() != null && r.getNeedCount() > 0) {
            return r.getNeedCount();
        }
        return r.getSignMode() != null && r.getSignMode() == MODE_AND ? 2 : 1;
    }
}
