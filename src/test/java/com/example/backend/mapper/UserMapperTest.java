package com.example.backend.mapper;

import com.example.backend.model.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserMapperの統合テスト。
 * UserMapper.xmlに書いたSQLが実際に正しく動くかを、実際のDBに接続して検証する。
 *
 * 【実行に必要なもの】
 * - "test" プロファイル(application-test.yml)で、ローカルPC上にインストールした
 *   PostgreSQLに接続する。開発用の共有DB(192.168.20.246等)には接続しない
 *   (他の開発者や実データに影響を与えないようにするため)
 * - 事前に application-test.yml.example を参考に application-test.yml を
 *   作成し、テスト専用のDB(例: tesla_test)を指しておくこと
 *
 * 【@Transactionalによる後始末】
 * このクラスにつけた@Transactionalにより、各テストメソッドの終了時に
 * 自動的にロールバックされる。テストで作成したデータがDBに残り続けることはない。
 *
 * 【Docker/Testcontainersを使わない理由】
 * 開発環境でDocker Desktopが使えない制約があるため、ローカルPostgreSQLに
 * 直接接続する方式にしている。将来Docker(WSL2上のDocker Engine等)が
 * 使える環境が整ったら、Testcontainersへの切り替えを検討すること。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void insertしたユーザーをfindActiveByIdで取得できる() {
        User user = new User();
        user.setName("テスト太郎");
        user.setEmail("mapper-test-" + System.nanoTime() + "@example.com");
        user.setFirstname("Taro");
        user.setFamilyname("Test");

        userMapper.insert(user);

        assertThat(user.getId()).isNotNull(); // useGeneratedKeysでIDが採番されていること

        Optional<User> found = userMapper.findActiveById(user.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(user.getEmail());
        assertThat(found.get().getCreatedAt()).isNotNull(); // DB側のDEFAULT now()が効いていること
    }

    @Test
    void softDeleteしたユーザーはfindAllActiveに出てこない() {
        User user = new User();
        user.setEmail("softdelete-test-" + System.nanoTime() + "@example.com");
        userMapper.insert(user);

        userMapper.softDelete(user.getId());

        Optional<User> found = userMapper.findActiveById(user.getId());
        assertThat(found).isEmpty(); // 論理削除後は取得できない

        List<User> all = userMapper.findAllActive();
        assertThat(all).noneMatch(u -> u.getId().equals(user.getId()));
    }

    @Test
    void existsByEmail_登録済みのemailはtrueを返す() {
        String email = "exists-test-" + System.nanoTime() + "@example.com";
        User user = new User();
        user.setEmail(email);
        userMapper.insert(user);

        assertThat(userMapper.existsByEmail(email)).isTrue();
        assertThat(userMapper.existsByEmail("not-registered-" + System.nanoTime() + "@example.com")).isFalse();
    }

    @Test
    void update_updated_atが更新される() {
        User user = new User();
        user.setEmail("update-test-" + System.nanoTime() + "@example.com");
        user.setName("更新前");
        userMapper.insert(user);

        User original = userMapper.findActiveById(user.getId()).orElseThrow();

        user.setName("更新後");
        userMapper.update(user);

        User updated = userMapper.findActiveById(user.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("更新後");
        // updated_atはSQL側でnow()を使っているため、更新前より後の時刻になっているはず
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(original.getUpdatedAt());
    }
}
