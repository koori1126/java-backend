package com.example.backend.controller;

import com.example.backend.model.dto.CsvImportResult;
import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import com.example.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;


/**
 * ユーザーAPIのエンドポイント定義。
 * 入出力のマッピングと呼び出しに専念し、ビジネスロジックは Service に委譲する。
 *
 * === 新規エンドポイント追加の手順 ===
 * 1. model/entity にテーブルに対応するエンティティを追加（無ければ）
 * 2. resources/db/migration に Flyway マイグレーションSQLを追加
 * 3. repository にリポジトリインターフェースを追加
 * 4. model/dto にリクエスト/レスポンスDTOを追加
 * 5. service にインターフェースと実装(impl)を追加してビジネスロジックを実装
 * 6. controller にこのクラスのようにエンドポイントを追加し、Serviceを呼び出す
 * 7. 必要ならテストを追加（src/test 配下）
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "ユーザー管理API")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "ユーザー一覧取得")
    public ResponseEntity<List<UserResponse>> getUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "ユーザー単体取得")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PostMapping
    @Operation(summary = "ユーザー新規作成")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/users/" + created.getId()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "ユーザー更新")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "ユーザー削除")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.delete(id);
    }

    /**
     * CSVファイルからユーザーを一括登録する。
     * ヘッダー行に name,email,firstname,familyname の列名を含むCSVを受け付ける。
     * 1行でもエラーがあっても処理全体は中断せず、行ごとの成否をまとめて返す。
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "CSVによるユーザー一括登録")
    public ResponseEntity<CsvImportResult> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        CsvImportResult result = userService.importFromCsv(file);
        return ResponseEntity.ok(result);
    }

    /**
     * 現在登録されているユーザー(論理削除済みを除く)をCSVファイルとして出力する。
     * Excelでそのまま開いても文字化けしないよう、UTF-8のBOM付きで出力する。
     */
    @GetMapping("/export")
    @Operation(summary = "ユーザー一覧のCSV出力")
    public void exportUsers(HttpServletResponse response) throws IOException {
        String filename = "users_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        // ExcelでUTF-8のCSVを開いた際の文字化けを防ぐため、BOMを先頭に付与する
        response.getWriter().write('\uFEFF');
        userService.exportToCsv(response.getWriter());
    }
}
