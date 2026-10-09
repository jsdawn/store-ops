package org.dromara.business.store.service;

import org.dromara.business.store.domain.bo.BizStoreBo;
import org.dromara.business.store.domain.vo.BizStoreQrcodeVo;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.system.api.domain.vo.RemoteDeptVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * 门店 服务接口
 *
 * <p>store 域对外门面：member/order 等域消费门店能力一律走本接口（同模块本地调用），
 * 禁止跨包注入 BizStoreMapper（守拆分演进纪律）。</p>
 *
 * @author store-ops
 */
public interface IBizStoreService {

    /**
     * 分页查询门店列表（管理台）
     *
     * @param bo        查询条件（名称模糊、状态）
     * @param pageQuery 分页参数
     * @return 门店分页列表
     */
    TableDataInfo<BizStoreVo> selectPageStoreList(BizStoreBo bo, PageQuery pageQuery);

    /**
     * 按 ID 查门店（含 status/tenantId），供 member/order 域校验门店归属与状态
     *
     * @param storeId 门店ID
     * @return 门店信息
     */
    BizStoreVo queryStoreById(Long storeId);

    /**
     * 按部门 ID 查门店（员工登录后定位所属门店）
     *
     * @param deptId 部门ID
     * @return 门店信息
     */
    BizStoreVo queryStoreByDeptId(Long deptId);

    /**
     * 查询启用门店精简列表（订单/优惠券/充值页筛选共用）
     *
     * @return 启用门店列表
     */
    List<BizStoreVo> selectEnabledStoreList();

    /**
     * 查询未绑定门店的部门下拉（新增弹窗数据源）
     *
     * @return 未绑定部门列表
     */
    List<RemoteDeptVo> selectUnboundDepts();

    /**
     * 新增门店（校验链：部门存在且属本租户 → 部门未被占用 → 名称唯一 → 写入 → 并发兜底）
     *
     * @param bo 门店信息
     * @return 结果
     */
    Boolean insertStore(BizStoreBo bo);

    /**
     * 修改门店（名称/地址/状态/备注；dept_id 只读不接收）
     *
     * @param bo 门店信息
     * @return 结果
     */
    Boolean updateStore(BizStoreBo bo);

    /**
     * 启停切换
     *
     * @param bo     入参（storeId + status）
     * @return 结果
     */
    Boolean changeStatus(BizStoreBo bo);

    /**
     * 查询门店小程序码（P1 占位：返回 scene 值）
     *
     * @param storeId 门店ID
     * @return 小程序码信息
     */
    BizStoreQrcodeVo queryQrcode(Long storeId);

}
