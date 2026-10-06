package com.tickethub.payment.controller;

import com.tickethub.payment.exception.InvalidSignatureException;
import com.tickethub.payment.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the HTTP contract Razorpay depends on: raw body + headers go in, 2xx comes out only on success. */
@WebMvcTest(PaymentWebhookController.class)
class PaymentWebhookControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PaymentWebhookService webhookService;

    @Test
    void webhook_passesRawBytesAndHeadersToTheService_andAnswers200() throws Exception {
        String body = "{\"event\":\"payment.captured\"}";
        when(webhookService.handle(any(), eq("sig123"), eq("evt_9"))).thenReturn("processed");

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "sig123")
                        .header("X-Razorpay-Event-Id", "evt_9")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processed"));

        verify(webhookService).handle(eq(body.getBytes(StandardCharsets.UTF_8)), eq("sig123"), eq("evt_9"));
    }

    @Test
    void webhook_withBadSignature_answers400_soNothingIsAcknowledged() throws Exception {
        when(webhookService.handle(any(), any(), any())).thenThrow(new InvalidSignatureException("bad"));

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "forged")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
