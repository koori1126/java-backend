package com.example.backend.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * public.users テーブルに対応するエンティティ。
 *
 * 【論理削除について】
 * del_flag カラムにより論理削除(ソフトデリート)方式を採用しています。
 * 物理削除(DELETE文)は行わず、削除時は del_flag = true に更新するのみです。
 * 一覧取得・単体取得は del_flag が true のレコードを除外します
 * (詳細は UserRepository / UserServiceImpl を参照)。
 *
 * 【created_by / updated_by について】
 * 現時点では認証機能が未実装のため、値はnullのままになります。
 * 認証機能導入後、ログインユーザーの情報をセットするよう実装を追加してください。
 */
@Entity
@Table(name = "users", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "name", length = 50)
    private String name;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "del_flag")
    private Boolean delFlag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "fastname", length = 50)
    private String fastname;

    @Column(name = "familyname", length = 50)
    private String familyname;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.delFlag == null) {
            this.delFlag = false;
        }
        // TODO: 認証機能導入後、createdBy にログインユーザーIDをセットする
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        // TODO: 認証機能導入後、updatedBy にログインユーザーIDをセットする
    }
}
