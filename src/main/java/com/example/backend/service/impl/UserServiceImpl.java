package com.example.backend.service.impl;

import com.example.backend.exception.DuplicateEmailException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.mapper.UserMapper;
import com.example.backend.model.dto.CsvImportResult;
import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import com.example.backend.model.entity.User;
import com.example.backend.service.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * UserService の実装。
 * DBアクセスは UserMapper(MyBatis) 経由でのみ行い、Controller から直接
 * Mapper を呼び出さないことで、ビジネスロジックと入出力層を分離する。
 *
 * del_flag による論理削除を採用しているため、delete() は物理削除ではなく
 * del_flag を true に更新するのみ(UserMapper#softDelete)。
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final Validator validator;

    /**
     * CSVのヘッダー行に期待する列名。
     * 列の順序ではなく列名でマッピングするため、順不同でも動作する。
     */
    private static final String[] CSV_HEADERS = {"name", "email", "firstname", "familyname"};

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userMapper.findAllActive().stream()
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
        // アプリ側の事前チェック(親切なエラーメッセージを早く返すための最適化)。
        // 並列リクエストによるすり抜けの最終防衛はDB側のUNIQUE制約
        // (users_email_unique)とGlobalExceptionHandlerでのDataIntegrityViolation
        // Exceptionハンドリングに委ねている。
        if (request.getEmail() != null && userMapper.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setFirstname(request.getFirstname());
        user.setFamilyname(request.getFamilyname());
        userMapper.insert(user);
        // insert後、MyBatisのuseGeneratedKeysによりuser.idに採番されたIDが入っている
        return UserResponse.from(user);
    }

    @Override
    public UserResponse update(Long id, UserRequest request) {
        User user = getActiveUserOrThrow(id);

        boolean emailChanged = request.getEmail() != null && !request.getEmail().equals(user.getEmail());
        if (emailChanged && userMapper.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setFirstname(request.getFirstname());
        user.setFamilyname(request.getFamilyname());
        userMapper.update(user);
        return UserResponse.from(user);
    }

    @Override
    public void delete(Long id) {
        // 存在確認(既に削除済み/存在しないIDを指定された場合は404にする)
        getActiveUserOrThrow(id);
        userMapper.softDelete(id);
    }

    /**
     * CSVの1行が name, email, firstname, familyname の列を持つ前提。
     * 例:
     *   name,email,firstname,familyname
     *   山田太郎,taro@example.com,Taro,Yamada
     *
     * 1行ごとにBean Validationのアノテーション(UserRequestの@Email等)を
     * 適用して検証し、エラーがあった行はスキップして次の行を処理する。
     * 1件でも成功した分はDBに反映される(全体をロールバックしない方式)。
     */
    @Override
    public CsvImportResult importFromCsv(MultipartFile file) throws IOException {
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        int successCount = 0;
        int totalCount = 0;

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreHeaderCase(true)
                .build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = new CSVParser(reader, format)) {

            for (CSVRecord record : parser) {
                totalCount++;
                // ヘッダー行を1行目として数えるため +1
                int lineNumber = (int) record.getRecordNumber() + 1;

                UserRequest request = new UserRequest();
                request.setName(getOrNull(record, "name"));
                request.setEmail(getOrNull(record, "email"));
                request.setFirstname(getOrNull(record, "firstname"));
                request.setFamilyname(getOrNull(record, "familyname"));

                Set<ConstraintViolation<UserRequest>> violations = validator.validate(request);
                if (!violations.isEmpty()) {
                    String reason = violations.stream()
                            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                            .reduce((a, b) -> a + " / " + b)
                            .orElse("入力値が不正です");
                    errors.add(CsvImportResult.RowError.builder()
                            .lineNumber(lineNumber)
                            .reason(reason)
                            .build());
                    continue;
                }

                try {
                    create(request);
                    successCount++;
                } catch (Exception e) {
                    errors.add(CsvImportResult.RowError.builder()
                            .lineNumber(lineNumber)
                            .reason("登録に失敗しました: " + e.getMessage())
                            .build());
                }
            }
        }

        return CsvImportResult.builder()
                .totalCount(totalCount)
                .successCount(successCount)
                .failureCount(errors.size())
                .errors(errors)
                .build();
    }

    private String getOrNull(CSVRecord record, String column) {
        if (!record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        return (value == null || value.isBlank()) ? null : value;
    }

    private User getActiveUserOrThrow(Long id) {
        return userMapper.findActiveById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found. id=" + id));
    }
}
