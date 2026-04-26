package com.smartroad.srmp.common.core;

import lombok.Data;

@Data
public class PageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
