package com.tickethub.payment.controller;

import com.tickethub.payment.dto.PaymentResponse;
import com.tickethub.payment.entity.PaymentMethod;
import com.tickethub.payment.entity.PaymentStatus;
import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.exception.PaymentNotFoundException;
import com.tickethub.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-LAYER TEST: starts only the controller + JSON + validation + error handler (no database, no Kafka, no Razorpay).
 * PaymentService is a mock. We check HTTP status codes and JSON shape.
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PaymentService paymentService;

    private static final String VALID_BODY = """
            {
              "bookingId": 101,
              "userId": 7,
              "customerEmail": "asha@example.com",
              "amount": 500.00,
              "currency": "INR",
              "idempotencyKey": "booking-101"
            }
            """;

    private PaymentResponse pendingResponse() {
        return new PaymentResponse(1L, 101L, new BigDecimal("500.00"), 50000L, "INR",
                PaymentMethod.UNSPECIFIED, PaymentStatus.PENDING, "order_abc", null, "rzp_test_key", null, Instant.now());
    }

    @Test
    void createPayment_validRequest_returns201WithCheckoutDetails() throws Exception {
        when(paymentService.createPayment(any())).thenReturn(pendingResponse());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.providerOrderId").value("order_abc"))
                .andExpect(jsonPath("$.razorpayKeyId").value("rzp_test_key"))
                .andExpect(jsonPath("$.amountInMinorUnits").value(50000));
    }

    @Test
    void createPayment_missingAmount_returns400WithFieldError() throws Exception {
        String bodyWithoutAmount = """
                { "bookingId": 101, "userId": 7, "customerEmail": "asha@example.com", "currency": "INR" }
                """;

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWithoutAmount))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void createPayment_amountBelowRazorpayMinimum_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("500.00", "0.50")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void verify_acceptsRazorpaysOwnSnakeCaseFieldNames() throws Exception {
        PaymentResponse paid = new PaymentResponse(1L, 101L, new BigDecimal("500.00"), 50000L, "INR",
                PaymentMethod.UPI, PaymentStatus.SUCCESS, "order_abc", "pay_xyz", null, null, Instant.now());
        when(paymentService.verifyPayment(any())).thenReturn(paid);

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "razorpay_order_id": "order_abc", "razorpay_payment_id": "pay_xyz", "razorpay_signature": "sig" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerPaymentId").value("pay_xyz"));
    }

    @Test
    void verify_wrongSignature_returns400() throws Exception {
        when(paymentService.verifyPayment(any())).thenThrow(new InvalidSignatureException("mismatch"));

        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "razorpayOrderId": "o", "razorpayPaymentId": "p", "razorpaySignature": "bad" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid signature"));
    }

    @Test
    void verify_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"razorpayOrderId\": \"o\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.razorpayPaymentId").exists());
    }

    @Test
    void getPayment_unknownId_returns404() throws Exception {
        when(paymentService.getPayment(99L)).thenThrow(new PaymentNotFoundException(99L));

        mockMvc.perform(get("/api/v1/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
