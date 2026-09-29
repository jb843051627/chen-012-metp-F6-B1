package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TMetpQcLineMapper;
import com.fc.v2.model.auto.TMetpQcLine;
import com.fc.v2.service.ITMetpQcLineService;

/**
 * 观测数据质量控制界限 Service业务层处理（rule-eval 形状）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TMetpQcLineServiceImpl implements ITMetpQcLineService {

    /** 界限情形：已停用 */
    private static final int RULE_STATUS_DISABLED = 1;
    /** 输入合法闭区间：[0, 100] */
    private static final BigDecimal INPUT_MIN = BigDecimal.ZERO;
    private static final BigDecimal INPUT_MAX = new BigDecimal("100");

    /** 优先级降序；并列按 ruleCode 降序（确定性） */
    private static final Comparator<TMetpQcLine> PRIORITY_THEN_CODE_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority();
            int pb = b.getPriority() == null ? 0 : b.getPriority();
            if (pa != pb) {
                return pb - pa;
            }
            String ca = a.getRuleCode() == null ? "" : a.getRuleCode();
            String cb = b.getRuleCode() == null ? "" : b.getRuleCode();
            return cb.compareTo(ca);
        }
    };

    /** 同代号多版本并存时，启用之日更晚者优先（null 视为最早） */
    private static final Comparator<TMetpQcLine> START_DESC = new Comparator<TMetpQcLine>() {
        @Override
        public int compare(TMetpQcLine a, TMetpQcLine b) {
            Date sa = a.getEffStart();
            Date sb = b.getEffStart();
            if (sa == null && sb == null) {
                return 0;
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            return sb.compareTo(sa);
        }
    };

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        if (at == null) {
            return new java.util.ArrayList<TMetpQcLine>();
        }
        List<TMetpQcLine> all = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0));
        List<TMetpQcLine> avail = new java.util.ArrayList<TMetpQcLine>();
        for (TMetpQcLine r : all) {
            if (inEffect(r, at)) {
                avail.add(r);
            }
        }
        Collections.sort(avail, PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (!inputInRange(input)) {
            return 0;
        }
        TMetpQcLine rule = findRule(ruleCode, at);
        if (rule == null) {
            return 0;
        }
        return levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (!inputInRange(input)) {
            return 0;
        }
        List<TMetpQcLine> avail = listAvailable(at);
        if (avail.isEmpty()) {
            return 0;
        }
        return levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TMetpQcLine r = this.metpQcLineMapper.selectById(id);
        return r != null && inEffect(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        if (at == null) {
            return 0;
        }
        return listAvailable(at).size();
    }

    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0).eq("rule_code", ruleCode));
        TMetpQcLine picked = null;
        for (TMetpQcLine r : hit) {
            if (!inEffect(r, at)) {
                continue;
            }
            if (picked == null || START_DESC.compare(r, picked) < 0) {
                picked = r;
            }
        }
        return picked;
    }

    /**
     * at 时刻是否落在生效半开区间 [eff_start, eff_end)：启用之日含、交棒之日不含；
     * 任一端未维护视为该端无界；已停用 / 已删除不在册。
     * 区间比较只在内存中进行（Date 直绑 JDBC 会因 serverTimezone 不对称整体偏移）。
     */
    private boolean inEffect(TMetpQcLine r, Date at) {
        if (r.getStatus() != null && r.getStatus() == RULE_STATUS_DISABLED) {
            return false;
        }
        if (r.getEffStart() != null && at.before(r.getEffStart())) {
            return false;
        }
        if (r.getEffEnd() != null && !at.before(r.getEffEnd())) {
            return false;
        }
        return true;
    }

    private boolean inputInRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) >= 0 && input.compareTo(INPUT_MAX) <= 0;
    }

    /** 分档：等于上限取高一档；阈值未维护的档跳过 */
    private int levelOf(TMetpQcLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
