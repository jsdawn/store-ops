package org.dromara.business.member.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.business.member.constant.OpsMemberConstants;
import org.dromara.business.member.domain.BizMember;
import org.dromara.business.member.domain.bo.MpBindMobileBo;
import org.dromara.business.member.domain.vo.MpLoginVo;
import org.dromara.business.member.domain.vo.MpMemberVo;
import org.dromara.business.member.mapper.BizMemberMapper;
import org.dromara.business.member.service.IMpMemberService;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.domain.vo.BizStoreVo;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.business.store.service.IBizStoreService;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.ops.api.constant.OpsMpConstants;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * mp 端会员服务实现（顾客微信端）
 *
 * <p>租户上下文：mp 请求无登录态，先 TenantHelper.ignore 取门店（解析 tenantId），
 * 再 TenantHelper.dynamic 包裹会员读写；归属校验显式比对 storeId，禁走框架数据权限（PRD 6.6）。
 * token 为不透明串：Redis key = {@link OpsMpConstants#MP_TOKEN_KEY} + token，value = memberId（契约）。</p>
 *
 * @author store-ops
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MpMemberServiceImpl implements IMpMemberService {

    private final BizMemberMapper baseMapper;
    private final BizStoreMapper storeMapper;
    private final IBizStoreService storeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MpLoginVo bindMobile(MpBindMobileBo bo) {
        BizStore store = TenantHelper.ignore(() -> storeMapper.selectById(bo.getStoreId()));
        if (store == null) {
            throw new ServiceException("门店不存在");
        }
        if (!SystemConstants.NORMAL.equals(store.getStatus())) {
            throw new ServiceException("门店暂停营业，暂不可建档");
        }
        String tenantId = store.getTenantId();
        BizMember member = TenantHelper.dynamic(tenantId, () -> bindOrRegister(store, bo));
        String token = issueToken(member.getMemberId());
        MpLoginVo loginVo = new MpLoginVo();
        loginVo.setToken(token);
        loginVo.setExpiresIn(OpsMpConstants.MP_TOKEN_TTL_DAYS * 24 * 3600);
        loginVo.setMember(toVo(member, store.getStoreName()));
        return loginVo;
    }

    @Override
    public MpMemberVo queryMember(Long memberId, Long storeId) {
        BizMember member = TenantHelper.ignore(() -> baseMapper.selectById(memberId));
        if (member == null) {
            throw new ServiceException("会员不存在");
        }
        if (storeId != null && !storeId.equals(member.getStoreId())) {
            throw new ServiceException("会员不属于当前门店");
        }
        BizStore store = TenantHelper.ignore(() -> storeMapper.selectById(member.getStoreId()));
        return toVo(member, store == null ? null : store.getStoreName());
    }

    /**
     * 按（门店, 手机号）匹配档案：已有 → 补绑/覆盖 open_id、更新昵称（编辑资料场景
     * open_id 相同也允许昵称变更）；无 → 新建
     */
    private BizMember bindOrRegister(BizStore store, MpBindMobileBo bo) {
        BizMember exist = baseMapper.selectOne(new LambdaQueryWrapper<BizMember>()
            .eq(BizMember::getStoreId, store.getStoreId())
            .eq(BizMember::getMobile, bo.getMobile()));
        if (exist != null) {
            boolean openIdChanged = !StringUtils.equals(exist.getOpenId(), bo.getOpenId());
            boolean nickChanged = StringUtils.isNotBlank(bo.getNickName())
                && !StringUtils.equals(exist.getNickName(), bo.getNickName());
            if (openIdChanged || nickChanged) {
                if (openIdChanged) {
                    exist.setOpenId(bo.getOpenId());
                }
                if (StringUtils.isNotBlank(bo.getNickName())) {
                    exist.setNickName(bo.getNickName());
                }
                try {
                    baseMapper.updateById(exist);
                } catch (DuplicateKeyException e) {
                    // uk_tenant_store_open 冲突：该 open_id 已被同店另一档案占用（换微信场景，E5/T2 本期人工处理）
                    throw new ServiceException("该微信已绑定本店其他手机号档案，请联系店家处理");
                }
            }
            return exist;
        }
        return registerMember(store, bo);
    }

    /**
     * 新建档案：卡号 = 门店序号(4位) + 店内序列(4位)，撞号重试一次（详设 5.1）
     */
    private BizMember registerMember(BizStore store, MpBindMobileBo bo) {
        BizMember member = new BizMember();
        member.setStoreId(store.getStoreId());
        member.setDeptId(store.getDeptId());
        member.setMobile(bo.getMobile());
        member.setOpenId(bo.getOpenId());
        member.setNickName(bo.getNickName());
        member.setAvatar(bo.getAvatar());
        member.setMemberNo(generateMemberNo(store.getStoreId()));
        try {
            baseMapper.insert(member);
            return member;
        } catch (DuplicateKeyException e) {
            String message = StringUtils.defaultString(e.getMessage());
            if (message.contains("uk_tenant_store_open")) {
                throw new ServiceException("该微信已绑定本店其他手机号档案，请联系店家处理");
            }
            // 手机号并发建档冲突或卡号撞号：前者提示，后者换号重试一次
            if (message.contains("uk_tenant_store_mobile")) {
                throw new ServiceException("该手机号已在本店建档");
            }
            member.setMemberNo(generateMemberNo(store.getStoreId()));
            try {
                baseMapper.insert(member);
                return member;
            } catch (DuplicateKeyException ex) {
                throw new ServiceException("会员建档失败，请重试");
            }
        }
    }

    private String generateMemberNo(Long storeId) {
        Integer storeSeq = storeService.selectStoreSeq(storeId);
        if (storeSeq == null) {
            throw new ServiceException("门店不存在，无法生成会员卡号");
        }
        long seq = RedisUtils.incrAtomicValue(OpsMemberConstants.MEMBER_NO_SEQ_KEY + storeId);
        return String.format("%04d%04d", storeSeq, seq % 10000);
    }

    /**
     * 签发 mp 端 token（30 天，value = memberId，契约 OpsMpConstants）
     */
    private String issueToken(Long memberId) {
        String token = IdUtil.fastSimpleUUID();
        RedisUtils.setCacheObject(OpsMpConstants.MP_TOKEN_KEY + token, memberId,
            Duration.ofDays(OpsMpConstants.MP_TOKEN_TTL_DAYS));
        return token;
    }

    private MpMemberVo toVo(BizMember member, String storeName) {
        MpMemberVo vo = new MpMemberVo();
        vo.setMemberId(member.getMemberId());
        vo.setStoreId(member.getStoreId());
        vo.setMemberNo(member.getMemberNo());
        vo.setOpenId(member.getOpenId());
        vo.setMobile(member.getMobile());
        vo.setNickName(member.getNickName());
        vo.setAvatar(member.getAvatar());
        vo.setBalance(member.getBalance());
        vo.setPointBalance(member.getPointBalance());
        vo.setStoreName(storeName);
        return vo;
    }

}
