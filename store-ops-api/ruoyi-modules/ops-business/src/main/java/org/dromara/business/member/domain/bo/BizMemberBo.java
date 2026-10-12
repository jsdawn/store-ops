package org.dromara.business.member.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.business.member.domain.BizMember;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 会员业务对象 biz_member（查询筛选 + 线下登记建档入参）
 *
 * @author store-ops
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BizMember.class, reverseConvertGenerate = false)
public class BizMemberBo extends BaseEntity {

    /**
     * 会员ID
     */
    private Long memberId;

    /**
     * 门店ID（建档必填；列表筛选）
     */
    @NotNull(message = "门店不能为空", groups = {AddGroup.class})
    private Long storeId;

    /**
     * 手机号（建档必填，门店内唯一；列表筛选）
     */
    @NotBlank(message = "手机号不能为空", groups = {AddGroup.class})
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确", groups = {AddGroup.class})
    private String mobile;

    /**
     * 会员卡号（列表筛选）
     */
    private String memberNo;

    /**
     * 昵称（建档可选；列表模糊筛选）
     */
    @Size(min = 0, max = 30, message = "昵称长度不能超过{max}个字符")
    private String nickName;

    /**
     * 备注
     */
    @Size(min = 0, max = 100, message = "备注长度不能超过{max}个字符")
    private String remark;

    /**
     * 收银/充值选会员关键字（手机号/卡号/昵称模糊，search 接口专用）
     */
    private String keyword;

    /**
     * 校验分组：线下登记建档
     */
    public interface AddGroup {
    }

}
