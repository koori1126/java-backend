package com.example.backend.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * ユーザー作成/更新リクエスト用DTO。
 * del_flag / created_at / created_by / updated_at / updated_by は
 * システム側で管理するため、リクエストには含めません。
 */
@Getter
@Setter
public class UserRequest {

    @Size(max = 50, message = "name は50文字以内で入力してください")
    private String name;

    @Email(message = "email の形式が不正です")
    @Size(max = 100)
    private String email;

    @Size(max = 50, message = "firstname は50文字以内で入力してください")
    private String firstname;

    @Size(max = 50, message = "familyname は50文字以内で入力してください")
    private String familyname;
}
