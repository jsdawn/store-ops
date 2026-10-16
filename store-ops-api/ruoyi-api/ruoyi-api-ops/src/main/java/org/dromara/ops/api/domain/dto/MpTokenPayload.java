package org.dromara.ops.api.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端 token Redis value 载荷（详设 4.6）
 *
 * <p>mp 端不透明 token 的身份容器：memberId 恒有；employeeId 仅当会员被识别为
 * 当前门店员工（会员手机号 = 门店对应部门下管理用户手机号，决策 D9）时下发。
 * 兼容历史：旧 token 的 Redis value 为裸 memberId（Long），拦截器双格式解析。</p>
 *
 * @author store-ops
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MpTokenPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 会员档案ID（雪花）
     */
    private Long memberId;

    /**
     * 员工身份 = 管理用户ID（sys_user.user_id，雪花）；非员工为 null
     */
    private Long employeeId;

}
