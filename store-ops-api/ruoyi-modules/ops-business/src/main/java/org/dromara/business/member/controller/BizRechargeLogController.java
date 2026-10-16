package org.dromara.business.member.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.business.member.domain.bo.BizRechargeLogBo;
import org.dromara.business.member.domain.bo.BizRechargeReversalBo;
import org.dromara.business.member.domain.vo.BizRechargeLogVo;
import org.dromara.business.member.service.IBizRechargeLogService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 充值流水（管理台专用；冲正仅管理台入口，员工端 /mp/staff/** 无冲正功能）
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/member/recharge")
public class BizRechargeLogController extends BaseController {

    private final IBizRechargeLogService rechargeLogService;

    /**
     * 充值流水分页（筛选：门店/会员手机号/昵称/充值方式/状态/时间区间；数据权限自动过滤）
     */
    @SaCheckPermission("ops:recharge:list")
    @GetMapping("/list")
    public TableDataInfo<BizRechargeLogVo> list(BizRechargeLogBo bo, PageQuery pageQuery) {
        return rechargeLogService.selectPageRechargeList(bo, pageQuery);
    }

    /**
     * 充值冲正（仅当日、仅一次、余额充足；冲正原因必填）
     *
     * @param rechargeId 充值流水ID
     * @param bo         冲正原因
     */
    @SaCheckPermission("ops:recharge:reversal")
    @Log(title = "充值冲正", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PostMapping("/{rechargeId}/reversal")
    public R<Void> reversal(@PathVariable Long rechargeId, @Validated @RequestBody BizRechargeReversalBo bo) {
        return toAjax(rechargeLogService.reversal(rechargeId, bo.getReason()));
    }

}
