package org.dromara.business.order.constant;

/**
 * order 域常量（字典值与订单号前缀）
 *
 * <p>字典：order_status / order_pay_type（sys_dict_data 初始化见 ops_order.sql）。</p>
 *
 * @author store-ops
 */
public class OpsOrderConstants {

    /**
     * 订单号前缀（ORD + yyMMdd + 雪花后6位，详设 5.8）
     */
    public static final String ORDER_NO_PREFIX = "ORD";

    /**
     * 订单状态：待收款
     */
    public static final String STATUS_PENDING = "0";

    /**
     * 订单状态：已完成
     */
    public static final String STATUS_PAID = "1";

    /**
     * 订单状态：已作废
     */
    public static final String STATUS_VOIDED = "2";

    /**
     * 支付方式：余额收款
     */
    public static final String PAY_TYPE_BALANCE = "1";

    /**
     * 冲正标志：未冲正
     */
    public static final String REVERSED_NO = "0";

    /**
     * 冲正标志：已冲正
     */
    public static final String REVERSED_YES = "1";

    /**
     * 单笔订单明细行数上限（详设 4.2）
     */
    public static final int ITEM_MAX_ROWS = 50;

    /**
     * 追加式备注总长上限（详设 5.7 / D6）
     */
    public static final int REMARK_MAX_LENGTH = 255;

    /**
     * 今日流水列表条数上限（详设 4.2 #8）
     */
    public static final int TODAY_LIST_LIMIT = 50;

    /**
     * 订单号展示脱敏长度（尾 6 位，同充值号口径）
     */
    public static final int ORDER_NO_TAIL_LENGTH = 6;

    /**
     * mp 员工端开单提交凭证 Redis key 前缀（完整 key = 前缀 + uuid，value = memberId|storeId，
     * 一次有效，防弱网重复开单；与充值凭证同构，详设 5.1 第 6 步「@RepeatSubmit 幂等」的员工端等价物）
     */
    public static final String MP_ORDER_TOKEN_KEY = "ops:mp:order:token:";

    /**
     * mp 员工端开单提交凭证有效期（分钟，同充值凭证口径）
     */
    public static final long MP_ORDER_TOKEN_TTL_MINUTES = 5;

}
