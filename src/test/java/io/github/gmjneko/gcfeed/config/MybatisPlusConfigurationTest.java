package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 通过真实 Mapper 验证分页、防误更新和实体时间填充确实参与 SQL 执行。 */
@SpringBootTest
@Transactional
class MybatisPlusConfigurationTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void paginationRestrictsRowsAndReturnsTotal() {
        for (int i = 1; i <= 3; i++) {
            userMapper.insert(user("page-user-" + i));
        }
        Page<User> page = userMapper.selectPage(new Page<>(2, 1), Wrappers.<User>lambdaQuery()
                .likeRight(User::getAccount, "page-user-").orderByAsc(User::getId));
        assertThat(page.getTotal()).isEqualTo(3);
        assertThat(page.getRecords()).extracting(User::getAccount).containsExactly("page-user-2");
    }

    @Test
    void updateWithoutBusinessConditionIsBlocked() {
        User user = user("protected-user");
        userMapper.insert(user);
        assertThatThrownBy(() -> userMapper.update(User.builder().nickname("overwritten").build(),
                Wrappers.<User>emptyWrapper()))
                .hasRootCauseInstanceOf(MybatisPlusException.class);
        assertThat(userMapper.selectById(user.getId()).getNickname()).isEqualTo("original");
    }

    @Test
    void insertAndPartialUpdateFillTimestampsWithoutOverwritingCreationTime() {
        LocalDateTime originalTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        User user = user("timestamp-user");
        user.setCreatedAt(originalTime);
        user.setUpdatedAt(originalTime);
        userMapper.insert(user);
        User patch = User.builder().id(user.getId()).bio("changed").build();
        userMapper.updateById(patch);
        assertThat(patch.getUpdatedAt()).isAfter(originalTime);
        assertThat(patch.getCreatedAt()).isNull();
        User stored = userMapper.selectById(user.getId());
        assertThat(stored.getCreatedAt()).isEqualTo(originalTime);
        assertThat(stored.getUpdatedAt()).isAfter(originalTime);

        User fresh = user("new-timestamp-user");
        userMapper.insert(fresh);
        assertThat(fresh.getCreatedAt()).isNotNull();
        assertThat(fresh.getUpdatedAt()).isEqualTo(fresh.getCreatedAt());
    }

    private User user(String account) {
        return User.builder().account(account).password("test-hash").nickname("original")
                .status(1).role("user").build();
    }

}
