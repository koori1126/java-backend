package com.example.backend.service;

import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;

import java.util.List;


/**
 * ビジネスロジックのインターフェース。
 * Controller はこのインターフェースにのみ依存し、実装(Impl)には依存しない。
 */
public interface UserService {

    List<UserResponse> findAll();

    UserResponse findById(Long id);

    UserResponse create(UserRequest request);

    UserResponse update(Long id, UserRequest request);

    void delete(Long id);
}
