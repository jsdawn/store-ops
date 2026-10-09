-- ----------------------------
-- store-ops 门店管理（ops-business · store 域）初始化脚本
-- 依据：《store-ops 门店管理详细设计》v1.0 第 3、6 节
-- 执行目标库：ry-cloud（MySQL 8）
-- ----------------------------

-- ----------------------------
-- 1. 门店表 biz_store
-- 租户隔离由 MyBatis-Plus 租户插件自动注入（tenant.excludes 未排除本表）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `biz_store` (
  `store_id`    BIGINT       NOT NULL                COMMENT '门店ID（雪花）',
  `tenant_id`   VARCHAR(20)  NOT NULL DEFAULT '000000' COMMENT '租户ID（插件注入）',
  `dept_id`     BIGINT       NOT NULL                COMMENT '绑定部门ID（sys_dept.dept_id，1:1）',
  `store_name`  VARCHAR(30)  NOT NULL                COMMENT '门店名称（租户内唯一）',
  `address`     VARCHAR(100) DEFAULT NULL            COMMENT '门店地址',
  `status`      CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '状态（0启用 1停用）',
  `remark`      VARCHAR(200) DEFAULT NULL            COMMENT '备注',
  `del_flag`    CHAR(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志（0存在 1删除）',
  `create_dept` BIGINT       DEFAULT NULL            COMMENT '创建部门',
  `create_by`   BIGINT       DEFAULT NULL            COMMENT '创建者',
  `create_time` DATETIME     DEFAULT NULL            COMMENT '创建时间',
  `update_by`   BIGINT       DEFAULT NULL            COMMENT '更新者',
  `update_time` DATETIME     DEFAULT NULL            COMMENT '更新时间',
  PRIMARY KEY (`store_id`),
  UNIQUE KEY `uk_tenant_dept` (`tenant_id`, `dept_id`),
  UNIQUE KEY `uk_tenant_name` (`tenant_id`, `store_name`),
  KEY `idx_status` (`tenant_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门店表';

-- ----------------------------
-- 2. 菜单权限（sys_menu.menu_id 非自增，业务菜单固定使用 3000 段，与框架 0~1623 不冲突）
-- ----------------------------

-- 2.1 目录：门店经营
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3000, '门店经营', 0, 10, 'ops', NULL, '', 1, 0, 'M', '0', '0', '', 'shopping', 103, 1, sysdate(), '门店经营目录');

-- 2.2 菜单：门店管理
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3001, '门店管理', 3000, 1, 'store', 'ops/store/index', '', 1, 0, 'C', '0', '0', 'ops:store:list', 'shop', 103, 1, sysdate(), '门店管理菜单');

-- 2.3 按钮权限
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3002, '门店查询', 3001, 1, '#', '', '', 1, 0, 'F', '0', '0', 'ops:store:query', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3003, '门店新增', 3001, 2, '#', '', '', 1, 0, 'F', '0', '0', 'ops:store:add', '#', 103, 1, sysdate(), '');
INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, component, query_param, is_frame, is_cache, menu_type, visible, status, perms, icon, create_dept, create_by, create_time, remark)
VALUES (3004, '门店修改', 3001, 3, '#', '', '', 1, 0, 'F', '0', '0', 'ops:store:edit', '#', 103, 1, sysdate(), '');

-- ----------------------------
-- 3. 配套说明（运行时操作，非脚本）
-- 3.1 租户套餐（系统管理 → 租户管理 → 套餐）勾选「门店经营」目录，租户管理员角色默认获得以上权限；
-- 3.2 店长角色不授予本目录权限（详设 6 节）；
-- 3.3 Nacos 配置：将 script/config/nacos/ops-business.yml 导入为 dataId=ops-business.yml（DEFAULT_GROUP）；
--     ruoyi-gateway.yml 增加的路由需同步到 Nacos 后重启网关。
-- ----------------------------
