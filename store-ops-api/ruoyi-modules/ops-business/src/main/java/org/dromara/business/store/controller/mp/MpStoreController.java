package org.dromara.business.store.controller.mp;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.dromara.business.store.domain.BizStore;
import org.dromara.business.store.mapper.BizStoreMapper;
import org.dromara.common.core.domain.R;
import org.dromara.common.tenant.helper.TenantHelper;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;

/**
 * mp 端门店接口（顾客微信端，无需登录：登录页展示门店信息）
 *
 * @author store-ops
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/mp/store")
public class MpStoreController {

    private final BizStoreMapper storeMapper;

    /**
     * 门店信息（登录页头部展示门店名/地址）
     *
     * @param storeId 门店ID（扫码 scene）
     * @return 门店公开信息
     */
    @GetMapping("/info")
    public R<MpStoreVo> info(@NotNull(message = "门店ID不能为空") @RequestParam Long storeId) {
        BizStore store = TenantHelper.ignore(() -> storeMapper.selectById(storeId));
        if (store == null) {
            return R.fail("门店不存在");
        }
        MpStoreVo vo = new MpStoreVo();
        vo.setStoreId(store.getStoreId());
        vo.setStoreName(store.getStoreName());
        vo.setAddress(store.getAddress());
        vo.setStatus(store.getStatus());
        return R.ok(vo);
    }

    /**
     * 门店公开信息（不含内部字段）
     */
    @Data
    public static class MpStoreVo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 门店ID
         */
        private Long storeId;

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

    }

}
