package com.fridgemind.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
@AllArgsConstructor
public class PageResult<T> {
    private List<T> records;
    private long total;
    private int page;
    private int size;
}
