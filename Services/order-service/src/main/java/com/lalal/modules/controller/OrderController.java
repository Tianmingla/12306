package com.lalal.modules.controller;

import com.lalal.modules.dto.request.OrderCreateRequestDTO;
import com.lalal.modules.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public String create(@RequestBody OrderCreateRequestDTO request) {
        return orderService.createOrder(request);
    }
}
