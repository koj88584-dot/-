package com.hmdp.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DeepSeekChatResponseDTO {
    private String reply;
    private Boolean configured;
    private List<AssistantActionDTO> actions = new ArrayList<>();
}
