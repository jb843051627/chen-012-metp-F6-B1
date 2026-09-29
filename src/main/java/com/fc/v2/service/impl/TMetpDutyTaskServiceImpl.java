package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpDutyTaskMapper;
import com.fc.v2.model.auto.TMetpDutyTask;
import com.fc.v2.service.ITMetpDutyTaskService;

/**
 * 标定巡检到期派工条目 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpDutyTaskServiceImpl implements ITMetpDutyTaskService {

    private static final Logger logger = LoggerFactory.getLogger(TMetpDutyTaskServiceImpl.class);

    /** 允许提前派发的自然日数窗口 [WIN_FROM, WIN_TO] */
    private static final int WIN_FROM = 2;
    private static final int WIN_TO = 5;
    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    @javax.annotation.Resource
    private TMetpDutyTaskMapper metpDutyTaskMapper;

    @Override
    public TMetpDutyTask selectTMetpDutyTaskById(Long id) {
        return this.metpDutyTaskMapper.selectById(id);
    }

    @Override
    public List<TMetpDutyTask> listDue(Date at) {
        if (at == null) {
            return new ArrayList<TMetpDutyTask>();
        }
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TMetpDutyTask> due = new ArrayList<TMetpDutyTask>();
        for (TMetpDutyTask r : all) {
            // SQL 已按未删除/待派过滤，内存再兜底一道，口径不依赖调用方是否走库
            if (r.getDelFlag() != null && r.getDelFlag() != 0) {
                continue;
            }
            if (r.getStatus() != null && r.getStatus() != STATUS_WAIT) {
                continue;
            }
            if (r.getDueAt() != null
                    // 到期（含恰好到期的日终截止时刻）
                    && !r.getDueAt().after(at)
                    // 提前派发天数落在执行窗口内
                    && inDispatchWindow(r.getAmount())) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        List<TMetpDutyTask> due = listDue(at);
        int ok = 0;
        for (TMetpDutyTask r : due) {
            try {
                r.setStatus(STATUS_DONE);
                this.metpDutyTaskMapper.updateById(r);
                ok++;
            } catch (RuntimeException e) {
                // 单条落账失败跳过继续：该条仍是待派，下一轮还能再捞，不绊住同批其余条目
                logger.warn("到期派工单条失败，itemNo={}", r.getItemNo(), e);
            }
        }
        return ok;
    }

    private boolean inDispatchWindow(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return amount.compareTo(BigDecimal.valueOf(WIN_FROM)) >= 0
                && amount.compareTo(BigDecimal.valueOf(WIN_TO)) <= 0;
    }
}
