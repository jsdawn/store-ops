package org.dromara.business.staff.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.staff.domain.vo.MpStaffHomeVo;
import org.dromara.business.staff.service.IMpStaffService;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.api.RemoteUserService;
import org.dromara.system.api.model.LoginUser;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * mp 员工端服务实现（小程序工作台）
 *
 * <p>身份复检：以识别口径实时重跑（会员手机号 × 门店对应部门下的管理用户，决策 D9），
 * 结果必须与 token 内 employeeId 一致——管理员停用、调岗、改手机号后旧 token 立即失效。</p>
 *
 * @author store-ops
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MpStaffServiceImpl implements IMpStaffService {

    private final BizMemberMapper memberMapper;
    private final BizStoreMapper storeMapper;

    @DubboReference
    private RemoteUserService remoteUserService;

    @Override
    public MpStaffHomeVo home(Long memberId, Long employeeId) {
        if (employeeId == null) {
            throw new ServiceException("当前账号不是门店员工");
        }
        BizMember member = TenantHelper.ignore(() -> memberMapper.selectById(memberId));
        if (member == null) {
            throw new ServiceException("会员不存在");
        }
        BizStore store = TenantHelper.ignore(() -> storeMapper.selectById(member.getStoreId()));
        if (store == null || !SystemConstants.NORMAL.equals(store.getStatus())) {
            throw new ServiceException("门店不存在或已停业");
        }
        // 实时复检员工身份：识别口径重跑（存在 + 启用 + 部门一致），并须与 token 声明一致
        String staffName = null;
        if (StringUtils.isNotBlank(member.getMobile()) && store.getDeptId() != null) {
            try {
                LoginUser user = remoteUserService.getUserInfoByPhonenumber(member.getMobile(), store.getTenantId());
                if (user != null && Objects.equals(store.getDeptId(), user.getDeptId())
                    && Objects.equals(employeeId, user.getUserId())) {
                    staffName = user.getNickname();
                }
            } catch (Exception e) {
                log.debug("mp 员工身份复检未通过：employeeId={}", employeeId);
            }
        }
        if (staffName == null) {
            throw new ServiceException("员工身份已失效，请重新登录后进入工作台");
        }
        MpStaffHomeVo vo = new MpStaffHomeVo();
        vo.setStoreId(store.getStoreId());
        vo.setStoreName(store.getStoreName());
        vo.setStaffName(staffName);
        return vo;
    }

}
