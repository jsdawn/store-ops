package org.dromara.business.member.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.dromara.business.member.domain.BizRechargeLog;
import org.dromara.business.member.domain.vo.BizRechargeLogVo;
import org.dromara.common.mybatis.annotation.DataColumn;
import org.dromara.common.mybatis.annotation.DataPermission;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 充值流水 数据层
 *
 * @author store-ops
 */
public interface BizRechargeLogMapper extends BaseMapperPlus<BizRechargeLog, BizRechargeLogVo> {

    /**
     * 分页查询充值流水（数据权限：dept_id 冗余列）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default Page<BizRechargeLogVo> selectPageRechargeList(Page<BizRechargeLog> page, @Param(Constants.WRAPPER) Wrapper<BizRechargeLog> queryWrapper) {
        return this.selectVoPage(page, queryWrapper);
    }

    /**
     * 按 ID 查充值流水（带数据权限：范围外流水视为不存在，冲正前置校验）
     */
    @DataPermission({
        @DataColumn(key = "deptName", value = "dept_id"),
        @DataColumn(key = "userName", value = "create_by")
    })
    default BizRechargeLogVo selectRechargeVoById(Long rechargeId) {
        return this.selectVoById(rechargeId);
    }

}
