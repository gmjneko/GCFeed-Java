package io.github.gmjneko.gcfeed.modules.user.mapper;

import io.github.gmjneko.gcfeed.modules.user.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 持久化账号，创建初始统计行，并按用户主键读取主页统计。 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Insert("INSERT INTO user_relation_stat (user_id, following_count, follower_count) VALUES (#{userId}, 0, 0)")
    int insertRelationStat(@Param("userId") Long userId);

    // 关注数读取关系模块维护的统计表，作品数只统计未逻辑删除的上线作品。
    @Select("""
            SELECT a.id, a.account, a.nickname, a.avatar_url, a.bio, a.status, a.role,
                   GREATEST(COALESCE(s.following_count, 0), 0) AS following_count,
                   GREATEST(COALESCE(s.follower_count, 0), 0) AS follower_count,
                   (SELECT COUNT(*) FROM video v
                    WHERE v.author_id = a.id AND v.status = 2 AND v.deleted = 0) AS work_count
            FROM account a
            LEFT JOIN user_relation_stat s ON s.user_id = a.id AND s.deleted = 0
            WHERE a.id = #{userId} AND a.deleted = 0
            """)
    User selectProfile(@Param("userId") Long userId);

}
