package com.example.backend.service.impl;

import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import com.example.backend.model.entity.User;
import com.example.backend.repository.UserRepository;
import com.example.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * UserService の実装。
 * DBアクセスは Repository 経由でのみ行い、Controller から直接 Repository を
 * 呼び出さないことで、ビジネスロジックと入出力層を分離する。
 *
 * del_flag による論理削除を採用しているため、delete() は物理削除ではなく
 * del_flag を true に更新するのみ。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAllActive().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = getActiveUserOrThrow(id);
        return UserResponse.from(user);
    }

    @Override
    public UserResponse create(UserRequest request) {
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setFastname(request.getFastname());
        user.setFamilyname(request.getFamilyname());
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    public UserResponse update(Long id, UserRequest request) {
        User user = getActiveUserOrThrow(id);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setFastname(request.getFastname());
        user.setFamilyname(request.getFamilyname());
        return UserResponse.from(userRepository.save(user));
    }

    @Override
    public void delete(Long id) {
        User user = getActiveUserOrThrow(id);
        user.setDelFlag(true);
        userRepository.save(user);
    }

    private User getActiveUserOrThrow(Long id) {
        return userRepository.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found. id=" + id));
    }
}
