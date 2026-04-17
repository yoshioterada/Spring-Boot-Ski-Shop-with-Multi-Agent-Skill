package com.example.skishop.agent.coupon.config;

import com.example.skishop.agent.coupon.client.CouponServiceClient;
import com.example.skishop.agent.coupon.client.PointServiceClient;
import com.example.skishop.agent.coupon.controller.CouponOptimizationController;
import com.example.skishop.agent.coupon.service.CouponOptimizationAgentService;
import com.example.skishop.agent.coupon.tool.CouponOptimizationToolService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    CouponOptimizationAgentConfig.class,
    CouponServiceClient.class,
    PointServiceClient.class,
    CouponOptimizationToolService.class,
    CouponOptimizationAgentService.class,
    CouponOptimizationController.class,
    CouponOptimizationAgentSecurityConfig.class
})
public class CouponOptimizationAgentAutoConfiguration {
}
