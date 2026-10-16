package org.dromara.business.member.constant;

/**
 * member 域常量（字典值与卡号序列 key）
 *
 * <p>字典：member_pay_type / balance_log_type / point_log_type（sys_dict_data 初始化见 ops_member.sql）。</p>
 *
 * @author store-ops
 */
public class OpsMemberConstants {

    /**
     * 会员卡号店内序列 Redis key 前缀（完整 key = 前缀 + storeId，INCR 自增）
     */
    public static final String MEMBER_NO_SEQ_KEY = "ops:member:no:";

    /**
     * 充值流水号前缀（RC + yyMMdd + 雪花后6位，详设 D5）
     */
    public static final String RECHARGE_NO_PREFIX = "RC";

    /**
     * 充值流水状态：有效
     */
    public static final String RECHARGE_STATUS_VALID = "0";

    /**
     * 充值流水状态：已冲正
     */
    public static final String RECHARGE_STATUS_REVERSED = "1";

    /**
     * 余额流水类型：充值
     */
    public static final String BALANCE_TYPE_RECHARGE = "1";

    /**
     * 余额流水类型：消费
     */
    public static final String BALANCE_TYPE_CONSUME = "2";

    /**
     * 余额流水类型：冲正
     */
    public static final String BALANCE_TYPE_REVERSAL = "3";

    /**
     * 积分流水类型：消费获得
     */
    public static final String POINT_TYPE_EARN = "1";

    /**
     * 积分流水类型：兑换消耗
     */
    public static final String POINT_TYPE_EXCHANGE = "2";

    /**
     * 收银选会员返回条数上限（详设 4.2 #4）
     */
    public static final int SEARCH_LIMIT = 20;

    /**
     * mp 员工端充值提交凭证 Redis key 前缀（完整 key = 前缀 + uuid，value = memberId|storeId，
     * 一次有效，详设 5.2 校验链第 4 步「幂等 token 一次有效」）
     */
    public static final String MP_RECHARGE_TOKEN_KEY = "ops:mp:recharge:token:";

    /**
     * mp 员工端充值提交凭证有效期（分钟）
     */
    public static final long MP_RECHARGE_TOKEN_TTL_MINUTES = 5;

}
