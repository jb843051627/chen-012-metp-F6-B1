package com.fc.v2.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpSubmitRowMapper;
import com.fc.v2.model.auto.TMetpSubmitRow;
import com.fc.v2.service.ITMetpSubmitRowService;

import java.math.BigDecimal;

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
    public int submitBatch(String batchNo, List<TMetpSubmitRow> rows) {
        // 空批次：无事可做
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        // 超单批上限：整批不入库，交调用方截流
        if (rows.size() > MAX_ROWS) {
            return -1;
        }

        // 同一批次重复提交不得重复入库：已有落账则原样返回既往成功行数
        Integer existed = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0));
        if (existed != null && existed > 0) {
            Integer ok = this.metpSubmitRowMapper.selectCount(new QueryWrapper<TMetpSubmitRow>()
                    .eq("batch_no", batchNo).eq("status", STATUS_OK).eq("del_flag", 0));
            return ok == null ? 0 : ok;
        }

        int ok = 0;
        for (int i = 0; i < rows.size(); i++) {
            TMetpSubmitRow r = rows.get(i);
            r.setBatchNo(batchNo);
            // 保留原册行号；源行未带行号时以册内顺序兜底
            if (r.getRowNo() == null) {
                r.setRowNo(Integer.valueOf(i + 1));
            }
            r.setDelFlag(0);
            if (!isValid(r)) {
                // 非法行挂驳回，不拖累同批合法行继续转存
                r.setStatus(STATUS_FAIL);
                this.metpSubmitRowMapper.insert(r);
                continue;
            }
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

    /** 时次要素代号非空、填报条数为正 */
    private boolean isValid(TMetpSubmitRow r) {
        return r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                && r.getQty() != null
                && r.getQty().compareTo(BigDecimal.ZERO) > 0;
    }
}
