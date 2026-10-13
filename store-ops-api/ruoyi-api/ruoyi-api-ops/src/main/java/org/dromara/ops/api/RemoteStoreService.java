package org.dromara.ops.api;

import org.dromara.ops.api.domain.vo.RemoteStoreVo;

/**
 * 门店远程服务（ruoyi-auth mp 登录链路跨服务查询，提供方：ops-business）
 *
 * <p>biz_store 挂租户插件过滤，mp 请求无登录态；实现侧以 TenantHelper.ignore
 * 查询后回填 tenantId，调用方不得依赖当前线程租户上下文。</p>
 *
 * @author store-ops
 */
public interface RemoteStoreService {

    /**
     * 按 ID 查门店（含租户ID，忽略租户过滤）
     *
     * @param storeId 门店ID
     * @return 门店信息，未命中返回 null
     */
    RemoteStoreVo selectByStoreId(Long storeId);

}
