package org.dromara.business.member.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.domain.vo.BizMemberVo;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.math.BigDecimal;
import java.util.List;

/**
 * 会员 数据层
 *
 * <p>余额/积分扣减使用<b>条件 UPDATE 原子语句</b>（详设 5.4 / D4）：
 * 影响行数 = 0 即余额/积分不足，不用乐观锁；InnoDB 行锁保证不超扣、无负数。</p>
 *
 * @author store-ops
 */
public interface BizMemberMapper extends BaseMapperPlus<BizMember, BizMemberVo> {

    /**
     * 分页查询会员列表（数据权限：dept_id 冗余列，店长本店及以下、租户管理员全部）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<BizMemberVo> selectPageMemberList(Page<BizMember> page, @Param(Constants.WRAPPER) Wrapper<BizMember> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 按 ID 查会员（带数据权限：越权/范围外返回 null，充值等写操作前置校验）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default BizMemberVo selectMemberVoById(Long memberId) {
        return this.selectVoById(memberId);
    }

    /**
     * 列表查询会员（带数据权限，收银/充值选会员等场景）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default List<BizMemberVo> selectMemberList(@Param(Constants.WRAPPER) Wrapper<BizMember> queryWrapper) {
        return this.selectVoList(queryWrapper);
    }

    /**
     * 原子扣减余额（余额不足影响行数为 0）
     *
     * @param memberId 会员ID
     * @param amount   扣减金额（正数）
     * @return 影响行数
     */
    @Update("UPDATE biz_member SET balance = balance - #{amount} "
        + "WHERE member_id = #{memberId} AND balance >= #{amount} AND del_flag = '0'")
    int deductBalance(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);

    /**
     * 原子入账余额（充值加款，无条件加）
     *
     * @param memberId 会员ID
     * @param amount   入账金额（正数）
     * @return 影响行数
     */
    @Update("UPDATE biz_member SET balance = balance + #{amount} "
        + "WHERE member_id = #{memberId} AND del_flag = '0'")
    int addBalance(@Param("memberId") Long memberId, @Param("amount") BigDecimal amount);

    /**
     * 原子扣减积分（积分不足影响行数为 0）
     *
     * @param memberId 会员ID
     * @param points   扣减积分（正数）
     * @return 影响行数
     */
    @Update("UPDATE biz_member SET point_balance = point_balance - #{points} "
        + "WHERE member_id = #{memberId} AND point_balance >= #{points} AND del_flag = '0'")
    int deductPoint(@Param("memberId") Long memberId, @Param("points") BigDecimal points);

    /**
     * 原子加积分
     *
     * @param memberId 会员ID
     * @param points   加积分（正数）
     * @return 影响行数
     */
    @Update("UPDATE biz_member SET point_balance = point_balance + #{points} "
        + "WHERE member_id = #{memberId} AND del_flag = '0'")
    int addPoint(@Param("memberId") Long memberId, @Param("points") BigDecimal points);

    /**
     * 订单完成结算（单条原子语句：加积分 + 累计消费金额 + 累计消费次数，详设 D3）
     *
     * @param memberId 会员ID
     * @param points   加积分（= 券后实付金额，1:1 等额转化）
     * @param amount   累计消费增加额
     * @return 影响行数
     */
    @Update("UPDATE biz_member SET point_balance = point_balance + #{points}, "
        + "total_amount = total_amount + #{amount}, total_orders = total_orders + 1 "
        + "WHERE member_id = #{memberId} AND del_flag = '0'")
    int settleOrder(@Param("memberId") Long memberId, @Param("points") BigDecimal points, @Param("amount") BigDecimal amount);

}
