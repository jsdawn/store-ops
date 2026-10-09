package org.dromara.business.store.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.business.store.domain.BizStore;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 门店业务对象 biz_store
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizStore.class, reverseConvertGenerate = false)
public class BizStoreBo extends BaseEntity {

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 绑定部门ID（新增必填；编辑表单部门只读，后端不接收）
     */
    private Long deptId;

    /**
     * 门店名称（租户内唯一）
     */
    @NotBlank(message = "门店名称不能为空")
    @Size(min = 0, max = 20, message = "门店名称长度不能超过{max}个字符")
    private String storeName;

    /**
     * 门店地址
     */
    @Size(min = 0, max = 50, message = "门店地址长度不能超过{max}个字符")
    private String address;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 备注
     */
    @Size(min = 0, max = 100, message = "备注长度不能超过{max}个字符")
    private String remark;

}
