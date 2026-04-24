package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class CityScenePackDTO {

    private String id;

    private String cityCode;

    private String title;

    private String subtitle;

    private String searchKeyword;

    private Long typeId;

    private String icon;

    private String districtHint;

    private String routeHint;

    private String assistantPrompt;

    private List<String> tags;
}
