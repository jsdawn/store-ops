package org.dromara.business.member.service;

import org.dromara.business.member.domain.bo.BizRechargeLogBo;
import org.dromara.business.member.domain.vo.BizRechargeLogVo;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;

/**
 * 充值流水 服务接口
 *
 * @author store-ops
 */
public interface IBizRechargeLogService {

    /**
     * 分页查询充值流水（筛选 storeId/mobile/nickName/payType/status/时间区间，数据权限过滤）
     *
     * @param bo        筛选条件
     * @param pageQuery 分页参数
     * @return 充值流水分页（带 storeName、会员昵称/手机号、到账合计）
     */
    TableDataInfo<BizRechargeLogVo> selectPageRechargeList(BizRechargeLogBo bo, PageQuery pageQuery);

    /**
     * 充值冲正（仅管理台：仅当日、仅一次、余额充足；本地事务：扣余额 + 状态翻转 + 负向余额流水，详设 5.3）
     *
     * @param rechargeId 充值流水ID
     * @param reason     冲正原因（必填 ≤100 字，记入负向流水 remark）
     * @return 结果
     */
    Boolean reversal(Long rechargeId, String reason);

}
