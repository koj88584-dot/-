package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CityHotNewsDTO {

    private String id;

    private String title;

    private String summary;

    private String source;

    private String sourceUrl;

    private String image;

    private LocalDateTime publishTime;

    private Integer heat;

    private List<String> keywords;

    private List<String> matchedKeywords;

    private Integer localizedScore;

    private String debugReason;

    private Boolean realSource;

    private Boolean fallback;
}
