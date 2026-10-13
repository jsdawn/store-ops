package org.dromara.business.store.dubbo;

import cn.hutool.core.bean.BeanUtil;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.ops.api.RemoteStoreService;
import org.dromara.ops.api.domain.vo.RemoteStoreVo;

import java.util.function.Supplier;

/**
 * 门店远程服务实现（提供方）
 *
 * <p>biz_store 挂租户插件过滤且 mp 请求无登录态，查询统一 TenantHelper.ignore
 * 包裹后由调用方按返回的 tenantId 自行选择后续隔离上下文（契约见 RemoteStoreService）。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@DubboService
public class RemoteStoreServiceImpl implements RemoteStoreService {

    private final BizStoreMapper baseMapper;

    @Override
    public RemoteStoreVo selectByStoreId(Long storeId) {
        if (storeId == null) {
            return null;
        }
        Supplier<BizStore> query = () -> baseMapper.selectById(storeId);
        BizStore store = TenantHelper.ignore(query);
        if (store == null) {
            return null;
        }
        RemoteStoreVo vo = BeanUtil.copyProperties(store, RemoteStoreVo.class);
        vo.setTenantId(store.getTenantId());
        return vo;
    }

}
