package com.finansportali.backend.controller;

import com.finansportali.backend.entity.ViopContract;
import com.finansportali.backend.service.ViopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/viop")
public class ViopController {

    private final ViopService service;

    public ViopController(ViopService service) {
        this.service = service;
    }

    @GetMapping
    public List<ViopContract> list(@RequestParam(required = false) String category) {
        if (category == null || category.isBlank()) {
            return service.findAll();
        }
        try {
            return service.findByCategory(
                    ViopContract.Category.valueOf(category.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }
}
