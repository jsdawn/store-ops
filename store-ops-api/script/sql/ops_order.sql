-- ----------------------------
-- store-ops 收银管理（ops-business · order 域）初始化脚本
-- 依据：《store-ops 收银管理详细设计》v1.1 第 3、6 节
-- 执行目标库：ry-cloud（MySQL 8）
-- 门店级隔离：所有业务表含 store_id + dept_id；订单为快照性数据，无 del_flag（作废代替删除）
-- ----------------------------

-- ----------------------------
-- 1. 订单主表 biz_order（会员昵称/手机号冗余快照，历史单不受改名影响）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_order` (
  `order_id`         BIGINT        NOT NULL                  COMMENT '订单ID（雪花）',
  `tenant_id`        VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`         BIGINT        NOT NULL                  COMMENT '门店ID（biz_store.store_id）',
  `dept_id`          BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门（数据权限过滤）',
  `order_no`         VARCHAR(32)   NOT NULL                  COMMENT '订单号（ORD+yyMMdd+雪花后6位，全局唯一）',
  `member_id`        BIGINT        DEFAULT NULL              COMMENT '会员ID（NULL=散客）',
  `member_name`      VARCHAR(50)   DEFAULT NULL              COMMENT '会员昵称快照（下单时刻）',
  `member_mobile`    VARCHAR(11)   DEFAULT NULL              COMMENT '会员手机号快照（下单时刻）',
  `total_amount`     DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '订单总额（元，明细合计，券前）',
  `coupon_id`        BIGINT        DEFAULT NULL              COMMENT '优惠券实例ID（未用券为NULL，券域P3）',
  `coupon_amount`    DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '优惠券优惠额（元）',
  `pay_amount`       DECIMAL(12,2) NOT NULL                  COMMENT '应付金额（元，=总额-优惠额，恒>0）',
  `pay_type`         CHAR(1)       DEFAULT NULL              COMMENT '支付方式（order_pay_type：1余额 2微信 3支付宝 4现金，完成时填）',
  `status`           CHAR(1)       NOT NULL DEFAULT '0'      COMMENT '状态（order_status：0待收款 1已完成 2已作废）',
  `earned_point`     DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '本单积分（=应付等额，散客为0，冗余展示）',
  `balance_reversed` CHAR(1)       NOT NULL DEFAULT '0'      COMMENT '余额收款冲正标志（0否 1是，防重复冲正）',
  `void_reason`      VARCHAR(200)  DEFAULT NULL              COMMENT '作废原因（作废时必填）',
  `operator_id`      BIGINT        NOT NULL                  COMMENT '开单操作人（sys_user.user_id）',
  `operator_name`    VARCHAR(30)   NOT NULL                  COMMENT '开单操作人姓名快照',
  `create_dept`      BIGINT        DEFAULT NULL,
  `create_by`        BIGINT        DEFAULT NULL,
  `create_time`      DATETIME      DEFAULT NULL,
  `update_by`        BIGINT        DEFAULT NULL,
  `update_time`      DATETIME      DEFAULT NULL,
  `remark`           VARCHAR(255)  DEFAULT NULL              COMMENT '备注（追加式：[MM-dd HH:mm 操作人] 内容，详设 5.7）',
  PRIMARY KEY (`order_id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_store_status` (`tenant_id`,`store_id`,`status`),
  KEY `idx_member` (`tenant_id`,`member_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表（门店级，收银）';

-- ----------------------------
-- 2. 订单明细表 biz_order_item（逐笔录入，订单完成后不可变；改明细=作废重开）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_order_item` (
  `item_id`      BIGINT        NOT NULL                  COMMENT '明细ID（雪花）',
  `order_id`     BIGINT        NOT NULL                  COMMENT '订单ID（biz_order.order_id）',
  `tenant_id`    VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`     BIGINT        NOT NULL                  COMMENT '门店ID',
  `item_name`    VARCHAR(50)   DEFAULT NULL              COMMENT '项目名称（选填，仅展示备注）',
  `unit_price`   DECIMAL(12,2) NOT NULL                  COMMENT '单价（元，>0）',
  `quantity`     INT           NOT NULL DEFAULT 1        COMMENT '数量（1~999）',
  `line_amount`  DECIMAL(12,2) NOT NULL                  COMMENT '行金额（=单价×数量，服务端计算）',
  `create_dept`  BIGINT        DEFAULT NULL,
  `create_by`    BIGINT        DEFAULT NULL,
  `update_by`    BIGINT        DEFAULT NULL,
  `update_time`  DATETIME      DEFAULT NULL,
  `create_time`  DATETIME      DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ----------------------------
-- 3. 菜单权限（menu_id 沿用 3000 段：store 3000~3004，member 3010~3022，order 3030~3037）
-- ----------------------------

-- 3.1 菜单：订单管理（页面）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3030, '订单管理', 3000, 4, 'order', 'ops/order/index', '', 1, 0, 'C', '0', '0', '', 'documentation', 103, 1, sysdate(), '订单管理菜单（开单/收款/查询/详情/备注/作废/余额冲正）');

-- 3.2 订单管理按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3031, '订单查询', 3030, 1, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:list', '#', 103, 1, sysdate(), '列表与今日流水（管理台）');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3032, '订单详情', 3030, 2, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:query', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3033, '订单作废', 3030, 3, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:void', '#', 103, 1, sysdate(), '仅待收款；仅店长可作废（审查结论⑤），店员不授予');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3034, '订单备注', 3030, 4, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:remark', '#', 103, 1, sysdate(), '仅已完成，错账备注用');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3035, '余额收款冲正', 3030, 5, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:reversal', '#', 103, 1, sysdate(), '仅管理台，当日有效，与充值冲正 3022 同级');

-- 3.3 开单/收款按钮权限（管理台收银功能：订单页开单/收款入口；员工端走 /mp/staff/** 独立鉴权不走 RBAC，见 member 详设 4.6 D10）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3036, '收银开单', 3030, 6, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:add', '#', 103, 1, sysdate(), '管理台开单入口按钮权限');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3037, '确认收款', 3030, 7, '#', '', '', 1, 0, 'F', '0', '0', 'ops:order:pay', '#', 103, 1, sysdate(), '管理台收款按钮权限');

-- ----------------------------
-- 4. 字典（dict_id/dict_code 用 6.0e18 高位固定段，避开 UI 生成的雪花 ID；
--    插入 000000 行后，存量租户在管理台「租户管理 → 同步租户字典」一键补副本并清缓存）
-- ----------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6001000000000000004, '000000', '订单状态',   'order_status',    103, 1, sysdate(), null, null, '订单状态列表');
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6001000000000000005, '000000', '订单支付方式', 'order_pay_type', 103, 1, sysdate(), null, null, '订单支付方式（与充值方式语义不同，独立字典，详设 D7）');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000031, '000000', 1, '待收款', '0', 'order_status', '', 'warning', 'Y', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000032, '000000', 2, '已完成', '1', 'order_status', '', 'success', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000033, '000000', 3, '已作废', '2', 'order_status', '', 'danger',  'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000041, '000000', 1, '余额收款',   '1', 'order_pay_type', '', 'primary', 'N', 103, 1, sysdate(), null, null, '扣会员余额，同事务');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000042, '000000', 2, '微信收款',   '2', 'order_pay_type', '', 'success', 'N', 103, 1, sysdate(), null, null, '线下收款标记');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000043, '000000', 3, '支付宝收款', '3', 'order_pay_type', '', 'warning', 'N', 103, 1, sysdate(), null, null, '线下收款标记');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000044, '000000', 4, '现金收款',   '4', 'order_pay_type', '', 'info',    'N', 103, 1, sysdate(), null, null, '线下收款标记');

-- ----------------------------
-- 5. 配套说明（运行时操作，非脚本）
-- 5.1 管理台「租户管理 → 同步租户字典」补存量租户 order_status / order_pay_type 副本（自动清缓存）；
-- 5.2 角色绑定（详设第 6 节）：店长勾 3031/3032/3033（含作废，仅店长）与 3034；
--     店员管理台无需订单菜单（收银走员工端 /mp/staff/**，不走 pc 权限体系）；租户管理员全量 + 3035（余额冲正）；
-- 5.3 顾客端 /mp/order/**（我的消费记录）与员工端 /mp/staff/** 均不走 pc 权限体系，Mp-Token 鉴权，详设 4.3 / member 详设 4.6。
-- ----------------------------
