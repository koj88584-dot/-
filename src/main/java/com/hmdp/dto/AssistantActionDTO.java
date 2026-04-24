package com.hmdp.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class AssistantActionDTO {
    private String type;
    private String label;
    private String description;
    private String path;
    private Boolean autoRun;
    private Map<String, Object> params = new HashMap<>();
}
