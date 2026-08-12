package com.example.backend.controller;

import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import com.example.backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller層の単体テストのサンプル。
 * Service はモック化し、リクエスト/レスポンスのマッピングのみを検証する。
 */
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    void createUser_returns201() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("Taro Yamada");
        request.setEmail("taro@example.com");
        request.setFirstname("Taro");
        request.setFamilyname("Yamada");

        UserResponse response = UserResponse.builder()
                .id(1L)
                .name("Taro Yamada")
                .email("taro@example.com")
                .firstname("Taro")
                .familyname("Yamada")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(userService.create(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("taro@example.com"));
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        UserRequest request = new UserRequest();
        request.setName("Taro Yamada");
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
