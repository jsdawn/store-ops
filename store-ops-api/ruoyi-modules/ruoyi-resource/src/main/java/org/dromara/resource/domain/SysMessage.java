package org.dromara.resource.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/**
 * 消息记录表 sys_message
 *
 * @author Lion Li
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
public class SysMessage extends BaseEntity {

    @TableId(value = "message_id")
    private Long messageId;

    private String category;

    private String type;

    private String source;

    private String title;

    private String message;

    private String content;

    private String dataJson;

    private String path;

    private String sendUserIds;
}
