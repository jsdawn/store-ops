package org.dromara.business.staff.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 员工端工作台首页视图对象（GET /mp/staff/home 出参，详设 4.6）
 *
 * @author store-ops
 */
@Data
public class MpStaffHomeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 门店名称（工作台 Hero「轻语美发 · xx店」）
     */
    private String storeName;

    /**
     * 员工姓名（Hero「店员：xxx · 商家工作台」）
     */
    private String staffName;

}
