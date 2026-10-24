-- ============================================================
-- FridgeMind 冰箱食材识别与管理系统 - 数据库建表脚本
-- 使用方式：首次部署时在 MySQL 中执行此脚本
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS fridgemind DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE fridgemind;

-- ============================================================
-- 1. 用户表
-- ============================================================
CREATE TABLE `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `phone`         VARCHAR(20)  NOT NULL                COMMENT '手机号（登录账号）',
    `password`      VARCHAR(128)          DEFAULT NULL   COMMENT 'bcrypt 加密后的密码，无密码登录时可为空',
    `nickname`      VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '用户昵称',
    `avatar`        VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '头像URL',
    `has_password`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已设置密码：0=未设置, 1=已设置',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 家庭表
-- ============================================================
CREATE TABLE `family` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '家庭ID',
    `name`            VARCHAR(50)  NOT NULL                COMMENT '家庭名称',
    `owner_id`        BIGINT       NOT NULL                COMMENT '创建者用户ID',
    `qr_code_token`   VARCHAR(64)           DEFAULT NULL   COMMENT '邀请二维码 token',
    `qr_token_expire` DATETIME              DEFAULT NULL   COMMENT '邀请二维码过期时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭表';

-- ============================================================
-- 3. 家庭成员表
-- 每个用户只能加入一个家庭（user_id UNIQUE）
-- ============================================================
CREATE TABLE `family_member` (
    `id`        BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `family_id` BIGINT   NOT NULL                COMMENT '家庭ID',
    `user_id`   BIGINT   NOT NULL                COMMENT '用户ID',
    `role`      TINYINT  NOT NULL DEFAULT 1      COMMENT '角色：0=创建者(owner), 1=普通成员(member)',
    `joined_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    INDEX `idx_family_id` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭成员表';

-- ============================================================
-- 4. 冰箱表
-- ============================================================
CREATE TABLE `fridge` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '冰箱ID',
    `family_id`     BIGINT       NOT NULL                COMMENT '所属家庭ID',
    `name`          VARCHAR(50)  NOT NULL                COMMENT '冰箱名称（用户自定义）',
    `device_sn`     VARCHAR(64)           DEFAULT NULL   COMMENT '硬件设备序列号（可空，纯软件版不需要）',
    `online_status` TINYINT      NOT NULL DEFAULT 0      COMMENT '在线状态：0=离线, 1=在线',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_family_id` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='冰箱表';

-- ============================================================
-- 5. 分类表
-- family_id 为 NULL 表示系统预置分类，非 NULL 表示家庭自定义分类
-- ============================================================
CREATE TABLE `category` (
    `id`         INT          NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    `name`       VARCHAR(50)  NOT NULL                COMMENT '分类名称',
    `parent_id`  INT                   DEFAULT NULL   COMMENT '父分类ID（支持二级分类）',
    `family_id`  BIGINT                DEFAULT NULL   COMMENT '所属家庭ID（NULL=系统预置分类）',
    `sort_order` INT          NOT NULL DEFAULT 0      COMMENT '排序权重，越小越靠前',
    `is_system`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否系统预置：0=自定义, 1=系统预置',
    `remind_days` INT         NOT NULL DEFAULT 3      COMMENT '提前提醒天数',
    PRIMARY KEY (`id`),
    INDEX `idx_family_id` (`family_id`),
    INDEX `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材分类表';

-- ============================================================
-- 6. 食材表（核心业务表）
-- ============================================================
CREATE TABLE `ingredient` (
    `id`               BIGINT         NOT NULL AUTO_INCREMENT COMMENT '食材ID',
    `fridge_id`        BIGINT         NOT NULL                COMMENT '所属冰箱ID',
    `category_id`      INT                      DEFAULT NULL  COMMENT '分类ID',
    `name`             VARCHAR(100)   NOT NULL                COMMENT '食材名称',
    `quantity`         DECIMAL(10,2)  NOT NULL DEFAULT 1.00   COMMENT '数量',
    `unit`             VARCHAR(20)    NOT NULL DEFAULT '个'   COMMENT '单位（eg. 个/袋/瓶/盒/g/mL）',
    `status`           TINYINT        NOT NULL DEFAULT 1      COMMENT '状态：1=未开封, 2=已开封, 3=过期, 4=已用完',
    `expiry_date`      DATE                     DEFAULT NULL  COMMENT '过期日期',
    `production_date`  DATE                     DEFAULT NULL  COMMENT '生产日期',
    `recognition_mode` VARCHAR(10)    NOT NULL DEFAULT 'manual' COMMENT '录入方式：manual/auto',
    `confidence`       DECIMAL(3,2)            DEFAULT NULL  COMMENT 'AI识别置信度（0.00~1.00）',
    `original_name`    VARCHAR(100)            DEFAULT NULL  COMMENT 'AI识别的原始名称（用户修改前）',
    `corrected_by`     BIGINT                  DEFAULT NULL  COMMENT '修正人用户ID',
    `corrected_at`     DATETIME                DEFAULT NULL  COMMENT '修正时间',
    `remark`           VARCHAR(200)   NOT NULL DEFAULT ''     COMMENT '备注',
    `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_fridge_id` (`fridge_id`),
    INDEX `idx_category_id` (`category_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_expiry_date` (`expiry_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食材表';

-- ============================================================
-- 7. 库存变更日志表
-- ============================================================
CREATE TABLE `inventory_log` (
    `id`              BIGINT         NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `ingredient_id`   BIGINT         NOT NULL                COMMENT '食材ID',
    `fridge_id`       BIGINT         NOT NULL                COMMENT '冰箱ID（冗余，方便按冰箱查询）',
    `user_id`         BIGINT                  DEFAULT NULL  COMMENT '操作用户ID',
    `type`            TINYINT        NOT NULL                COMMENT '操作类型：1=新增, 2=移除, 3=过期, 4=修改',
    `source`          TINYINT        NOT NULL DEFAULT 1      COMMENT '来源：1=手动录入, 2=自动检测',
    `quantity_change` DECIMAL(10,2)  NOT NULL DEFAULT 0.00   COMMENT '数量变化',
    `quantity_after`  DECIMAL(10,2)  NOT NULL DEFAULT 0.00   COMMENT '操作后剩余数量',
    `confirmed`       TINYINT                 DEFAULT NULL  COMMENT '处理方式：NULL=未记录, 1=已食用, 2=已丢弃, 3=已过期',
    `confirmed_at`    DATETIME                DEFAULT NULL  COMMENT '确认时间',
    `remark`          VARCHAR(200)   NOT NULL DEFAULT ''     COMMENT '备注',
    `created_at`      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    INDEX `idx_ingredient_id` (`ingredient_id`),
    INDEX `idx_fridge_id` (`fridge_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_type` (`type`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变更日志表';

-- ============================================================
-- 8. 审计日志表
-- ============================================================
CREATE TABLE `audit_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id`    BIGINT       NOT NULL                COMMENT '操作用户ID',
    `family_id`  BIGINT                DEFAULT NULL  COMMENT '所属家庭ID',
    `action`     VARCHAR(50)  NOT NULL                COMMENT '操作类型（如：RECOGNIZE_INGREDIENT, ADD_INGREDIENT）',
    `target`     VARCHAR(200) NOT NULL DEFAULT ''     COMMENT '操作目标描述',
    `level`      VARCHAR(10)  NOT NULL DEFAULT 'INFO' COMMENT '级别：INFO/WARN/ERROR',
    `result`     VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS/FAIL',
    `ip`         VARCHAR(45)           DEFAULT NULL  COMMENT '操作IP',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_family_id` (`family_id`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- ============================================================
-- 9. 管理员表
-- ============================================================
CREATE TABLE `admin` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
    `username`   VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `password`   VARCHAR(128) NOT NULL                COMMENT 'BCrypt 加密密码',
    `nickname`   VARCHAR(50)  NOT NULL DEFAULT ''     COMMENT '昵称',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统管理员表';

-- ============================================================
-- 10. 购物清单表（家庭共享）
-- ============================================================
CREATE TABLE `shopping_list` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '清单项ID',
    `family_id`    BIGINT        NOT NULL                COMMENT '所属家庭ID',
    `name`         VARCHAR(100)  NOT NULL                COMMENT '商品名称',
    `quantity`     DECIMAL(10,2) NOT NULL DEFAULT 1.00   COMMENT '数量',
    `unit`         VARCHAR(20)   NOT NULL DEFAULT '个'   COMMENT '单位',
    `category_id`  INT                    DEFAULT NULL   COMMENT '分类ID（保留字段，当前未使用）',
    `is_purchased` TINYINT       NOT NULL DEFAULT 0      COMMENT '0=待购买, 1=已购买',
    `added_by`     BIGINT        NOT NULL                COMMENT '添加人用户ID',
    `checked_by`   BIGINT                 DEFAULT NULL   COMMENT '勾选购买人用户ID',
    `checked_at`   DATETIME               DEFAULT NULL   COMMENT '勾选时间',
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_family_id` (`family_id`),
    INDEX `idx_is_purchased` (`is_purchased`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭购物清单表';

-- ============================================================
-- 11. 用户反馈表
-- ============================================================
CREATE TABLE `feedback` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '反馈ID',
    `user_id`    BIGINT       NOT NULL                COMMENT '提交用户ID',
    `content`    VARCHAR(500) NOT NULL                COMMENT '反馈内容',
    `status`     TINYINT      NOT NULL DEFAULT 0      COMMENT '处理状态：0=未处理, 1=已处理',
    `reply`      VARCHAR(500)          DEFAULT NULL   COMMENT '管理员回复',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户反馈表';

-- ============================================================
-- 冰箱表新增字段（设备监控用）
-- ============================================================
ALTER TABLE `fridge`
    ADD COLUMN `last_heartbeat` DATETIME DEFAULT NULL COMMENT '最后一次设备心跳时间',
    ADD COLUMN `is_fault`       TINYINT  NOT NULL DEFAULT 0 COMMENT '是否故障：0=正常, 1=故障';

-- ============================================================
-- 插入默认管理员账号 admin / admin123
-- ============================================================
INSERT INTO `admin` (`username`, `password`, `nickname`) VALUES
('admin', '$2b$10$eSh6KhIB2s/bsPKQ68uyuu3FBzFBYpDBD8dVdZ3xfVhF.s08IDIAO', '超级管理员');

-- ============================================================
-- 用户表新增字段（管理员可禁用用户）
-- ============================================================
ALTER TABLE `user`
    ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0=禁用, 1=启用';

-- 食材表：层位字段已废弃（所有冰箱统一单层存放），若历史库仍存在请执行
-- ALTER TABLE `ingredient` DROP COLUMN `position`;

-- ============================================================
-- 食材表新增字段（端云库存对账用）
-- 板子推送的 inventory_update 中 id 为设备端本地条目ID，
-- 后端存储该映射后才能向设备精确下发 remote_remove
-- ============================================================
ALTER TABLE `ingredient`
    ADD COLUMN `device_item_id` BIGINT DEFAULT NULL COMMENT '设备端本地条目ID（端云对账用，手动录入为NULL）';

-- ============================================================
-- 唯一约束（库层设防）：同一食材只被一台设备认领；一台真冰箱只绑一个家庭
-- ============================================================
ALTER TABLE `ingredient`
    ADD UNIQUE INDEX `uk_fridge_device` (`fridge_id`, `device_item_id`);
ALTER TABLE `fridge`
    ADD UNIQUE INDEX `uk_device_sn` (`device_sn`);

-- ============================================================
-- 冰箱表新增字段（设备接入密钥）
-- 设备连接 URL 携带 key 参数，后端比对；NULL 表示不校验（纯软件冰箱）
-- ============================================================
ALTER TABLE `fridge`
    ADD COLUMN `device_key` VARCHAR(64) DEFAULT NULL COMMENT '设备接入密钥（连接URL携带，NULL=不校验）';

-- ============================================================
-- 预置系统分类数据
-- ============================================================
INSERT INTO `category` (`name`, `parent_id`, `family_id`, `sort_order`, `is_system`, `remind_days`) VALUES
('水果',   NULL, NULL, 1, 1, 2),
('蔬菜',   NULL, NULL, 2, 1, 1),
('肉类',   NULL, NULL, 3, 1, 2),
('蛋类',   NULL, NULL, 4, 1, 3),
('乳制品', NULL, NULL, 5, 1, 3),
('饮料',   NULL, NULL, 6, 1, 7),
('包装食品', NULL, NULL, 7, 1, 7),
('其他',   NULL, NULL, 8, 1, 3);

-- ============================================================
-- 测试数据
-- ============================================================

-- ----- 用户（6个，密码均为 test123 的 bcrypt 哈希） -----
INSERT INTO `user` (`id`, `phone`, `password`, `nickname`, `avatar`, `has_password`, `enabled`) VALUES
(1, '13800000001', '$2b$10$f3doEDUEpvzzNXFAQ4aaruLJBrRW/9qKx4qAUJLE75ILnk/OMGgrK', '张三', '', 1, 1),
(2, '13800000002', '$2b$10$f3doEDUEpvzzNXFAQ4aaruLJBrRW/9qKx4qAUJLE75ILnk/OMGgrK', '李四', '', 1, 1),
(3, '13800000003', NULL,                                         '王五', '', 0, 1),
(4, '13800000004', '$2b$10$f3doEDUEpvzzNXFAQ4aaruLJBrRW/9qKx4qAUJLE75ILnk/OMGgrK', '赵六', '', 1, 0),
(5, '13800000005', NULL,                                         '小明', '', 0, 1),
(6, '13800000006', '$2b$10$f3doEDUEpvzzNXFAQ4aaruLJBrRW/9qKx4qAUJLE75ILnk/OMGgrK', '小红', '', 1, 1);

-- ----- 家庭（2个） -----
INSERT INTO `family` (`id`, `name`, `owner_id`) VALUES
(1, '张家厨房', 1),
(2, '快乐家庭', 2);

-- ----- 家庭成员 -----
INSERT INTO `family_member` (`family_id`, `user_id`, `role`) VALUES
(1, 1, 0),  -- 张三是群主
(1, 3, 1),  -- 王五是成员
(1, 5, 1),  -- 小明是成员
(2, 2, 0),  -- 李四是群主
(2, 6, 1);  -- 小红是成员

-- ----- 冰箱（5台，第5台为真实硬件设备：RK3566 开发板，绑定家庭1） -----
-- device_key：设备连接 URL 携带的接入密钥，NULL 表示不校验（纯软件冰箱）
INSERT INTO `fridge` (`id`, `family_id`, `name`, `device_sn`, `device_key`, `online_status`, `last_heartbeat`, `is_fault`) VALUES
(1, 1, '主冰箱',   'SN-2024-001', NULL,           0, NULL, 0),
(2, 1, '小冷柜',   'SN-2024-002', NULL,           0, NULL, 0),
(3, 2, '厨房冰箱', 'SN-2024-003', NULL,           0, NULL, 0),
(4, 2, '旧冰箱',   'SN-2024-004', NULL,           0, NULL, 1),
(5, 1, 'FridgeMind 智能冰箱', 'fridge_001', 'demo-key-2026', 0, NULL, 0);

-- ----- 食材（12条，分布在4台冰箱；所有冰箱统一单层存放） -----
-- 保质期使用相对日期，保证种子数据永不过期腐烂
INSERT INTO `ingredient` (`id`, `fridge_id`, `category_id`, `name`, `quantity`, `unit`, `status`, `expiry_date`, `production_date`, `recognition_mode`) VALUES
(1,  1, 1, '苹果',     6.00,  '个', 1, DATE_ADD(NOW(), INTERVAL 14 DAY), NULL,         'manual'),
(2,  1, 1, '香蕉',     3.00,  '根', 1, DATE_ADD(NOW(), INTERVAL 3 DAY),  NULL,         'manual'),
(3,  1, 2, '西红柿',   4.00,  '个', 1, DATE_ADD(NOW(), INTERVAL 5 DAY),  NULL,         'auto'),
(4,  1, 3, '鸡胸肉',   0.50,  'kg', 2, DATE_ADD(NOW(), INTERVAL 2 DAY),  DATE_ADD(NOW(), INTERVAL -5 DAY), 'auto'),
(5,  1, 4, '鸡蛋',     10.00, '个', 1, DATE_ADD(NOW(), INTERVAL 30 DAY), NULL,         'manual'),
(6,  1, 5, '牛奶',     1.00,  '瓶', 2, DATE_ADD(NOW(), INTERVAL 4 DAY),  NULL,         'manual'),
(7,  2, 6, '可乐',     4.00,  '罐', 1, DATE_ADD(NOW(), INTERVAL 180 DAY), NULL,        'auto'),
(8,  2, 7, '薯片',     2.00,  '袋', 1, DATE_ADD(NOW(), INTERVAL 90 DAY), NULL,         'manual'),
(9,  2, 3, '猪肉',     1.20,  'kg', 3, DATE_ADD(NOW(), INTERVAL -5 DAY), DATE_ADD(NOW(), INTERVAL -13 DAY), 'manual'),
(10, 3, 2, '白菜',     2.00,  '颗', 1, DATE_ADD(NOW(), INTERVAL 7 DAY),  NULL,         'manual'),
(11, 3, 1, '葡萄',     1.50,  'kg', 1, DATE_ADD(NOW(), INTERVAL 5 DAY),  NULL,         'auto'),
(12, 3, 5, '酸奶',     6.00,  '盒', 1, DATE_ADD(NOW(), INTERVAL 12 DAY), NULL,         'manual');

-- ----- 库存变更日志（15条） -----
INSERT INTO `inventory_log` (`id`, `ingredient_id`, `fridge_id`, `user_id`, `type`, `source`, `quantity_change`, `quantity_after`, `created_at`) VALUES
(1,  1,  1, 1, 1, 1, 6.00,  6.00,  DATE_ADD(NOW(), INTERVAL -6 HOUR)),
(2,  2,  1, 1, 1, 1, 3.00,  3.00,  DATE_ADD(NOW(), INTERVAL -5 HOUR)),
(3,  3,  1, 3, 1, 2, 4.00,  4.00,  DATE_ADD(NOW(), INTERVAL -5 HOUR)),
(4,  4,  1, 1, 1, 2, 0.50,  0.50,  DATE_ADD(NOW(), INTERVAL -6 HOUR)),
(5,  5,  1, 5, 1, 1, 10.00, 10.00, DATE_ADD(NOW(), INTERVAL -4 HOUR)),
(6,  6,  1, 1, 1, 1, 1.00,  1.00,  DATE_ADD(NOW(), INTERVAL -7 HOUR)),
(7,  7,  2, 1, 1, 2, 4.00,  4.00,  DATE_ADD(NOW(), INTERVAL -4 HOUR)),
(8,  8,  2, 3, 1, 1, 2.00,  2.00,  DATE_ADD(NOW(), INTERVAL -3 HOUR)),
(9,  9,  2, 1, 1, 1, 1.20,  1.20,  DATE_ADD(NOW(), INTERVAL -8 HOUR)),
(10, 10, 3, 2, 1, 1, 2.00,  2.00,  DATE_ADD(NOW(), INTERVAL -3 HOUR)),
(11, 11, 3, 6, 1, 2, 1.50,  1.50,  DATE_ADD(NOW(), INTERVAL -2 HOUR)),
(12, 12, 3, 2, 1, 1, 6.00,  6.00,  DATE_ADD(NOW(), INTERVAL -2 HOUR)),
(13, 9,  2, 3, 3, 1, 0,     1.20,  DATE_ADD(NOW(), INTERVAL -1 HOUR)),  -- 猪肉过期
(14, 6,  1, 5, 2, 1, -0.50, 0.50,  DATE_ADD(NOW(), INTERVAL -1 HOUR)),  -- 牛奶喝掉半瓶
(15, 4,  1, 1, 2, 1, -0.25, 0.25,  DATE_ADD(NOW(), INTERVAL -30 MINUTE));  -- 鸡胸肉用掉一半

-- ----- 审计日志（10条） -----
INSERT INTO `audit_log` (`id`, `user_id`, `family_id`, `action`, `target`, `created_at`) VALUES
(1,  1, 1, 'CREATE_FAMILY',   '创建家庭"张家厨房"',    '2026-06-20 14:00:00'),
(2,  1, 1, 'ADD_INGREDIENT',  '添加食材"苹果"',        DATE_ADD(NOW(), INTERVAL -6 HOUR)),
(3,  1, 1, 'ADD_INGREDIENT',  '添加食材"鸡胸肉"',      DATE_ADD(NOW(), INTERVAL -6 HOUR)),
(4,  3, 1, 'ADD_INGREDIENT',  '添加食材"西红柿"',      DATE_ADD(NOW(), INTERVAL -5 HOUR)),
(5,  2, 2, 'CREATE_FAMILY',   '创建家庭"快乐家庭"',    '2026-06-21 10:00:00'),
(6,  2, 2, 'ADD_INGREDIENT',  '添加食材"白菜"',        DATE_ADD(NOW(), INTERVAL -3 HOUR)),
(7,  6, 2, 'ADD_INGREDIENT',  '添加食材"葡萄"',        DATE_ADD(NOW(), INTERVAL -2 HOUR)),
(8,  1, 1, 'KICK_MEMBER',     '踢出成员（测试用户）',  '2026-06-21 16:00:00'),
(9,  2, 2, 'TRANSFER_OWNER',  '转让群主给小红（测试）', '2026-06-21 18:00:00'),
(10, 1, 1, 'RECOGNIZE_INGREDIENT', 'AI识别"西红柿"',  '2026-06-22 09:04:00');

-- ----- 用户反馈（3条） -----
INSERT INTO `feedback` (`id`, `user_id`, `content`, `status`, `reply`, `created_at`) VALUES
(1, 3, 'AI识别有时候会把土豆认成红薯，希望能改进', 0, NULL, '2026-06-22 11:00:00'),
(2, 6, '希望能增加批量添加食材的功能，一个一个加太慢了', 0, NULL, '2026-06-22 11:30:00'),
(3, 1, '冰箱设备SN绑定后无法解绑，需要帮助', 1, '已联系您处理，请查看家庭设置中的解绑按钮', '2026-06-21 15:00:00');

-- ----- 购物清单（6条，家庭1和家庭2各3条） -----
INSERT INTO `shopping_list` (`id`, `family_id`, `name`, `quantity`, `unit`, `category_id`, `is_purchased`, `added_by`, `checked_by`, `checked_at`, `created_at`) VALUES
(1, 1, '牛奶',     2.00, '瓶', 5, 0, 1, NULL, NULL,          DATE_ADD(NOW(), INTERVAL -6 HOUR)),
(2, 1, '鸡蛋',     1.00, '盒', 4, 0, 3, NULL, NULL,          '2026-06-22 09:30:00'),
(3, 1, '面包',     1.00, '袋', 7, 1, 1, 1,    DATE_ADD(NOW(), INTERVAL -3 HOUR), '2026-06-21 18:00:00'),
(4, 2, '西红柿',   0.50, 'kg', 2, 0, 2, NULL, NULL,          DATE_ADD(NOW(), INTERVAL -7 HOUR)),
(5, 2, '酱油',     1.00, '瓶', 8, 0, 6, NULL, NULL,          DATE_ADD(NOW(), INTERVAL -1 HOUR)),
(6, 2, '酸奶',     3.00, '盒', 5, 1, 2, 2,    '2026-06-22 11:00:00', '2026-06-21 20:00:00');

-- ============================================================
-- 12. AI 对话消息表（单时间线，按用户存储；历史 6 轮作上下文）
-- ============================================================
CREATE TABLE IF NOT EXISTS `chat_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '消息ID',
    `user_id`    BIGINT       NOT NULL                COMMENT '用户ID（按用户隔离）',
    `role`       TINYINT      NOT NULL                COMMENT '角色：0=用户, 1=AI助手',
    `content`    TEXT         NOT NULL                COMMENT '消息内容',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话消息表（单时间线，按用户存储）';

-- ============================================================
-- 13. 食谱表（爬虫数据：美食天下 13 分类共 1241 道）
-- recipe_code 为内容哈希幂等键（rc_ + md5(name|time|steps)[:12]），
-- 同一道菜重复爬取不会产生重复行；图片只存相对路径，
-- 实际文件位于 data/recipes/{recipe_code}/ 下
-- ============================================================
CREATE TABLE IF NOT EXISTS `recipe` (
    `id`               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '物理主键',
    `recipe_code`      VARCHAR(64)  NOT NULL                COMMENT '内容哈希幂等键 rc_xxxxxxxxxxxx',
    `name`             VARCHAR(100) NOT NULL                COMMENT '菜名',
    `description`      VARCHAR(1000) NOT NULL DEFAULT ''    COMMENT '菜品描述',
    `image_path`       VARCHAR(255) NOT NULL                COMMENT '主图相对路径 images/main.jpg',
    `taste`            VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '口味：奶香/甜味/清淡…',
    `method`           VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '工艺：煎/蒸/煮/炒…',
    `difficulty`       VARCHAR(10)  NOT NULL DEFAULT ''     COMMENT '难度：简单/初级/中级/高级',
    `cooking_time`     VARCHAR(20)  NOT NULL DEFAULT ''     COMMENT '原文耗时：十分钟/一小时/廿分钟',
    `cooking_time_min` INT                   DEFAULT NULL   COMMENT '分钟数（未知=NULL，用于数值筛选）',
    `ingredients`      JSON         NOT NULL                COMMENT '[{"name","amount","group"}]',
    `steps`            JSON         NOT NULL                COMMENT '[{"image","text"}]',
    `tags`             JSON                   DEFAULT NULL  COMMENT '["面食","早餐","饼"]',
    `category`         VARCHAR(20)  NOT NULL                COMMENT '13分类之一：热菜/凉菜/汤羹…',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    UNIQUE KEY `uk_recipe_code` (`recipe_code`),
    KEY `idx_category` (`category`),
    KEY `idx_created_id` (`created_at`, `id`)   -- 浏览页按入库时间倒序翻页；created_at 大面积同值，靠 id 兜底才能不重不漏
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食谱表';

-- ============================================================
-- 14. 家庭点餐表（一餐制：一个家庭只维护一张"当前菜单"，吃完清空重来）
-- 无日期/无餐型/无状态——"一家人决定安排吃一顿饭"的一次性场景
-- ============================================================
CREATE TABLE `meal_plan` (
    `id`          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '点餐记录ID',
    `family_id`   BIGINT       NOT NULL                COMMENT '家庭ID',
    `recipe_code` VARCHAR(64)  NOT NULL                COMMENT '点的食谱（关联 recipe）',
    `user_id`     BIGINT       NOT NULL                COMMENT '谁点的',
    `note`        VARCHAR(200) NOT NULL DEFAULT ''     COMMENT '备注：少放辣/不放葱',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点的时间',
    `archived`    TINYINT      NOT NULL DEFAULT 0      COMMENT '0=当前菜单 1=已归档(吃完啦,历史可查)',
    `archived_at` DATETIME              DEFAULT NULL   COMMENT '归档时间',
    -- 不给 (family_id, recipe_code) 建唯一键：归档记录会长期占坑，导致"吃完归档后同一道菜再也点不了"；
    -- 重复点餐由 MealServiceImpl.add 预检"当前菜单(archived=0)"拦截
    KEY `idx_family` (`family_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭点餐记录（当前这一餐 + 归档历史）';

-- ============================================================
-- 15. 家庭食谱收藏表（共享收藏池：谁收藏的大家都看到、都能点）
-- ============================================================
CREATE TABLE `recipe_favorite` (
    `id`          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '收藏记录ID',
    `family_id`   BIGINT      NOT NULL                COMMENT '家庭ID',
    `recipe_code` VARCHAR(64) NOT NULL                COMMENT '收藏的食谱',
    `user_id`     BIGINT      NOT NULL                COMMENT '收藏人',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    UNIQUE KEY `uk_family_recipe` (`family_id`, `recipe_code`) COMMENT '同一家庭一道菜只收藏一次'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家庭食谱收藏';
