package com.tickethub.notification.controller;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "notification.security.api-key=s3cret")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeySecurityTest {

    private static final String BODY = """
            {"to":"customer@tickethub.test","subject":"Hi","message":"Body"}""";

    @Autowired MockMvc mockMvc;
    @MockBean JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void withoutKey_returns401() throws Exception {
        mockMvc.perform(post("/api/notifications/email").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withWrongKey_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications").header("X-API-Key", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withRightKey_isAccepted() throws Exception {
        mockMvc.perform(post("/api/notifications/email").header("X-API-Key", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void healthEndpoint_staysOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
