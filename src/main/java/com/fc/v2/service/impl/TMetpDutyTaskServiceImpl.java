package com.fc.v2.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    /** 允许提前派发的自然日窗口：最早提前 WIN_TO 日、最晚提前 WIN_FROM 日 */
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
        List<TMetpDutyTask> all = this.metpDutyTaskMapper.selectList(new QueryWrapper<TMetpDutyTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT));
        List<TMetpDutyTask> due = new java.util.ArrayList<TMetpDutyTask>();
        for (TMetpDutyTask r : all) {
            // SQL 已按待派/未删过滤，这里再兜一道，口径不随调用方改 wrapper 而走样
            boolean waiting = r.getStatus() == null || r.getStatus() == STATUS_WAIT;
            boolean alive = r.getDelFlag() == null || r.getDelFlag() == 0;
            if (waiting && alive && isDispatchable(r, at)) {
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
                // 派一条落一条：已派回写，单条失败挂起后继续派下一条
                r.setStatus(STATUS_DONE);
                this.metpDutyTaskMapper.updateById(r);
                ok++;
            } catch (RuntimeException ex) {
                r.setStatus(STATUS_FAIL);
                this.metpDutyTaskMapper.updateById(r);
            }
        }
        return ok;
    }

    /**
     * 可派发三条件齐备：
     * 1) 提前派发日数（amount）落在允许窗口 [WIN_FROM, WIN_TO] 内；
     * 2) 已到派发时刻（at ≥ 到期日终 − amount 个自然日，恰好到期也算）；
     * 3) 截止时刻已维护。
     */
    private boolean isDispatchable(TMetpDutyTask r, Date at) {
        if (r.getDueAt() == null || r.getAmount() == null || at == null) {
            return false;
        }
        int leadDays = r.getAmount().intValue();
        if (leadDays < WIN_FROM || leadDays > WIN_TO) {
            return false;
        }
        long daysUntilDue = naturalDaysBetween(at, r.getDueAt());
        return daysUntilDue <= leadDays;
    }

    /** 两日终时刻之间的自然日数（未来为正、过去为负） */
    private long naturalDaysBetween(Date from, Date to) {
        Calendar a = dayStart(from);
        Calendar b = dayStart(to);
        return TimeUnit.MILLISECONDS.toDays(b.getTimeInMillis() - a.getTimeInMillis());
    }

    private Calendar dayStart(Date d) {
        Calendar c = Calendar.getInstance();
        c.setTime(d);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
