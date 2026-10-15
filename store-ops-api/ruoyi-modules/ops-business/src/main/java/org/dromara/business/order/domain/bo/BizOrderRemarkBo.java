package org.dromara.business.order.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单追加备注入参（仅已完成，错账备注用；单次内容 ≤100 字，累计 255 上限服务端校验）
 *
 * @author store-ops
 */
@Data
public class BizOrderRemarkBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 备注内容（追加式）
     */
    @NotBlank(message = "备注内容不能为空")
    @Size(min = 0, max = 100, message = "备注内容长度不能超过{max}个字符")
    private String remark;

}
