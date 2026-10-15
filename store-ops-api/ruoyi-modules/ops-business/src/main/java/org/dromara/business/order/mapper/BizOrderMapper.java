package org.dromara.business.order.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.business.order.domain.BizOrder;
import org.dromara.business.order.domain.vo.BizOrderVo;
import org.dromara.business.order.domain.vo.OrderDailySummaryVo;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 订单 数据层
 *
 * <p>状态流转（收款/作废/冲正）均为<b>条件 UPDATE 原子语句</b>（详设 5.2/5.3/5.6）：
 * 影响行数 = 0 即状态已被并发修改，回滚并友好提示，不用乐观锁。</p>
 *
 * @author store-ops
 */
public interface BizOrderMapper extends BaseMapperPlus<BizOrder, BizOrderVo> {

    /**
     * 分页查询订单列表（数据权限：dept_id 冗余列，店长本店及以下、租户管理员全部）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<BizOrderVo> selectPageOrderList(Page<BizOrder> page, @Param(Constants.WRAPPER) Wrapper<BizOrder> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 按 ID 查订单（带数据权限：越权/范围外返回 null，收款/作废/冲正写操作前置校验）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default BizOrderVo selectOrderVoById(Long orderId) {
        return this.selectVoById(orderId);
    }

    /**
     * 列表查询订单（带数据权限）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<BizOrderVo> selectOrderList(@Param(Constants.WRAPPER) Wrapper<BizOrder> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 确认收款：待收款 → 已完成（并发收款互斥点，影响行数 0 = 已被并发处理）
     */
    @Update("UPDATE biz_order SET status = '1', pay_type = #{payType}, earned_point = #{earnedPoint} "
        + "WHERE order_id = #{orderId} AND status = '0'")
    int markPaid(@Param("orderId") Long orderId, @Param("payType") String payType,
                 @Param("earnedPoint") BigDecimal earnedPoint);

    /**
     * 作废：待收款 → 已作废（并发双作废互斥点）
     */
    @Update("UPDATE biz_order SET status = '2', void_reason = #{voidReason} "
        + "WHERE order_id = #{orderId} AND status = '0'")
    int markVoid(@Param("orderId") Long orderId, @Param("voidReason") String voidReason);

    /**
     * 标记余额收款已冲正（防双冲互斥点）
     */
    @Update("UPDATE biz_order SET balance_reversed = '1' "
        + "WHERE order_id = #{orderId} AND balance_reversed = '0'")
    int markBalanceReversed(@Param("orderId") Long orderId);

    /**
     * 追加备注（仅已完成；服务端拼接 [MM-dd HH:mm 操作人] 内容，超长在服务层前置校验）
     */
    @Update("UPDATE biz_order SET remark = CONCAT(IFNULL(remark, ''), #{append}) "
        + "WHERE order_id = #{orderId} AND status = '1'")
    int appendRemark(@Param("orderId") Long orderId, @Param("append") String append);

    /**
     * 今日流水汇总（营业额/单量仅算已完成，审查结论⑦；租户隔离由插件追加）
     */
    @Select("SELECT IFNULL(SUM(pay_amount), 0) AS total_amount, COUNT(*) AS order_count "
        + "FROM biz_order WHERE store_id = #{storeId} AND status = '1' AND create_time >= #{startTime}")
    OrderDailySummaryVo selectDailySummary(@Param("storeId") Long storeId, @Param("startTime") Date startTime);

}
