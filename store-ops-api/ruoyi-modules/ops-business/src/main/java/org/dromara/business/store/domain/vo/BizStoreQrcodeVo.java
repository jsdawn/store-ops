package org.dromara.business.store.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店小程序码视图对象
 *
 * <p>P1 阶段占位交付：scene 参数已定死为 store_id，真实小程序码随 P2 小程序端实装。</p>
 *
 * @author store-ops
 */
@Data
public class BizStoreQrcodeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称
     */
    private String storeName;

    /**
     * 小程序码 scene 参数（= store_id，满足微信 getUnlimitedQRCode 32 字符限制）
     */
    private String scene;

    /**
     * 小程序码图片地址（P1 为 null 占位，P2 实装后返回图片）
     */
    private String imageUrl;

    /**
     * 占位说明
     */
    private String tip;

}
