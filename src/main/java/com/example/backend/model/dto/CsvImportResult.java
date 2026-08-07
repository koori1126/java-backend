package com.example.backend.model.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * CSV一括登録の結果サマリ。
 * 1行でもエラーがあっても全体を失敗させず、成功した行は登録した上で
 * エラー行の内容(行番号+理由)を返す方式にしている。
 */
@Getter
@Builder
public class CsvImportResult {

    private int totalCount;
    private int successCount;
    private int failureCount;
    private List<RowError> errors;

    @Getter
    @Builder
    public static class RowError {
        /** ヘッダー行を1行目として数えた、CSV上の行番号 */
        private int lineNumber;
        private String reason;
    }
}
