package com.example.backend.service;

import com.example.backend.model.dto.CsvImportResult;
import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    /**
     * CSVファイルの内容から複数ユーザーを一括登録する。
     * 1行ごとに検証し、エラーがあった行はスキップして処理を続行する。
     */
    CsvImportResult importFromCsv(MultipartFile file) throws IOException;
}
