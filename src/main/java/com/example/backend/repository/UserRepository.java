package com.example.backend.repository;

import com.example.backend.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * users テーブルへのデータアクセス。
 *
 * del_flag による論理削除を採用しているため、一覧・単体取得系のメソッドは
 * 削除済み(del_flag = true)のレコードを除外して取得します。
 * 物理削除に方針変更した場合は、これらのメソッドは不要になります。
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.delFlag IS NOT TRUE")
    List<User> findAllActive();

    @Query("SELECT u FROM User u WHERE u.id = :id AND u.delFlag IS NOT TRUE")
    Optional<User> findActiveById(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
