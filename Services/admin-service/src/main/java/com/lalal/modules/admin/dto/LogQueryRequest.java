package com.lalal.modules.admin.dto;

import com.lalal.modules.dto.PageQueryNormal;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 操作日志查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogQueryRequest extends PageQueryNormal {

    /**
     * 操作人用户名
     */
    private String adminUsername;

    /**
     * 操作类型
     */
    private String operationType;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作状态
     */
    private Integer status;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;
}
