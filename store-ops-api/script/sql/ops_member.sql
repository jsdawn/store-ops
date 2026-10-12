-- ----------------------------
-- store-ops 会员体系（ops-business · member 域）初始化脚本
-- 依据：《store-ops 会员体系详细设计》v1.0 第 3、6 节
-- 执行目标库：ry-cloud（MySQL 8）
-- 门店级隔离：所有业务表含 store_id + dept_id，唯一键均含 (tenant_id, store_id) 维度
-- ----------------------------

-- ----------------------------
-- 1. 会员档案表 biz_member（门店级会员池：余额/积分账户 + 累计消费冗余）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_member` (
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID（biz_store.store_id）',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门（数据权限过滤）',
  `member_no`     VARCHAR(20)   NOT NULL                  COMMENT '会员卡号（门店序号4位+店内序列4位，门店内唯一）',
  `mobile`        VARCHAR(11)   NOT NULL                  COMMENT '手机号（建档必填，门店内唯一）',
  `open_id`       VARCHAR(64)   DEFAULT NULL              COMMENT '微信open_id（仅登录凭证，未绑定为NULL）',
  `nick_name`     VARCHAR(30)   DEFAULT NULL              COMMENT '昵称（微信昵称，线下登记可空）',
  `avatar`        VARCHAR(255)  DEFAULT NULL              COMMENT '微信头像URL',
  `balance`       DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '余额（元）',
  `point_balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '积分（两位小数）',
  `total_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '累计消费（元，已完成订单应付合计）',
  `total_orders`  INT           NOT NULL DEFAULT 0       COMMENT '累计消费次数',
  `del_flag`      CHAR(1)       NOT NULL DEFAULT '0'     COMMENT '删除标志（0存在 1删除，本期无删除入口）',
  `create_dept`   BIGINT        DEFAULT NULL            COMMENT '创建部门',
  `create_by`     BIGINT        DEFAULT NULL            COMMENT '创建者',
  `create_time`   DATETIME      DEFAULT NULL            COMMENT '创建时间',
  `update_by`     BIGINT        DEFAULT NULL            COMMENT '更新者',
  `update_time`   DATETIME      DEFAULT NULL            COMMENT '更新时间',
  `remark`        VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
  PRIMARY KEY (`member_id`),
  UNIQUE KEY `uk_tenant_store_mobile` (`tenant_id`,`store_id`,`mobile`),
  UNIQUE KEY `uk_tenant_store_open` (`tenant_id`,`store_id`,`open_id`),
  UNIQUE KEY `uk_tenant_store_no` (`tenant_id`,`store_id`,`member_no`),
  KEY `idx_store` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员档案（门店级会员池）';

-- ----------------------------
-- 2. 充值流水表 biz_recharge_log（冲正状态挂本表，冲正动作另记余额流水）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_recharge_log` (
  `recharge_id`   BIGINT        NOT NULL                  COMMENT '充值流水ID（雪花）',
  `recharge_no`   VARCHAR(32)   NOT NULL                  COMMENT '充值流水号（RC+yyMMdd+雪花后6位，全局唯一）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `amount`        DECIMAL(12,2) NOT NULL                  COMMENT '充值金额（本金）',
  `gift_amount`   DECIMAL(12,2) NOT NULL DEFAULT 0.00    COMMENT '赠送金额',
  `pay_type`      CHAR(1)       NOT NULL                  COMMENT '充值方式（1微信 2支付宝 3现金，仅记录不触发支付）',
  `operator_id`   BIGINT        NOT NULL                  COMMENT '操作店员（sys_user.user_id）',
  `operator_name` VARCHAR(30)   NOT NULL                  COMMENT '操作店员姓名（冗余，防员工离职后不可读）',
  `status`        CHAR(1)       NOT NULL DEFAULT '0'      COMMENT '状态（0有效 1已冲正）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(200)  DEFAULT NULL             COMMENT '备注（如「周年庆活动」）',
  PRIMARY KEY (`recharge_id`),
  UNIQUE KEY `uk_recharge_no` (`recharge_no`),
  KEY `idx_member` (`member_id`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值流水';

-- ----------------------------
-- 3. 余额流水表 biz_balance_log（每笔变动一条，含变动后快照）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_balance_log` (
  `log_id`        BIGINT        NOT NULL                  COMMENT '流水ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `change_amount`        DECIMAL(12,2) NOT NULL                  COMMENT '变动金额（正入账/负扣减）',
  `after_balance` DECIMAL(12,2) NOT NULL                  COMMENT '变动后余额快照（对账用）',
  `biz_type`      CHAR(1)       NOT NULL                  COMMENT '业务类型（1充值 2消费 3冲正）',
  `biz_no`        VARCHAR(32)   NOT NULL                  COMMENT '关联业务单号（充值流水号/订单号）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(255)  DEFAULT NULL             COMMENT '备注（冲正原因记此处）',
  PRIMARY KEY (`log_id`),
  KEY `idx_member_time` (`member_id`,`create_time`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员余额流水';

-- ----------------------------
-- 4. 积分流水表 biz_point_log（余额+流水模式）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_point_log` (
  `log_id`        BIGINT        NOT NULL                  COMMENT '流水ID（雪花）',
  `tenant_id`     VARCHAR(20)   NOT NULL DEFAULT '000000'  COMMENT '租户ID（插件注入）',
  `store_id`      BIGINT        NOT NULL                  COMMENT '门店ID',
  `dept_id`       BIGINT        NOT NULL                  COMMENT '冗余门店绑定部门',
  `member_id`     BIGINT        NOT NULL                  COMMENT '会员ID',
  `change_point`        DECIMAL(12,2) NOT NULL                  COMMENT '变动积分（正获得/负消耗）',
  `after_point`   DECIMAL(12,2) NOT NULL                  COMMENT '变动后积分快照',
  `biz_type`      CHAR(1)       NOT NULL                  COMMENT '业务类型（1消费获得 2兑换消耗）',
  `biz_no`        VARCHAR(32)   NOT NULL                  COMMENT '关联业务单号（订单号/券实例ID）',
  `create_dept`   BIGINT        DEFAULT NULL,
  `create_by`     BIGINT        DEFAULT NULL,
  `create_time`   DATETIME      DEFAULT NULL,
  `update_by`     BIGINT        DEFAULT NULL,
  `update_time`   DATETIME      DEFAULT NULL,
  `remark`        VARCHAR(200)  DEFAULT NULL,
  PRIMARY KEY (`log_id`),
  KEY `idx_member_time` (`member_id`,`create_time`),
  KEY `idx_store_time` (`store_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员积分流水';

-- ----------------------------
-- 5. 菜单权限（menu_id 沿用 3000 段：store 3000~3004，member 3010~3022）
-- ----------------------------

-- 5.1 菜单：会员管理（页面）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3010, '会员管理', 3000, 2, 'member', 'ops/member/index', '', 1, 0, 'C', '0', '0', '', 'peoples', 103, 1, sysdate(), '会员管理菜单');

-- 5.2 会员管理按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3011, '会员查询', 3010, 1, '#', '', '', 1, 0, 'F', '0', '0', 'ops:member:list', '#', 103, 1, sysdate(), '会员列表与收银选客搜索');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3012, '会员详情', 3010, 2, '#', '', '', 1, 0, 'F', '0', '0', 'ops:member:query', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3013, '线下登记会员', 3010, 3, '#', '', '', 1, 0, 'F', '0', '0', 'ops:member:add', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3014, '充值入账', 3010, 4, '#', '', '', 1, 0, 'F', '0', '0', 'ops:member:recharge', '#', 103, 1, sysdate(), '小程序工作台充值入口共用');

-- 5.3 菜单：充值流水（页面）
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3020, '充值流水', 3000, 3, 'recharge', 'ops/recharge/index', '', 1, 0, 'C', '0', '0', '', 'money', 103, 1, sysdate(), '充值流水与冲正菜单');

-- 5.4 充值流水按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3021, '充值流水查询', 3020, 1, '#', '', '', 1, 0, 'F', '0', '0', 'ops:recharge:list', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3022, '充值冲正', 3020, 2, '#', '', '', 1, 0, 'F', '0', '0', 'ops:recharge:reversal', '#', 103, 1, sysdate(), '仅管理台，当日有效');

-- ----------------------------
-- 6. 字典（dict_id/dict_code 用 6.0e18 高位固定段，避开 UI 生成的雪花 ID；
--    管理租户 000000 全租户可见，租户数据按框架字典继承机制读取）
-- ----------------------------
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6001000000000000001, '000000', '会员充值方式', 'member_pay_type',   103, 1, sysdate(), null, null, '会员充值方式列表');
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6001000000000000002, '000000', '余额流水类型', 'balance_log_type', 103, 1, sysdate(), null, null, '会员余额流水业务类型');
INSERT INTO sys_dict_type (dict_id, tenant_id, dict_name, dict_type, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6001000000000000003, '000000', '积分流水类型', 'point_log_type',    103, 1, sysdate(), null, null, '会员积分流水业务类型');

INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000001, '000000', 1, '微信收款',   '1', 'member_pay_type',   '', 'primary', 'Y', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000002, '000000', 2, '支付宝收款', '2', 'member_pay_type',   '', 'warning', 'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000003, '000000', 3, '现金收款',   '3', 'member_pay_type',   '', 'info',    'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000011, '000000', 1, '充值', '1', 'balance_log_type', '', 'primary', 'Y', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000012, '000000', 2, '消费', '2', 'balance_log_type', '', 'info',    'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000013, '000000', 3, '冲正', '3', 'balance_log_type', '', 'danger',  'N', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000021, '000000', 1, '消费获得', '1', 'point_log_type', '', 'primary', 'Y', 103, 1, sysdate(), null, null, '');
INSERT INTO sys_dict_data (dict_code, tenant_id, dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, create_dept, create_by, create_time, update_by, update_time, remark)
VALUES (6002000000000000022, '000000', 2, '兑换消耗', '2', 'point_log_type', '', 'warning', 'N', 103, 1, sysdate(), null, null, '');

-- ----------------------------
-- 7. 配套说明（运行时操作，非脚本）
-- 7.1 租户套餐（系统管理 → 租户管理 → 套餐）勾选「门店经营」目录下的新菜单按钮；
-- 7.2 店长角色按需勾选 3011~3014（会员查询/详情/登记/充值），不勾门店管理与充值流水冲正（3022 仅租户管理员）；
-- 7.3 mp 端登录链路：ruoyi-auth /auth/mp/login + 网关白名单 /mp/**（见代码与 Nacos 配置变更）。
-- ----------------------------
