package com.example.backend.mapper;

import com.example.backend.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * users テーブルへのデータアクセス(MyBatis)。
 * 実際のSQLは resources/mapper/UserMapper.xml に定義している。
 *
 * del_flag による論理削除を採用しているため、一覧・単体取得系のメソッドは
 * 削除済み(del_flag = true)のレコードを除外して取得する。
 * 物理削除に方針変更した場合は、これらのメソッドは不要になる。
 */
@Mapper
public interface UserMapper {

    List<User> findAllActive();

    Optional<User> findActiveById(@Param("id") Long id);

    Optional<User> findByEmail(@Param("email") String email);

    boolean existsByEmail(@Param("email") String email);

    /**
     * INSERT実行後、DBが自動採番したidが引数のuser.idにセットされる
     * (UserMapper.xmlの useGeneratedKeys="true" keyProperty="id" による)。
     */
    void insert(User user);

    void update(User user);

    /** 論理削除(del_flag を true に更新するだけ。物理削除は行わない) */
    void softDelete(@Param("id") Long id);
}
