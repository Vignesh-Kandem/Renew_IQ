package com.renewiq.controller;

import com.renewiq.model.Payment;
import com.renewiq.repository.PaymentRepository;
import com.renewiq.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/payments") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentRepository payments;
    private final CurrentUser current;
    public record History(List<Payment> items, int page, int totalPages, long totalElements) { }
    @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (payments.deleteOwned(id, current.id()) == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Payment not found.");
        }
    }
    @GetMapping public History history(@RequestParam(defaultValue = "0") int page) {
        var result = payments.findByUserIdOrderByIdDesc(current.id(), PageRequest.of(Math.max(0, page), 20));
        return new History(result.getContent(), result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }
}
