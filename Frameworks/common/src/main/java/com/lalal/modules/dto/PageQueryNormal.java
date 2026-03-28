package com.lalal.modules.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 普通分页查询基类
 * 适用于数据量小、无需分库分表的场景
 */
@Data
public class PageQueryNormal implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，从1开始
     */
    private Integer pageNum = 1;

    /**
     * 每页大小，默认 20
     */
    private Integer pageSize = 20;

    /**
     * 搜索关键字
     */
    private String keyword;

    /**
     * 获取实际 pageNum，防止小于1
     */
    public Integer getPageNum() {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }
        return pageNum;
    }

    /**
     * 获取实际 pageSize，防止过大
     */
    public Integer getPageSize() {
        if (pageSize == null || pageSize <= 0) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    /**
     * 计算 OFFSET
     */
    public int getOffset() {
        return (getPageNum() - 1) * getPageSize();
    }
}
