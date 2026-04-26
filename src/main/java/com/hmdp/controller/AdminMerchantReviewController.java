package com.hmdp.controller;

import com.hmdp.dto.AdminReviewActionDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.impl.MerchantApplicationFlowService;
import com.hmdp.service.impl.MerchantAuthService;
import com.hmdp.service.impl.MerchantShopManagementService;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/admin")
public class AdminMerchantReviewController {

    @Resource
    private MerchantApplicationFlowService merchantApplicationFlowService;
    @Resource
    private MerchantAuthService merchantAuthService;
    @Resource
    private MerchantShopManagementService merchantShopManagementService;

    @GetMapping("/merchant-applications")
    public Result listMerchantApplications(@RequestParam(value = "status", required = false) Integer status) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return Result.ok(merchantApplicationFlowService.listMerchantApplications(status));
    }

    @PostMapping("/merchant-applications/{id}/approve")
    public Result approveMerchantApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.approveMerchantApplication(id, UserHolder.getUser().getId(), dto);
    }

    @PostMapping("/merchant-applications/{id}/reject")
    public Result rejectMerchantApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.rejectMerchantApplication(id, UserHolder.getUser().getId(), dto);
    }

    @GetMapping("/shop-claim-applications")
    public Result listShopClaimApplications(@RequestParam(value = "status", required = false) Integer status) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return Result.ok(merchantApplicationFlowService.listShopClaimApplications(status));
    }

    @PostMapping("/shop-claim-applications/{id}/approve")
    public Result approveShopClaimApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.approveShopClaimApplication(id, UserHolder.getUser().getId(), dto);
    }

    @PostMapping("/shop-claim-applications/{id}/reject")
    public Result rejectShopClaimApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.rejectShopClaimApplication(id, UserHolder.getUser().getId(), dto);
    }

    @GetMapping("/shop-create-applications")
    public Result listShopCreateApplications(@RequestParam(value = "status", required = false) Integer status) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return Result.ok(merchantApplicationFlowService.listShopCreateApplications(status));
    }

    @PostMapping("/shop-create-applications/{id}/approve")
    public Result approveShopCreateApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.approveShopCreateApplication(id, UserHolder.getUser().getId(), dto);
    }

    @PostMapping("/shop-create-applications/{id}/reject")
    public Result rejectShopCreateApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return merchantApplicationFlowService.rejectShopCreateApplication(id, UserHolder.getUser().getId(), dto);
    }

    @GetMapping("/shop-update-applications")
    public Result listShopUpdateApplications(@RequestParam(value = "status", required = false) Integer status) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        return Result.ok(merchantShopManagementService.listUpdateApplications(status));
    }

    @PostMapping("/shop-update-applications/{id}/approve")
    public Result approveShopUpdateApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        String remark = dto == null ? null : dto.getReviewRemark();
        return merchantShopManagementService.approveUpdateApplication(id, UserHolder.getUser().getId(), remark);
    }

    @PostMapping("/shop-update-applications/{id}/reject")
    public Result rejectShopUpdateApplication(@PathVariable("id") Long id, @RequestBody(required = false) AdminReviewActionDTO dto) {
        Result access = assertAdmin();
        if (access != null) {
            return access;
        }
        String remark = dto == null ? null : dto.getReviewRemark();
        return merchantShopManagementService.rejectUpdateApplication(id, UserHolder.getUser().getId(), remark);
    }

    private Result assertAdmin() {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        if (!merchantAuthService.isAdmin(UserHolder.getUser().getId())) {
            return Result.fail("当前账号暂无管理员权限");
        }
        return null;
    }
}
