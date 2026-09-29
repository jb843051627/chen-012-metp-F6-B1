package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;
import com.fc.v2.util.StringUtils;

/**
 * 正点观测数据核收转存行 Service业务层处理（batch-process 形状：整批提交）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpSubmitRowServiceImpl implements ITMetpSubmitRowService {

    private static final int MAX_ROWS = 500;
    private static final int STATUS_OK = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpSubmitRowMapper metpSubmitRowMapper;

    @Override
    public TMetpSubmitRow selectTMetpSubmitRowById(Long id) {
        return this.metpSubmitRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 行数超过单批上限：整批不入库
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        // 册号以入参为准，入参未给时回退取首行自带册号
        String no = StringUtils.isNotEmpty(batchNo) ? batchNo : rows.get(0) == null ? null : rows.get(0).getBatchNo();

        // 同一批次重复提交不得重复入库（已转存成功的批次不得再进；上回仅挂驳回的不算）
        if (StringUtils.isNotEmpty(no)) {
            Integer exist = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                    .eq("batch_no", no).eq("status", STATUS_OK).eq("del_flag", 0));
            if (exist != null && exist > 0) {
                return 0;
            }
            // 上回挂驳回的明细随本次重新验明一并清掉，免得同册两本账
            TMetpSubmitRow tomb = new TMetpSubmitRow();
            tomb.setDelFlag(1);
            this.metpSubmitRowMapper.update(tomb, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<TMetpSubmitRow>()
                    .eq("batch_no", no).eq("status", STATUS_FAIL).eq("del_flag", 0));
        }

        // 先逐行验明：非法行挂驳回（保留原册行次），一条不过则整批不转存
        List<TMetpSubmitRow> errors = new ArrayList<TMetpSubmitRow>();
        for (TMetpSubmitRow r : rows) {
            // 空元素无法落明细，整批按拒收处理
            if (r == null) {
                return 0;
            }
            if (!isValid(r)) {
                r.setBatchNo(no);
                r.setStatus(STATUS_FAIL);
                errors.add(r);
            }
        }
        if (!errors.isEmpty()) {
            for (TMetpSubmitRow r : errors) {
                this.metpSubmitRowMapper.insert(r);
            }
            return 0;
        }

        // 全部合法：统一回写册号后转存
        int ok = 0;
        for (TMetpSubmitRow r : rows) {
            r.setBatchNo(no);
            r.setStatus(STATUS_OK);
            this.metpSubmitRowMapper.insert(r);
            ok++;
        }
        return ok;
    }

    @Override
    public List<TMetpSubmitRow> listErrors(String batchNo) {
        return this.metpSubmitRowMapper.selectList(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo)
                .eq("status", STATUS_FAIL)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    /** 行合法：要素代号非空、填报条数为正 */
    private boolean isValid(TMetpSubmitRow r) {
        return r != null
                && StringUtils.isNotEmpty(r.getItemCode())
                && r.getQty() != null
                && r.getQty().compareTo(java.math.BigDecimal.ZERO) > 0;
    }
}
