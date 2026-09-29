package com.fc.v2.service.impl;

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
        // 条件与分页均由调用方给（startPage 已在控制层下过），此处不得另起炉灶、不得写死进展
        return this.baseMapper.selectList(queryWrapper);
    }

    @Override
    public int insertTMetpElemCard(TMetpElemCard record) {
        if (record == null) {
            return 0;
        }

        TMetpStnDoc refArch = metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                .eq("id", record.getSiteId()).eq("del_flag", 0));
        if (refArch == null) {
            return 0;
        }
        if (refArch.getStatus() != null && refArch.getStatus() == 1) {
            return 0;
        }
        // 卡号即建档卡号，建卡人走自动填充，不得拿卡号顶建卡人
        record.setCreateBy(null);
        record.setSiteNo(refArch.getSiteNo());
        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }
        record.setGradeLevel(gradeOf(refArch, record.getQty()));

        record.setDelFlag(0);
        return this.baseMapper.insert(record);
    }

    @Override
    public int updateTMetpElemCard(TMetpElemCard record) {
        if (record == null || record.getId() == null) {
            return 0;
        }

        TMetpElemCard exists = this.baseMapper.selectOne(new QueryWrapper<TMetpElemCard>()
                .eq("id", record.getId()).eq("del_flag", 0));
        if (exists == null) {
            return 0;
        }

        if (StringUtils.isNotEmpty(record.getBillNo())) {
            Integer dupCnt = this.baseMapper.selectCount(new QueryWrapper<TMetpElemCard>()
                    .eq("bill_no", record.getBillNo()).ne("id", record.getId()).eq("del_flag", 0));
            if (dupCnt != null && dupCnt > 0) {
                return 0;
            }
        }

        // 换挂档案时，卡上编号与档位随新档案并拢；仍须是在册档案
        TMetpStnDoc refArch = null;
        if (record.getSiteId() != null) {
            refArch = metpStnDocMapper.selectOne(new QueryWrapper<TMetpStnDoc>()
                    .eq("id", record.getSiteId()).eq("del_flag", 0));
            if (refArch == null || (refArch.getStatus() != null && refArch.getStatus() == 1)) {
                return 0;
            }
            record.setSiteNo(refArch.getSiteNo());
        }

        // 项数与档级口径同建卡一致：数量变了按档案上限重算（档案未换则取原档案）
        if (record.getQty() != null) {
            TMetpStnDoc arch = refArch != null ? refArch : metpStnDocMapper.selectOne(
                    new QueryWrapper<TMetpStnDoc>().eq("id", exists.getSiteId()).eq("del_flag", 0));
            if (arch != null) {
                record.setGradeLevel(gradeOf(arch, record.getQty()));
            }
        }

        record.setUpdateTime(new Date());
        return this.baseMapper.update(record, new UpdateWrapper<TMetpElemCard>()
                .eq("id", record.getId())
                .eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardByIds(String ids) {
        Long[] idArr = ConvertUtil.toLongArray(ids);
        if (idArr.length == 0) {
            return 0;
        }
        // 与全表口径一致：软删，不做物理删除
        TMetpElemCard tomb = new TMetpElemCard();
        tomb.setDelFlag(1);
        tomb.setUpdateTime(new Date());
        return this.baseMapper.update(tomb, new UpdateWrapper<TMetpElemCard>()
                .in("id", Arrays.asList(idArr)).eq("del_flag", 0));
    }

    @Override
    public int deleteTMetpElemCardById(Long id) {
        TMetpElemCard tomb = new TMetpElemCard();
        tomb.setDelFlag(1);
        tomb.setUpdateTime(new Date());
        return this.baseMapper.update(tomb, new UpdateWrapper<TMetpElemCard>()
                .eq("id", id).eq("del_flag", 0));
    }

    /** 按档案三档上限归级，等于上限计入高一档；档案未维护上限的档跳过（取靠后一档） */
    private int gradeOf(TMetpStnDoc arch, java.math.BigDecimal qty) {
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
