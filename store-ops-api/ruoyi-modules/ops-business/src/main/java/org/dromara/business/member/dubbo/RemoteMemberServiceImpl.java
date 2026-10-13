package org.dromara.business.member.dubbo;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.business.member.constant.OpsMemberConstants;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.ops.api.RemoteMemberService;
import org.dromara.ops.api.domain.vo.RemoteMemberVo;
import org.springframework.dao.DuplicateKeyException;

/**
 * 会员远程服务实现（提供方，ruoyi-auth mp 登录链路）
 *
 * <p>全部按入参 tenantId 以 TenantHelper.dynamic 包裹（mp 无登录态，租户插件不自动过滤）；
 * 查询显式比对 storeId（门店级隔离，PRD 6.1）。</p>
 *
 * @author store-ops
 */
@RequiredArgsConstructor
@DubboService
public class RemoteMemberServiceImpl implements RemoteMemberService {

    private final BizMemberMapper baseMapper;
    private final IBizStoreService storeService;

    @Override
    public RemoteMemberVo selectByOpenId(String tenantId, Long storeId, String openId) {
        return TenantHelper.dynamic(tenantId, () -> {
            BizMember member = baseMapper.selectOne(new LambdaQueryWrapper<BizMember>()
                .eq(BizMember::getStoreId, storeId)
                .eq(BizMember::getOpenId, openId)
                .last("LIMIT 1"));
            return toVo(member);
        });
    }

    @Override
    public RemoteMemberVo selectByMobile(String tenantId, Long storeId, String mobile) {
        return TenantHelper.dynamic(tenantId, () -> {
            BizMember member = baseMapper.selectOne(new LambdaQueryWrapper<BizMember>()
                .eq(BizMember::getStoreId, storeId)
                .eq(BizMember::getMobile, mobile)
                .last("LIMIT 1"));
            return toVo(member);
        });
    }

    @Override
    public void bindOpenId(String tenantId, Long memberId, String openId) {
        TenantHelper.dynamic(tenantId, () -> {
            BizMember member = baseMapper.selectById(memberId);
            if (member == null) {
                throw new ServiceException("会员不存在");
            }
            if (!StringUtils.equals(member.getOpenId(), openId)) {
                member.setOpenId(openId);
                try {
                    baseMapper.updateById(member);
                } catch (DuplicateKeyException e) {
                    // uk_tenant_store_open 冲突：该 open_id 已被同店另一档案占用（换微信场景，E5/T2 本期人工处理）
                    throw new ServiceException("该微信已绑定本店其他手机号档案，请联系店家处理");
                }
            }
            return null;
        });
    }

    @Override
    public RemoteMemberVo registerMember(String tenantId, Long storeId, String mobile,
                                         String openId, String nickName, String avatar) {
        return TenantHelper.dynamic(tenantId, () -> {
            BizStoreVo store = storeService.queryStoreById(storeId);
            if (store == null) {
                throw new ServiceException("门店不存在");
            }
            BizMember member = new BizMember();
            member.setStoreId(storeId);
            member.setDeptId(store.getDeptId());
            member.setMobile(mobile);
            member.setOpenId(openId);
            member.setNickName(nickName);
            member.setAvatar(avatar);
            member.setMemberNo(generateMemberNo(storeId));
            try {
                baseMapper.insert(member);
            } catch (DuplicateKeyException e) {
                String message = StringUtils.defaultString(e.getMessage());
                if (message.contains("uk_tenant_store_open")) {
                    throw new ServiceException("该微信已绑定本店其他手机号档案，请联系店家处理");
                }
                if (message.contains("uk_tenant_store_mobile")) {
                    throw new ServiceException("该手机号已在本店建档");
                }
                // 卡号撞号（seq 漂移）重试一次
                member.setMemberNo(generateMemberNo(storeId));
                try {
                    baseMapper.insert(member);
                } catch (DuplicateKeyException ex) {
                    throw new ServiceException("会员建档失败，请重试");
                }
            }
            return toVo(member);
        });
    }

    /**
     * 卡号 = 门店序号(4位) + 店内序列(4位)，Redis INCR + 唯一索引兜底（详设 5.1）
     */
    private String generateMemberNo(Long storeId) {
        Integer storeSeq = storeService.selectStoreSeq(storeId);
        if (storeSeq == null) {
            throw new ServiceException("门店不存在，无法生成会员卡号");
        }
        long seq = RedisUtils.incrAtomicValue(OpsMemberConstants.MEMBER_NO_SEQ_KEY + storeId);
        return String.format("%04d%04d", storeSeq, seq % 10000);
    }

    private RemoteMemberVo toVo(BizMember member) {
        if (member == null) {
            return null;
        }
        return BeanUtil.copyProperties(member, RemoteMemberVo.class);
    }

}
