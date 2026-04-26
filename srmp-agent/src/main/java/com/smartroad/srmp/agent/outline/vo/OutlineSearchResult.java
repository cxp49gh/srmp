package com.smartroad.srmp.agent.outline.vo;

import lombok.Data;

@Data
public class OutlineSearchResult {
    private String id;
    private String title;
    private String text;
    private String url;
    private Double score;
}
