package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IGroupDealOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/group-deals")
public class GroupDealController {

    @Resource
    private IGroupDealOrderService groupDealOrderService;

    @PostMapping("/{id}/orders")
    public Result createOrder(@PathVariable("id") Long id) {
        return groupDealOrderService.createOrder(id);
    }

    @GetMapping("/orders/my")
    public Result queryMyOrders(@RequestParam(value = "status", required = false) Integer status,
                                @RequestParam(value = "commented", required = false) Integer commented,
                                @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return groupDealOrderService.queryMyOrders(status, commented, current);
    }
}
