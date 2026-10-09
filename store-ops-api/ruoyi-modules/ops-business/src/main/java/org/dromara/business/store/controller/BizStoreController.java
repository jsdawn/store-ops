package org.dromara.business.store.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.business.store.domain.bo.BizStoreBo;
import org.dromara.business.store.domain.vo.BizStoreQrcodeVo;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.domain.R;
import org.dromara.common.idempotent.annotation.RepeatSubmit;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.web.core.BaseController;
import org.dromara.system.api.domain.vo.RemoteDeptVo;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店管理（管理台 pc 端）
 *
 * <p>本期不提供删除接口（PRD 5.0 节）；mp 端接口（/mp/store/**）随小程序端按需补充。</p>
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/store")
public class BizStoreController extends BaseController {

    private final IBizStoreService storeService;

    /**
     * 门店分页列表（筛选：名称模糊、状态；租户插件自动隔离）
     */
    @SaCheckPermission("ops:store:list")
    @GetMapping("/list")
    public TableDataInfo<BizStoreVo> list(BizStoreBo bo, PageQuery pageQuery) {
        return storeService.selectPageStoreList(bo, pageQuery);
    }

    /**
     * 门店详情
     *
     * @param storeId 门店ID
     */
    @SaCheckPermission("ops:store:query")
    @GetMapping("/{storeId}")
    public R<BizStoreVo> getInfo(@PathVariable Long storeId) {
        return R.ok(storeService.queryStoreById(storeId));
    }

    /**
     * 新增门店（先建部门再关联，1 部门 1 门店）
     */
    @SaCheckPermission("ops:store:add")
    @Log(title = "门店管理", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody BizStoreBo bo) {
        return toAjax(storeService.insertStore(bo));
    }

    /**
     * 修改门店（名称/地址/状态/备注；部门只读不可换绑）
     */
    @SaCheckPermission("ops:store:edit")
    @Log(title = "门店管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody BizStoreBo bo) {
        return toAjax(storeService.updateStore(bo));
    }

    /**
     * 门店启停切换
     *
     * @param bo 入参（storeId + status）
     */
    @SaCheckPermission("ops:store:edit")
    @Log(title = "门店管理", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping("/changeStatus")
    public R<Void> changeStatus(@RequestBody BizStoreBo bo) {
        return toAjax(storeService.changeStatus(bo));
    }

    /**
     * 未绑定门店的部门下拉（新增弹窗数据源）
     */
    @SaCheckPermission("ops:store:add")
    @GetMapping("/unbound-depts")
    public R<List<RemoteDeptVo>> unboundDepts() {
        return R.ok(storeService.selectUnboundDepts());
    }

    /**
     * 门店精简下拉（订单/优惠券/充值页筛选共用，仅启用门店）
     */
    @SaCheckPermission("ops:store:list")
    @GetMapping("/optionselect")
    public R<List<BizStoreVo>> optionselect() {
        return R.ok(storeService.selectEnabledStoreList());
    }

    /**
     * 门店小程序码（P1 返回 scene 占位）
     *
     * @param storeId 门店ID
     */
    @SaCheckPermission("ops:store:query")
    @GetMapping("/{storeId}/qrcode")
    public R<BizStoreQrcodeVo> qrcode(@PathVariable Long storeId) {
        return R.ok(storeService.queryQrcode(storeId));
    }

}
