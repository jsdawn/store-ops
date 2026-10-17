package org.dromara.business.order.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.dromara.business.coupon.domain.vo.CouponOptionVo;
import org.dromara.business.coupon.service.IBizCouponService;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.business.member.service.IBizMemberService;
import org.dromara.business.order.constant.OpsOrderConstants;
import org.dromara.business.order.domain.BizOrder;
import org.dromara.business.order.domain.BizOrderItem;
import org.dromara.business.order.domain.bo.BizOrderCreateBo;
import org.dromara.business.order.domain.bo.BizOrderItemBo;
import org.dromara.business.order.domain.bo.BizOrderPayBo;
import org.dromara.business.order.domain.bo.BizOrderQueryBo;
import org.dromara.business.order.domain.vo.BizOrderVo;
import org.dromara.business.order.domain.vo.OrderDailySummaryVo;
import org.dromara.business.order.domain.vo.OrderTodayVo;
import org.dromara.business.order.mapper.BizOrderItemMapper;
import org.dromara.business.order.mapper.BizOrderMapper;
import org.dromara.business.order.service.IBizOrderService;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.satoken.utils.LoginHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单 服务层实现
 *
 * <p>收银两段式：开单落待收款（明细不可变，改明细=作废重开）→ 收款事务内扣余额/落积分 +
 * 状态条件 UPDATE（并发互斥点）；错账处理：待收款作废、已完成余额收款当日冲正（D4）。
 * 会员资产走 member 门面、券走 coupon 门面 stub（D3），本域不触碰他域 Mapper。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@Service
public class BizOrderServiceImpl implements IBizOrderService {

    private final BizOrderMapper baseMapper;
    private final BizOrderItemMapper itemMapper;
    private final IBizMemberService memberService;
    private final IBizCouponService couponService;
    private final IBizStoreService storeService;

    /**
     * 分页查询订单列表（数据权限过滤在 mapper 层）
     */
    @Override
    public TableDataInfo<BizOrderVo> selectPageOrderList(BizOrderQueryBo bo, PageQuery pageQuery) {
        Page<BizOrderVo> page = baseMapper.selectPageOrderList(pageQuery.build(), buildQueryWrapper(bo));
        fillStoreInfo(page.getRecords());
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<BizOrder> buildQueryWrapper(BizOrderQueryBo bo) {
        LambdaQueryWrapper<BizOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ObjectUtil.isNotNull(bo.getStoreId()), BizOrder::getStoreId, bo.getStoreId())
            .eq(StringUtils.isNotBlank(bo.getStatus()), BizOrder::getStatus, bo.getStatus())
            .eq(StringUtils.isNotBlank(bo.getPayType()), BizOrder::getPayType, bo.getPayType())
            .like(StringUtils.isNotBlank(bo.getOrderNo()), BizOrder::getOrderNo, bo.getOrderNo())
            .like(StringUtils.isNotBlank(bo.getMemberMobile()), BizOrder::getMemberMobile, bo.getMemberMobile())
            .ge(ObjectUtil.isNotNull(bo.getBeginTime()), BizOrder::getCreateTime, bo.getBeginTime())
            .le(ObjectUtil.isNotNull(bo.getEndTime()), BizOrder::getCreateTime, bo.getEndTime())
            .orderByDesc(BizOrder::getCreateTime);
        return wrapper;
    }

    /**
     * 订单详情（含明细行）
     */
    @Override
    public BizOrderVo queryById(Long orderId) {
        BizOrderVo vo = baseMapper.selectOrderVoById(orderId);
        if (vo == null) {
            return null;
        }
        vo.setItems(itemMapper.selectByOrderId(orderId));
        fillStoreInfo(List.of(vo));
        return vo;
    }

    /**
     * 今日流水汇总（营业额/单量仅算已完成；列表含待收款带状态，审查结论⑦）
     */
    @Override
    public OrderTodayVo queryToday(Long storeId) {
        if (storeId == null || storeService.queryStoreById(storeId) == null) {
            throw new ServiceException("门店不存在或不属于当前租户");
        }
        Date begin = DateUtil.beginOfDay(new Date());
        OrderTodayVo vo = new OrderTodayVo();
        OrderDailySummaryVo summary = baseMapper.selectDailySummary(storeId, begin);
        vo.setTotalAmount(summary != null && summary.getTotalAmount() != null
            ? summary.getTotalAmount() : BigDecimal.ZERO);
        vo.setOrderCount(summary != null && summary.getOrderCount() != null ? summary.getOrderCount() : 0);
        List<BizOrderVo> list = baseMapper.selectOrderList(new LambdaQueryWrapper<BizOrder>()
            .eq(BizOrder::getStoreId, storeId)
            .in(BizOrder::getStatus, List.of(OpsOrderConstants.STATUS_PENDING, OpsOrderConstants.STATUS_PAID))
            .ge(BizOrder::getCreateTime, begin)
            .orderByDesc(BizOrder::getCreateTime)
            .last("LIMIT " + OpsOrderConstants.TODAY_LIST_LIMIT));
        fillStoreInfo(list);
        vo.setList(list);
        return vo;
    }

    /**
     * 开单（→待收款）：服务端逐行计算行金额/总额，pay_amount 恒 >0（杜绝 0 元单，详设 5.1）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Long createByBo(BizOrderCreateBo bo) {
        BizStoreVo store = checkStoreEnabled(bo.getStoreId());
        String memberName = null;
        String memberMobile = null;
        if (bo.getMemberId() != null) {
            BizMemberVo member = memberService.queryById(bo.getMemberId());
            if (member == null || !bo.getStoreId().equals(member.getStoreId())) {
                throw new ServiceException("会员不存在或不属于当前门店");
            }
            memberName = member.getNickName();
            memberMobile = member.getMobile();
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<BizOrderItem> items = new java.util.ArrayList<>(bo.getItems().size());
        for (BizOrderItemBo itemBo : bo.getItems()) {
            BigDecimal lineAmount = itemBo.getUnitPrice().multiply(BigDecimal.valueOf(itemBo.getQuantity()));
            totalAmount = totalAmount.add(lineAmount);
            BizOrderItem item = new BizOrderItem();
            item.setItemName(itemBo.getItemName());
            item.setUnitPrice(itemBo.getUnitPrice());
            item.setQuantity(itemBo.getQuantity());
            item.setLineAmount(lineAmount);
            items.add(item);
        }
        // 本期无券（coupon_amount=0）；券域 P3 接入后此处改扣减并复核券可用性
        BigDecimal payAmount = totalAmount;
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("订单金额必须大于0");
        }
        BizOrder order = new BizOrder();
        order.setStoreId(bo.getStoreId());
        order.setDeptId(store.getDeptId());
        order.setOrderNo(generateOrderNo());
        order.setMemberId(bo.getMemberId());
        order.setMemberName(memberName);
        order.setMemberMobile(memberMobile);
        order.setTotalAmount(totalAmount);
        order.setCouponAmount(BigDecimal.ZERO);
        order.setPayAmount(payAmount);
        order.setStatus(OpsOrderConstants.STATUS_PENDING);
        order.setBalanceReversed(OpsOrderConstants.REVERSED_NO);
        // 操作人：管理台走 PC 登录态；mp 员工端显式传入（无 PC 会话，决策 D11）
        Long operatorId = bo.getOperatorId() != null ? bo.getOperatorId() : LoginHelper.getUserId();
        String operatorName = StringUtils.isNotBlank(bo.getOperatorName()) ? bo.getOperatorName() : LoginHelper.getUsername();
        order.setOperatorId(operatorId);
        order.setOperatorName(operatorName);
        baseMapper.insert(order);
        items.forEach(item -> {
            item.setOrderId(order.getOrderId());
            item.setStoreId(bo.getStoreId());
        });
        itemMapper.insertBatch(items);
        return order.getOrderId();
    }

    /**
     * 确认收款（核心事务，详设 5.2）：余额收款整单成败（同事务回滚）、会员单 1:1 积分、
     * 状态条件 UPDATE 防并发收款；券复核/核销 P3 前跳过（stub 语义位已留）。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean pay(Long orderId, BizOrderPayBo bo) {
        BizOrderVo order = baseMapper.selectOrderVoById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或无权操作");
        }
        if (OpsOrderConstants.STATUS_PAID.equals(order.getStatus())) {
            throw new ServiceException("该订单已收款");
        }
        if (OpsOrderConstants.STATUS_VOIDED.equals(order.getStatus())) {
            throw new ServiceException("该订单已作废");
        }
        checkStoreEnabled(order.getStoreId());
        if (OpsOrderConstants.PAY_TYPE_BALANCE.equals(bo.getPayType())) {
            if (order.getMemberId() == null) {
                throw new ServiceException("散客单不支持余额收款");
            }
            // 余额不足整单失败：同事务回滚，杜绝「订单成功余额没扣」（PRD 5.3.1）
            if (!memberService.deductBalance(order.getMemberId(), order.getPayAmount(), order.getOrderNo())) {
                throw new ServiceException("余额不足，整单失败");
            }
        }
        BigDecimal earnedPoint = BigDecimal.ZERO;
        if (order.getMemberId() != null) {
            // 会员单按券后实付 1:1 落积分（详设 5.4）；散客不产生
            earnedPoint = order.getPayAmount();
            memberService.settleOrder(order.getMemberId(), order.getPayAmount(), order.getOrderNo());
        }
        // 券复核 + 核销（P3 前无券跳过；接入点：couponService.writeOff(couponId, orderId) 失败整单失败）
        int rows = baseMapper.markPaid(orderId, bo.getPayType(), earnedPoint);
        if (rows == 0) {
            throw new ServiceException("订单状态已变化，请刷新");
        }
        return true;
    }

    /**
     * 作废（仅待收款；无任何资产变动，详设 5.3）
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean voidOrder(Long orderId, String reason) {
        BizOrderVo order = baseMapper.selectOrderVoById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或无权操作");
        }
        if (OpsOrderConstants.STATUS_PAID.equals(order.getStatus())) {
            throw new ServiceException("已收款订单不可作废");
        }
        if (OpsOrderConstants.STATUS_VOIDED.equals(order.getStatus())) {
            throw new ServiceException("该订单已作废，请勿重复操作");
        }
        checkStoreEnabled(order.getStoreId());
        int rows = baseMapper.markVoid(orderId, reason);
        if (rows == 0) {
            throw new ServiceException("订单状态已变化，请刷新");
        }
        return true;
    }

    /**
     * 追加备注（仅已完成；服务端拼时间戳+操作人，累计 255 上限，详设 5.7 / D6）
     */
    @Override
    public Boolean appendRemark(Long orderId, String remark) {
        BizOrderVo order = baseMapper.selectOrderVoById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或无权操作");
        }
        if (!OpsOrderConstants.STATUS_PAID.equals(order.getStatus())) {
            throw new ServiceException("仅已完成订单可备注");
        }
        String append = buildRemarkAppend(LoginHelper.getUsername(), remark);
        int existing = order.getRemark() == null ? 0 : order.getRemark().length();
        if (existing + append.length() > OpsOrderConstants.REMARK_MAX_LENGTH) {
            throw new ServiceException("备注累计已达上限（255 字），请精简内容");
        }
        int rows = baseMapper.appendRemark(orderId, append);
        if (rows == 0) {
            throw new ServiceException("订单状态已变化，请刷新");
        }
        return true;
    }

    /**
     * 余额收款错账冲正（详设 5.6 / D4）：校验四重（已完成/余额收款/当日/未冲正）→
     * 加回余额（member 门面）→ 标志位条件 UPDATE 防双冲 → 备注自动记冲正痕迹；不动积分/券/状态。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Boolean balanceReversal(Long orderId, String reason) {
        BizOrderVo order = baseMapper.selectOrderVoById(orderId);
        if (order == null) {
            throw new ServiceException("订单不存在或无权操作");
        }
        if (!OpsOrderConstants.STATUS_PAID.equals(order.getStatus())) {
            throw new ServiceException("仅已完成订单可冲正");
        }
        if (!OpsOrderConstants.PAY_TYPE_BALANCE.equals(order.getPayType())) {
            throw new ServiceException("仅余额收款订单可冲正");
        }
        if (order.getCreateTime() == null || order.getCreateTime().before(DateUtil.beginOfDay(new Date()))) {
            throw new ServiceException("仅当日订单可冲正");
        }
        if (order.getMemberId() == null) {
            throw new ServiceException("订单会员信息缺失，无法冲正");
        }
        int rows = baseMapper.markBalanceReversed(orderId);
        if (rows == 0) {
            throw new ServiceException("该订单已冲正过");
        }
        memberService.reversalConsume(order.getMemberId(), order.getPayAmount(), order.getOrderNo());
        // 冲正痕迹自动入备注（主流程成功后；备注超长不阻断冲正，忽略失败）
        baseMapper.appendRemark(orderId, buildRemarkAppend(LoginHelper.getUsername(),
            "余额冲正 +" + order.getPayAmount() + "元（原因：" + reason + "）"));
        return true;
    }

    /**
     * 收银选券候选（券域 P3 前恒空）
     */
    @Override
    public List<CouponOptionVo> listCouponOptions(Long memberId, Long storeId, BigDecimal totalAmount) {
        if (memberId == null) {
            return List.of();
        }
        return couponService.listUsableForOrder(memberId, storeId, totalAmount);
    }

    /**
     * 校验门店存在、属本租户、未停用（开单/收款/作废拦截停用店；查询与冲正不受影响，详设 5.5）
     */
    private BizStoreVo checkStoreEnabled(Long storeId) {
        BizStoreVo store = storeService.queryStoreById(storeId);
        if (store == null) {
            throw new ServiceException("门店不存在或不属于当前租户");
        }
        if (!SystemConstants.NORMAL.equals(store.getStatus())) {
            throw new ServiceException("门店已停用，不可办理会员业务");
        }
        return store;
    }

    /**
     * 生成订单号：ORD + yyMMdd + 雪花后6位（与充值号 RC 同构，详设 5.8）
     */
    private String generateOrderNo() {
        String datePart = DateUtil.format(new Date(), "yyMMdd");
        long tail = IdUtil.getSnowflakeNextId() % 1000000;
        return OpsOrderConstants.ORDER_NO_PREFIX + datePart + String.format("%06d", tail);
    }

    /**
     * 构建追加式备注片段：[MM-dd HH:mm 操作人] 内容
     */
    private String buildRemarkAppend(String operatorName, String content) {
        return "[" + DateUtil.format(new Date(), "MM-dd HH:mm") + " " + operatorName + "] " + content + "\n";
    }

    /**
     * 批量补齐 storeName（store 域门面，停用门店名称也返回）
     */
    private void fillStoreInfo(List<BizOrderVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<Long> storeIds = list.stream().map(BizOrderVo::getStoreId).distinct().collect(Collectors.toList());
        Map<Long, String> nameMap = storeService.selectStoreNameMap(storeIds);
        list.forEach(vo -> vo.setStoreName(nameMap.get(vo.getStoreId())));
    }

}
