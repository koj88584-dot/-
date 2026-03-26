package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeepSeekChatRequestDTO {
    private String message;
    private String scene;
    private List<AssistantMessageDTO> history;
}
