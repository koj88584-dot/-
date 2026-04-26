package com.hmdp.service;

import com.hmdp.dto.Result;

public interface ICityHotNewsService {

    Result queryHotNews(String cityCode, Integer current);
}
