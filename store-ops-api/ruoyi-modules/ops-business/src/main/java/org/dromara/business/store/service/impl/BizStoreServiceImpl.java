package org.dromara.business.store.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.domain.bo.BizStoreBo;
import org.dromara.business.store.domain.vo.BizStoreQrcodeVo;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.system.api.RemoteDeptService;
import org.dromara.system.api.domain.vo.RemoteDeptVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门店 服务层实现
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@Service
public class BizStoreServiceImpl implements IBizStoreService {

    private final BizStoreMapper baseMapper;

    @DubboReference
    private RemoteDeptService remoteDeptService;

    /**
     * 分页查询门店列表（管理台）
     */
    @Override
    public TableDataInfo<BizStoreVo> selectPageStoreList(BizStoreBo bo, PageQuery pageQuery) {
        Page<BizStoreVo> page = baseMapper.selectVoPage(pageQuery.build(), buildQueryWrapper(bo));
        return TableDataInfo.build(page);
    }

    private LambdaQueryWrapper<BizStore> buildQueryWrapper(BizStoreBo bo) {
        LambdaQueryWrapper<BizStore> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(bo.getStoreName()), BizStore::getStoreName, bo.getStoreName())
            .like(StringUtils.isNotBlank(bo.getAddress()), BizStore::getAddress, bo.getAddress())
            .eq(StringUtils.isNotBlank(bo.getStatus()), BizStore::getStatus, bo.getStatus())
            .orderByDesc(BizStore::getCreateTime);
        return wrapper;
    }

    /**
     * 按 ID 查门店
     */
    @Override
    public BizStoreVo queryStoreById(Long storeId) {
        return baseMapper.selectVoById(storeId);
    }

    /**
     * 按部门 ID 查门店
     */
    @Override
    public BizStoreVo queryStoreByDeptId(Long deptId) {
        return baseMapper.selectVoOne(new LambdaQueryWrapper<BizStore>().eq(BizStore::getDeptId, deptId));
    }

    /**
     * 查询启用门店精简列表
     */
    @Override
    public List<BizStoreVo> selectEnabledStoreList() {
        return baseMapper.selectVoList(new LambdaQueryWrapper<BizStore>()
            .select(BizStore::getStoreId, BizStore::getStoreName, BizStore::getDeptId)
            .eq(BizStore::getStatus, SystemConstants.NORMAL)
            .orderByDesc(BizStore::getCreateTime));
    }

    /**
     * 查询未绑定门店的部门下拉（仅二级部门：租户根部门直接子部门）
     *
     * <p>Dubbo 取本租户正常状态部门 → 过滤层级（根部门及其更深层级不可绑店）→ 差集已占用 dept_id。</p>
     */
    @Override
    public List<RemoteDeptVo> selectUnboundDepts() {
        List<RemoteDeptVo> depts = remoteDeptService.selectDeptsByList();
        if (CollUtil.isEmpty(depts)) {
            return depts;
        }
        Long rootDeptId = resolveRootDeptId(depts);
        Set<Long> boundDeptIds = baseMapper.selectList(new LambdaQueryWrapper<BizStore>().select(BizStore::getDeptId))
            .stream().map(BizStore::getDeptId).collect(Collectors.toSet());
        return depts.stream()
            .filter(dept -> !boundDeptIds.contains(dept.getDeptId()))
            .filter(dept -> rootDeptId != null && rootDeptId.equals(dept.getParentId()))
            .collect(Collectors.toList());
    }

    /**
     * 解析租户根部门 ID（parent_id=0，每租户唯一）
     */
    private Long resolveRootDeptId(List<RemoteDeptVo> depts) {
        return depts.stream()
            .filter(dept -> Long.valueOf(0L).equals(dept.getParentId()))
            .map(RemoteDeptVo::getDeptId)
            .findFirst()
            .orElse(null);
    }

    /**
     * 新增门店
     */
    @Override
    public Boolean insertStore(BizStoreBo bo) {
        checkDeptAvailable(bo.getDeptId());
        checkDeptNotBound(bo.getDeptId(), null);
        checkNameUnique(bo.getStoreName(), null);
        BizStore store = MapstructUtils.convert(bo, BizStore.class);
        try {
            return baseMapper.insert(store) > 0;
        } catch (DuplicateKeyException e) {
            // 并发兜底：按命中唯一键转友好提示
            throw new ServiceException(resolveDuplicateMessage(e));
        }
    }

    /**
     * 修改门店（dept_id 只读不接收）
     */
    @Override
    public Boolean updateStore(BizStoreBo bo) {
        BizStoreVo exist = queryStoreById(bo.getStoreId());
        if (exist == null) {
            throw new ServiceException("门店不存在或已被删除");
        }
        checkNameUnique(bo.getStoreName(), bo.getStoreId());
        BizStore store = MapstructUtils.convert(bo, BizStore.class);
        // 换绑部门本期禁止（详设 5.3），防止前端传入污染
        store.setDeptId(null);
        store.setTenantId(null);
        try {
            return baseMapper.updateById(store) > 0;
        } catch (DuplicateKeyException e) {
            throw new ServiceException(resolveDuplicateMessage(e));
        }
    }

    /**
     * 启停切换
     */
    @Override
    public Boolean changeStatus(BizStoreBo bo) {
        if (!SystemConstants.NORMAL.equals(bo.getStatus()) && !SystemConstants.DISABLE.equals(bo.getStatus())) {
            throw new ServiceException("非法的状态值");
        }
        BizStoreVo exist = queryStoreById(bo.getStoreId());
        if (exist == null) {
            throw new ServiceException("门店不存在或已被删除");
        }
        return baseMapper.update(null, new LambdaUpdateWrapper<BizStore>()
            .set(BizStore::getStatus, bo.getStatus())
            .eq(BizStore::getStoreId, bo.getStoreId())) > 0;
    }

    /**
     * 查询门店小程序码（P1 占位）
     */
    @Override
    public BizStoreQrcodeVo queryQrcode(Long storeId) {
        BizStoreVo store = queryStoreById(storeId);
        if (store == null) {
            throw new ServiceException("门店不存在或已被删除");
        }
        BizStoreQrcodeVo vo = new BizStoreQrcodeVo();
        vo.setStoreId(storeId);
        vo.setStoreName(store.getStoreName());
        vo.setScene(String.valueOf(storeId));
        vo.setImageUrl(null);
        vo.setTip("小程序码随小程序端实装后生成（scene=store_id），当前为占位返回");
        return vo;
    }

    /**
     * 校验部门：存在、属本租户、未停用、层级合规（仅根部门直接子部门可绑店）
     *
     * <p>Dubbo 单查，租户插件隔离，跨租户 deptId 返回 null</p>
     */
    private void checkDeptAvailable(Long deptId) {
        if (ObjectUtil.isNull(deptId)) {
            throw new ServiceException("所属部门不能为空");
        }
        RemoteDeptVo dept = remoteDeptService.selectDeptById(deptId);
        if (dept == null) {
            throw new ServiceException("部门不存在或不属于当前租户");
        }
        if (!SystemConstants.NORMAL.equals(dept.getStatus())) {
            throw new ServiceException("部门已停用，无法绑定门店");
        }
        Long rootDeptId = resolveRootDeptId(remoteDeptService.selectDeptsByList());
        if (rootDeptId == null || !rootDeptId.equals(dept.getParentId())) {
            throw new ServiceException("门店只能绑定租户下的一级部门（根部门的直接子部门）");
        }
    }

    /**
     * 校验部门未被占用（1 部门 1 门店，排除自身）
     */
    private void checkDeptNotBound(Long deptId, Long excludeStoreId) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BizStore>()
            .eq(BizStore::getDeptId, deptId)
            .ne(ObjectUtil.isNotNull(excludeStoreId), BizStore::getStoreId, excludeStoreId));
        if (exist) {
            throw new ServiceException("该部门已绑定门店，1 个部门只能绑定 1 家门店");
        }
    }

    /**
     * 校验门店名称租户内唯一（排除自身）
     */
    private void checkNameUnique(String storeName, Long excludeStoreId) {
        boolean exist = baseMapper.exists(new LambdaQueryWrapper<BizStore>()
            .eq(BizStore::getStoreName, storeName)
            .ne(ObjectUtil.isNotNull(excludeStoreId), BizStore::getStoreId, excludeStoreId));
        if (exist) {
            throw new ServiceException("门店名称'" + storeName + "'已存在");
        }
    }

    /**
     * 按命中唯一键解析并发冲突提示
     */
    private String resolveDuplicateMessage(DuplicateKeyException e) {
        String message = StringUtils.defaultString(e.getMessage());
        if (message.contains("uk_tenant_dept")) {
            return "该部门已绑定门店，1 个部门只能绑定 1 家门店";
        }
        if (message.contains("uk_tenant_name")) {
            return "门店名称已存在";
        }
        return "数据库中已存在该记录，请联系管理员确认";
    }

}
