package com.smartroad.srmp.common.core;

import lombok.Data;
import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {
    private Long total = 0L;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private List<T> records = Collections.emptyList();

    public static <T> PageResult<T> empty(Integer pageNo, Integer pageSize) {
        PageResult<T> page = new PageResult<>();
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        return page;
    }
}
