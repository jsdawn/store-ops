package org.dromara.business.store.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.business.store.domain.BizStore;
import org.dromara.common.translation.annotation.Translation;
import org.dromara.common.translation.constant.TransConstant;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 门店视图对象 biz_store
 *
 * @author store-ops
 */
@Data
@AutoMapper(target = BizStore.class)
public class BizStoreVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 绑定部门ID
     */
    private Long deptId;

    /**
     * 绑定部门名称
     */
    @Translation(type = TransConstant.DEPT_ID_TO_NAME, mapper = "deptId")
    private String deptName;

    /**
     * 门店名称
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
     * 创建时间
     */
    private Date createTime;

}
