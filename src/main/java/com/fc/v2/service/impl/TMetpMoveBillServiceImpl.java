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

    /** 末层：node_no 取值 0..3，第 3 层签齐即核讫 */
    private static final int MAX_NODE = 3;
    private static final int MODE_OR = 0;
    private static final int MODE_AND = 1;
    private static final int STATUS_RUNNING = 0;
    private static final int STATUS_PASS = 1;
    private static final int STATUS_VETO = 2;
    /** 两名点齐：need_count 未维护时会签层的默认应签人数 */
    private static final int DEFAULT_AND_NEED = 2;

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
        // 已核讫 / 已打回的单子不得继续签
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node > MAX_NODE) {
            return null;
        }
        int mode = r.getSignMode() == null ? MODE_OR : r.getSignMode();
        int signed = r.getSignCount() == null ? 0 : r.getSignCount() + 1;
        r.setSignCount(signed);

        if (mode == MODE_AND && signed < andNeed(r)) {
            // 会签层尚未点齐：本层停留，仅累计签数
            r.setNodeNo(node);
            r.setStatus(STATUS_RUNNING);
        } else {
            // 任一人即过，或会签已点齐：本层签数清零后进入下一层
            r.setSignCount(0);
            if (node >= MAX_NODE) {
                r.setNodeNo(MAX_NODE);
                r.setStatus(STATUS_PASS);
            } else {
                r.setNodeNo(node + 1);
                r.setStatus(STATUS_RUNNING);
            }
        }
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    @Override
    public TMetpMoveBill reject(Long id, String approver, String comment) {
        TMetpMoveBill r = this.metpMoveBillMapper.selectById(id);
        if (r == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
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
        // 只有在核中的单子可退；已核讫/已打回不得退
        if (r.getStatus() != null && r.getStatus() != STATUS_RUNNING) {
            return null;
        }
        int node = r.getNodeNo() == null ? 0 : r.getNodeNo();
        if (node <= 0) {
            return null;
        }
        r.setNodeNo(node - 1);
        // 退回一层，上一层签过的笔墨一并抹去，须重新会签
        r.setSignCount(0);
        r.setStatus(STATUS_RUNNING);
        this.metpMoveBillMapper.updateById(r);
        return r;
    }

    private int andNeed(TMetpMoveBill r) {
        Integer need = r.getNeedCount();
        return need == null || need.intValue() <= 0 ? DEFAULT_AND_NEED : need.intValue();
    }
}
