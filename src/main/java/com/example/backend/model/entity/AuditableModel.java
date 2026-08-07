package com.example.backend.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * created_at / created_by / updated_at / updated_by を持つテーブル共通の
 * 監査カラムをまとめた基底クラス(MyBatis用。JPAのようなエンティティ
 * ライフサイクル機構は無いため、日時のセットはSQL側(now())または
 * サービス層で明示的に行う。詳細は各Mapper XMLとServiceImplを参照)。
 *
 * 【使い方】
 * 新しいモデルで同じ4カラムを持つ場合、このクラスを継承するだけで
 * フィールド定義が引き継がれる。
 *
 *   public class Product extends AuditableModel {
 *       ...(products固有のカラムのみ定義)
 *   }
 */
@Getter
@Setter
public abstract class AuditableModel {
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
