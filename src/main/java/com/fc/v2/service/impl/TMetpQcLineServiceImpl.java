package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    private static final int STATUS_ACTIVE = 0;

    /** 合法入参区间 [0, 100]，越界一律按不可判处理返回 0 */
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

    @javax.annotation.Resource
    private TMetpQcLineMapper metpQcLineMapper;

    @Override
    public TMetpQcLine selectTMetpQcLineById(Long id) {
        return this.metpQcLineMapper.selectById(id);
    }

    @Override
    public List<TMetpQcLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TMetpQcLine>();
        }
        List<TMetpQcLine> all = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE));
        List<TMetpQcLine> avail = new ArrayList<TMetpQcLine>();
        for (TMetpQcLine r : all) {
            // SQL 已按现行/未删过滤，这里再兜一道，口径不随调用方改 wrapper 而走样
            boolean active = r.getStatus() == null || r.getStatus() == STATUS_ACTIVE;
            boolean alive = r.getDelFlag() == null || r.getDelFlag() == 0;
            if (active && alive && inEffect(r, at)) {
                avail.add(r);
            }
        }
        avail.sort(PRIORITY_THEN_CODE_DESC);
        return avail;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        if (outOfRange(input)) {
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
        if (outOfRange(input)) {
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
        return r != null
                && (r.getStatus() == null || r.getStatus() == STATUS_ACTIVE)
                && (r.getDelFlag() == null || r.getDelFlag() == 0)
                && inEffect(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        return listAvailable(at).size();
    }

    /** 定位某代号在 at 时刻的生效版本；同刻多版并存时取顺位最高者（口径与列表一致） */
    private TMetpQcLine findRule(String ruleCode, Date at) {
        List<TMetpQcLine> hit = this.metpQcLineMapper.selectList(new QueryWrapper<TMetpQcLine>()
                .eq("del_flag", 0)
                .eq("status", STATUS_ACTIVE)
                .eq("rule_code", ruleCode));
        TMetpQcLine picked = null;
        for (TMetpQcLine r : hit) {
            boolean active = r.getStatus() == null || r.getStatus() == STATUS_ACTIVE;
            boolean alive = r.getDelFlag() == null || r.getDelFlag() == 0;
            if (!active || !alive || !inEffect(r, at)) {
                continue;
            }
            if (picked == null || PRIORITY_THEN_CODE_DESC.compare(r, picked) < 0) {
                picked = r;
            }
        }
        return picked;
    }

    /** 生效窗口：启用之日 ≤ at < 交棒之日（交棒日不含）；未维护启用日视为早有效、未维护交棒日视为长期有效 */
    private boolean inEffect(TMetpQcLine r, Date at) {
        if (r.getEffStart() != null && r.getEffStart().after(at)) {
            return false;
        }
        if (r.getEffEnd() != null && !r.getEffEnd().after(at)) {
            return false;
        }
        return true;
    }

    private boolean outOfRange(BigDecimal input) {
        return input.compareTo(INPUT_MIN) < 0 || input.compareTo(INPUT_MAX) > 0;
    }

    /** 分档：等于上限取高一档（严格小于本档上限才算本档）；该档上限未维护时顺推下一档，全部未维护归 4 档 */
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
