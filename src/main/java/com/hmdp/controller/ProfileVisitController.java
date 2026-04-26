package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IProfileVisitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/profile-visit")
public class ProfileVisitController {

    @Resource
    private IProfileVisitService profileVisitService;

    @PostMapping("/record/{visitedId}")
    public Result recordVisit(@PathVariable("visitedId") Long visitedId) {
        return profileVisitService.recordVisit(visitedId);
    }

    @GetMapping("/list")
    public Result queryMyVisitors(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return profileVisitService.queryMyVisitors(current);
    }

    @GetMapping("/count")
    public Result countUnread() {
        return profileVisitService.countUnread();
    }

    @PostMapping("/read")
    public Result markAllRead() {
        return profileVisitService.markAllRead();
    }
}
