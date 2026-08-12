package com.example.backend.service.impl;

import com.example.backend.exception.DuplicateEmailException;
import com.example.backend.exception.ResourceNotFoundException;
import com.example.backend.mapper.UserMapper;
import com.example.backend.model.dto.CsvImportResult;
import com.example.backend.model.dto.UserRequest;
import com.example.backend.model.dto.UserResponse;
import com.example.backend.model.entity.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UserServiceImplの単体テスト。
 * UserMapper(DBアクセス)はモック化しているため、実際のDB接続は不要。
 * ビジネスロジック(email重複チェック、論理削除、CSV検証)だけを検証する。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    // BeanValidationの実際の実装を使う(@Emailや@Size等の検証ロジックそのものは
    // モック化する対象ではなく、実際に動かして確認したいため)
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, validator);
    }

    @Test
    void create_正常系_ユーザーを登録できる() {
        UserRequest request = new UserRequest();
        request.setName("山田太郎");
        request.setEmail("taro@example.com");
        request.setFirstname("Taro");
        request.setFamilyname("Yamada");

        when(userMapper.existsByEmail("taro@example.com")).thenReturn(false);
        // insertが呼ばれた際、MyBatisのuseGeneratedKeysを模してidをセットする
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return null;
        }).when(userMapper).insert(any(User.class));

        UserResponse response = userService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("taro@example.com");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void create_email重複時にDuplicateEmailExceptionを投げる() {
        UserRequest request = new UserRequest();
        request.setEmail("duplicate@example.com");

        when(userMapper.existsByEmail("duplicate@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(DuplicateEmailException.class);

        // 重複エラーの場合、insertは呼ばれてはいけない
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void findById_存在しないIDの場合ResourceNotFoundExceptionを投げる() {
        when(userMapper.findActiveById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_論理削除であり物理削除しない() {
        User existing = new User();
        existing.setId(1L);
        when(userMapper.findActiveById(1L)).thenReturn(Optional.of(existing));

        userService.delete(1L);

        // 物理削除メソッドは存在しない。softDeleteが呼ばれることだけを確認する
        verify(userMapper).softDelete(1L);
        verify(userMapper, never()).insert(any());
    }

    @Test
    void importFromCsv_不正な行はスキップして正常な行だけ登録される() throws IOException {
        String csv = """
                name,email,firstname,familyname
                山田太郎,taro@example.com,Taro,Yamada
                不正データ,not-an-email,Test,Error
                鈴木花子,hanako@example.com,Hanako,Suzuki
                """;
        MockMultipartFile file = new MockMultipartFile(
                "file", "users.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        when(userMapper.existsByEmail(any())).thenReturn(false);

        CsvImportResult result = userService.importFromCsv(file);

        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getLineNumber()).isEqualTo(3); // ヘッダー行を1行目として数える

        // 正常な2行分だけinsertが呼ばれる
        verify(userMapper, times(2)).insert(any(User.class));
    }

    @Test
    void findAll_論理削除済みを除いた一覧が返る() {
        User user = new User();
        user.setId(1L);
        user.setEmail("active@example.com");
        when(userMapper.findAllActive()).thenReturn(List.of(user));

        List<UserResponse> result = userService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("active@example.com");
        // findAllActive() を呼んでいる(del_flag=trueを除外するSQL)ことを確認
        verify(userMapper).findAllActive();
    }
}
