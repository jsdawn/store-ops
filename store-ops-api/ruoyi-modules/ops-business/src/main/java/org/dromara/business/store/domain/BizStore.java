package org.dromara.business.store.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.tenant.core.TenantEntity;

import java.io.Serial;

/**
 * 门店对象 biz_store
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_store")
public class BizStore extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID
     */
    @TableId(value = "store_id")
    private Long storeId;

    /**
     * 绑定部门ID（sys_dept.dept_id，1 部门 1 门店）
     */
    private Long deptId;

    /**
     * 门店名称（租户内唯一）
     */
    private String storeName;

    /**
     * 门店地址
     */
    private String address;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志（0代表存在 1代表删除）
     */
    @TableLogic
    private String delFlag;

}
