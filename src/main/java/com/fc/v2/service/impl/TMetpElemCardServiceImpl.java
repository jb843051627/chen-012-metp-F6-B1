package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fc.v2.common.support.ConvertUtil;
import com.fc.v2.mapper.auto.TMetpElemCardMapper;
import com.fc.v2.mapper.auto.TMetpStnDocMapper;
import com.fc.v2.model.auto.TMetpElemCard;
import com.fc.v2.model.auto.TMetpStnDoc;
import com.fc.v2.service.ITMetpElemCardService;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 观测要素挂用建档卡Service业务层处理
 *
 * @author fuce
 * @date 2026-09-12
 */
@Service
public class TMetpElemCardServiceImpl extends ServiceImpl<TMetpElemCardMapper, TMetpElemCard> implements ITMetpElemCardService {

    /** 档案情形：已停测 */
    private static final int ARCH_STATUS_STOPPED = 1;

    @Autowired
    private TMetpStnDocMapper metpStnDocMapper;

    @Override
    public TMetpElemCard selectTMetpElemCardById(Long id) {
        return this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public List<TMetpElemCard> selectTMetpElemCardList(Wrapper<TMetpElemCard> queryWrapper) {
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTMetpElemCard(TMetpElemCard record) {
        if (record == null) {
            return 0;
        }

        TMetpStnDoc refArch = loadActiveArch(record.getSiteId());
        if (refArch == null) {
            // 档案不存在 / 已删除 / 已停测，一律不得挂卡
            return 0;
        }
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        // 卡上两处指向同一本档：编号照档案誊写，不各记各的
        record.setSiteNo(refArch.getSiteNo());
        record.setGradeLevel(bandLevel(refArch, record.getQty()));
        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMetpElemCard(TMetpElemCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        TMetpElemCard old = this.baseMapper.selectById(record.getId());
        if (old == null || (old.getDelFlag() != null && old.getDelFlag() != 0)) {
            return 0;
        }

        // 换了挂靠档案：新档须在册，编号随档案一并改过来
        Integer siteId = record.getSiteId() != null ? record.getSiteId() : old.getSiteId();
        TMetpStnDoc refArch = loadActiveArch(siteId);
        if (refArch == null) {
            return 0;
        }
        record.setSiteNo(refArch.getSiteNo());

        // 项数或档案任一变化，等级都按同一本档重算，避免卡/档两处账分叉
        BigDecimal qty = record.getQty() != null ? record.getQty() : old.getQty();
        record.setGradeLevel(bandLevel(refArch, qty));

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMetpElemCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        return this.baseMapper.deleteBatchIds(Arrays.asList(idArr));
    }

    @Override
    public int deleteTMetpElemCardById(Long id) {
        return this.baseMapper.deleteById(id);
    }

    /** 取在册（未删除、未停测）的台站档案；查不到返回 null */
    private TMetpStnDoc loadActiveArch(Integer siteId) {
        if (siteId == null) {
            return null;
        }
        TMetpStnDoc arch = this.metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                .eq("id", siteId).eq("del_flag", 0));
        if (arch == null || (arch.getStatus() != null && arch.getStatus() == ARCH_STATUS_STOPPED)) {
            return null;
        }
        return arch;
    }

    /**
     * 分档：等于上限算高一档（种子档案口径 12/40/120）。
     * 严格小于本档上限落在本档；阈值未维护的档跳过；项数未维护返回 0。
     */
    private int bandLevel(TMetpStnDoc arch, BigDecimal qty) {
        if (qty == null) {
            return 0;
        }
        if (arch.getTh1Max() != null && qty.compareTo(arch.getTh1Max()) < 0) {
            return 1;
        }
        if (arch.getTh2Max() != null && qty.compareTo(arch.getTh2Max()) < 0) {
            return 2;
        }
        if (arch.getTh3Max() != null && qty.compareTo(arch.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
