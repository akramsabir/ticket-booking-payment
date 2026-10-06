package com.tickethub.userservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserServiceFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("token").asText();
    }

    @Test
    void fullFlow() throws Exception {
        // register -> 201, CUSTOMER role, audited as "system"
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ravi\",\"lastName\":\"Kumar\",\"email\":\"Ravi@Example.com\","
                                + "\"phone\":\"9876543210\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is("ravi@example.com")))
                .andExpect(jsonPath("$.roles", hasItem("CUSTOMER")))
                .andExpect(jsonPath("$.createdBy", is("system")))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // duplicate email -> 409
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"R\",\"lastName\":\"K\",\"email\":\"ravi@example.com\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isConflict());

        // validation -> 400
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"\",\"lastName\":\"K\",\"email\":\"bad\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());

        // wrong password -> 401
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ravi@example.com\",\"password\":\"wrong-pass\"}"))
                .andExpect(status().isUnauthorized());

        // no token -> 401
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        String customerToken = login("ravi@example.com", "Secret@123");

        // /me works and lastLoginAt is set
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Ravi")))
                .andExpect(jsonPath("$.lastLoginAt").exists());

        // update profile -> updatedBy is the customer's email
        mvc.perform(put("/api/users/me").header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Ravi\",\"lastName\":\"Sharma\",\"phone\":\"9876543210\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName", is("Sharma")))
                .andExpect(jsonPath("$.updatedBy", is("ravi@example.com")));

        // customer cannot use admin endpoints -> 403
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        // admin can list users (seeded admin + ravi)
        String adminToken = login("admin@tickethub.com", "Admin@12345");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // find ravi's id
        String list = mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();
        long raviId = 0;
        for (JsonNode n : mapper.readTree(list)) {
            if (n.get("email").asText().equals("ravi@example.com")) raviId = n.get("id").asLong();
        }

        // admin grants ADMIN + CUSTOMER roles
        mvc.perform(put("/api/admin/users/" + raviId + "/roles").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ADMIN\",\"CUSTOMER\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", hasSize(2)))
                .andExpect(jsonPath("$.updatedBy", is("admin@tickethub.com")));

        // admin blocks ravi -> login now rejected with 403
        mvc.perform(patch("/api/admin/users/" + raviId + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BLOCKED")));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ravi@example.com\",\"password\":\"Secret@123\"}"))
                .andExpect(status().isForbidden());
        // and his old token no longer works
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isUnauthorized());

        // admin cannot block their own account -> 400
        String me = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long adminId = mapper.readTree(me).get("id").asLong();
        mvc.perform(patch("/api/admin/users/" + adminId + "/status").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest());
    }
}
