package org.dromara.business.member.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * mp 端切换门店 - 门店摘要（我的门店列表行，带资产摘要）
 *
 * @author store-ops
 */
@Data
public class MpStoreBriefVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID（雪花，BigNumberSerializer 字符串下发）
     */
    private Long storeId;

    /**
     * 门店名
     */
    private String storeName;

    /**
     * 会员卡号（该店档案）
     */
    private String memberNo;

    /**
     * 余额（元）
     */
    private BigDecimal balance;

    /**
     * 积分
     */
    private BigDecimal pointBalance;

    /**
     * 是否当前门店
     */
    private Boolean current;

}
