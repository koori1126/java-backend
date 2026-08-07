package com.example.backend.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * public.users テーブルに対応するモデル(MyBatisのマッピング対象)。
 * created_at/created_by/updated_at/updated_by は AuditableModel で共通定義している。
 *
 * 【論理削除について】
 * del_flag カラムにより論理削除(ソフトデリート)方式を採用しています。
 * 物理削除(DELETE文)は行わず、削除時は del_flag = true に更新するのみです。
 * 一覧取得・単体取得は del_flag が true のレコードを除外します
 * (詳細は UserMapper.xml / UserServiceImpl を参照)。
 *
 * JPAと異なり、テーブルとのマッピングはこのクラスのフィールド名と
 * mapper/UserMapper.xml 側のresultMapで対応付けている(実行時のリフレクションによる
 * 自動マッピングではなく、SQLとJavaオブジェクトの対応を明示的に管理する)。
 */
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableModel {

    private Long id;
    private String name;
    private String email;
    private String firstname;
    private String familyname;
    private Boolean delFlag;
}
