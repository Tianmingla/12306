package com.lalal.modules.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 游标分页查询基类
 * 兼容分库分表场景，避免 OFFSET 深分页性能问题
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上一页最后一条记录的ID（游标）
     * 首页查询时传 null 或不传
     */
    private Long lastId;

    /**
     * 每页大小，默认 20
     */
    private Integer pageSize = 20;

    /**
     * 搜索关键字
     */
    private String keyword;

    /**
     * 获取实际 pageSize，防止过大
     */
    public Integer getPageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }
}
