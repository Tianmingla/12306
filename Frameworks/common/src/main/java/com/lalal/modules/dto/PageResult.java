package com.lalal.modules.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应
 * 支持游标分页和普通分页两种模式
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> list;

    /**
     * 总记录数
     */
    private Long total;

    // ========== 游标分页字段 ==========

    /**
     * 下一页起始ID（游标）
     */
    private Long nextId;

    /**
     * 是否有更多数据
     */
    private Boolean hasMore;

    // ========== 普通分页字段 ==========

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    // ========== 游标分页工厂方法 ==========

    public static <T> PageResult<T> ofCursor(List<T> list, Long total, Long nextId, boolean hasMore) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setNextId(nextId);
        result.setHasMore(hasMore);
        return result;
    }

    public static <T> PageResult<T> of(List<T> list, Long total, Long nextId, boolean hasMore) {
        return ofCursor(list, total, nextId, hasMore);
    }

    // ========== 普通分页工厂方法 ==========

    public static <T> PageResult<T> ofPage(List<T> list, Long total, Integer pageNum, Integer pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setHasMore((long) pageNum * pageSize < total);
        return result;
    }

    /**
     * 空结果
     */
    public static <T> PageResult<T> empty() {
        PageResult<T> result = new PageResult<>();
        result.setList(Collections.emptyList());
        result.setTotal(0L);
        result.setNextId(null);
        result.setHasMore(false);
        result.setPageNum(1);
        result.setPageSize(20);
        return result;
    }
}
