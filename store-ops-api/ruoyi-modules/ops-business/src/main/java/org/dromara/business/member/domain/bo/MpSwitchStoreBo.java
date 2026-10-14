package org.dromara.business.member.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端切换门店入参
 *
 * @author store-ops
 */
@Data
public class MpSwitchStoreBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 目标门店ID（须为已注册会员的门店）
     */
    @NotNull(message = "门店ID不能为空")
    private Long storeId;

}
