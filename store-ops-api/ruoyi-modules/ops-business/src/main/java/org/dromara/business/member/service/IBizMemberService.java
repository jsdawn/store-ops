package org.dromara.business.member.service;

import org.dromara.business.member.domain.bo.BizMemberBo;
import org.dromara.business.member.domain.bo.BizRechargeBo;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员 服务接口
 *
 * <p>member 域对外门面：order/coupon 域消费会员资产能力（扣余额/结算积分/扣积分）
 * 一律走本接口，禁止跨包注入 member 域 Mapper（守拆分演进纪律）。
 * 资产变动均为条件 UPDATE 原子语句（详设 5.4 / D4），调用方在本地事务内编排（架构 9 节 P2）。</p>
 *
 * @author store-ops
 */
public interface IBizMemberService {

    /**
     * 分页查询会员列表（数据权限过滤：店长本店及以下、租户管理员全部）
     *
     * @param bo        筛选条件（storeId/mobile/memberNo/nickName）
     * @param pageQuery 分页参数
     * @return 会员分页列表（带 storeName、wxBound）
     */
    TableDataInfo<BizMemberVo> selectPageMemberList(BizMemberBo bo, PageQuery pageQuery);

    /**
     * 按 ID 查会员详情（档案 + 余额 + 积分 + 累计消费）
     *
     * @param memberId 会员ID
     * @return 会员详情
     */
    BizMemberVo queryById(Long memberId);

    /**
     * 线下登记建档（open_id 留空；mobile 本店唯一，重复/并发冲突均友好提示，前端跳转走 search 接口）
     *
     * @param bo 会员信息（storeId + mobile 必填）
     * @return 结果
     */
    Boolean insertByBo(BizMemberBo bo);

    /**
     * 收银/充值前选会员（keyword 手机号/卡号/昵称模糊 + storeId，前 20 条精简项）
     *
     * @param bo 查询条件（keyword、storeId）
     * @return 会员精简列表
     */
    List<BizMemberVo> selectSearchList(BizMemberBo bo);

    /**
     * 充值入账（校验链 + 本地事务：加余额 + 充值流水 + 余额流水，详设 5.2）
     *
     * @param bo 充值入参
     * @return 结果
     */
    Boolean recharge(BizRechargeBo bo);

    /**
     * 【域间门面】原子扣减余额（余额不足返回 false，不记流水不抛异常）
     *
     * <p>供 order 域余额收款调用；扣减成功同事务内记余额消费流水（biz_type=消费，biz_no=orderNo）。</p>
     *
     * @param memberId 会员ID
     * @param amount   扣减金额（正数）
     * @param orderNo  订单号
     * @return 是否扣减成功
     */
    boolean deductBalance(Long memberId, BigDecimal amount, String orderNo);

    /**
     * 【域间门面】订单完成结算：1:1 加积分 + 累计消费冗余更新 + 积分流水（详设 5.5 / D3）
     *
     * @param memberId  会员ID
     * @param payAmount 券后实付金额
     * @param orderNo   订单号
     */
    void settleOrder(Long memberId, BigDecimal payAmount, String orderNo);

    /**
     * 【域间门面】原子扣减积分（兑券，coupon 域调用；积分不足返回 false）
     *
     * @param memberId 会员ID
     * @param points   扣减积分（正数）
     * @param couponId 券实例ID（流水 biz_no）
     * @return 是否扣减成功
     */
    boolean deductPoints(Long memberId, BigDecimal points, Long couponId);

    /**
     * 【域间门面】余额收款冲正：原子加回余额 + 记余额冲正流水（biz_no=订单号，详设 order 5.6 / D4）
     *
     * <p>供 order 域错账冲正调用；不动积分、不动累计消费（PRD 6.5 简化规则）。</p>
     *
     * @param memberId 会员ID
     * @param amount   加回金额（正数）
     * @param orderNo  订单号
     */
    void reversalConsume(Long memberId, BigDecimal amount, String orderNo);

}
