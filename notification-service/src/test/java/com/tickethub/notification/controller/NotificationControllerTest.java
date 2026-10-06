package com.tickethub.notification.controller;

import com.tickethub.notification.repository.NotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired NotificationRepository repository;
    @MockBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void sendEmail_returns201_andTheDeliveryRecord() throws Exception {
        mockMvc.perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"customer@tickethub.test","recipientName":"Asha",
                                 "subject":"Welcome","message":"Thanks for joining TicketHub"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.recipient").value("customer@tickethub.test"))
                .andExpect(jsonPath("$.notificationId").exists());
    }

    @Test
    void sendEmail_withInvalidAddress_returns400() throws Exception {
        mockMvc.perform(post("/api/notifications/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"to":"not-an-email","subject":"Hi","message":"Body"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void sameIdempotencyKey_returns200_theSecondTime() throws Exception {
        String body = """
                {"to":"customer@tickethub.test","subject":"Hi","message":"Body","idempotencyKey":"abc-1"}""";
        mockMvc.perform(post("/api/notifications/email").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/notifications/email").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void trigger_bookingConfirmed_returns201_andCanBeListedByBooking() throws Exception {
        mockMvc.perform(post("/api/notifications/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"BOOKING_CONFIRMED","recipientEmail":"customer@tickethub.test","bookingId":77,
                                 "amount":500,"currency":"INR","showTitle":"Dune","seats":["B4","B5"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("BOOKING_CONFIRMED"));

        mockMvc.perform(get("/api/notifications").param("bookingId", "77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SENT"));
    }

    @Test
    void unknownNotification_returns404() throws Exception {
        mockMvc.perform(get("/api/notifications/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
