package com.lalal.modules.admin.dao;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 操作日志实体
 */
@Data
@TableName("t_operation_log")
public class OperationLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作人ID
     */
    private Long adminUserId;

    /**
     * 操作人用户名
     */
    private String adminUsername;

    /**
     * 操作类型: CREATE/UPDATE/DELETE/LOGIN/EXPORT
     */
    private String operationType;

    /**
     * 操作模块
     */
    private String module;

    /**
     * 操作描述
     */
    private String description;

    /**
     * 请求方法
     */
    private String requestMethod;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * 请求参数
     */
    private String requestParams;

    /**
     * 响应结果
     */
    private String responseResult;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 操作状态: 0-成功, 1-失败
     */
    private Integer status;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 执行时长(毫秒)
     */
    private Long duration;

    /**
     * 创建时间
     */
    private Date createTime;
}
