package org.dromara.ops.api.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 门店远程视图对象（跨服务传输，biz_store）
 *
 * <p>供 ruoyi-auth mp 登录链路解析租户与门店状态使用；查询方只携带 storeId，
 * 服务端以 TenantHelper.ignore 查询后回填 tenantId（mp 请求无登录态）。</p>
 *
 * @author store-ops
 */
@Data
public class RemoteStoreVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 门店名称
     */
    private String storeName;

    /**
     * 门店地址
     */
    private String address;

    /**
     * 门店绑定部门ID（门店与部门一一对应，员工身份识别按此匹配管理用户，详设 4.6）
     */
    private Long deptId;

    /**
     * 状态（0启用 1停用）
     */
    private String status;

}
