package com.hmdp.service;

import com.hmdp.dto.DeepSeekChatRequestDTO;
import com.hmdp.dto.Result;

public interface IDeepSeekAssistantService {
    Result chat(DeepSeekChatRequestDTO requestDTO);
}
