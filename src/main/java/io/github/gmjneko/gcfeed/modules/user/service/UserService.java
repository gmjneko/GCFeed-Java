package io.github.gmjneko.gcfeed.modules.user.service;

import io.github.gmjneko.gcfeed.common.jwt.JwtPayload;
import io.github.gmjneko.gcfeed.modules.user.dto.LoginDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.UpdateProfileDTO;
import io.github.gmjneko.gcfeed.modules.user.vo.LoginVO;
import io.github.gmjneko.gcfeed.modules.user.vo.PublicUserVO;
import io.github.gmjneko.gcfeed.modules.user.vo.UserVO;

/** 提供账号生命周期和资料读写能力；事务、唯一性和凭证校验均由服务层负责。 */
public interface UserService {

    /** 创建账号和初始统计；账号重复抛出 ACCOUNT_ALREADY_EXISTS，参数非法抛出对应业务异常。 */
    UserVO register(RegisterDTO dto);

    /** 校验凭证并签发令牌；凭证错误统一抛出 INVALID_CREDENTIALS，异常状态拒绝登录。 */
    LoginVO login(LoginDTO dto);

    /** 吊销已经通过鉴权的令牌，黑名单保留到令牌原过期时间。 */
    void logout(JwtPayload payload);

    /** 读取本人资料；ID 非法或用户已删除、不存在时抛出对应业务异常。 */
    UserVO getProfile(Long userId);

    /** 读取公开资料，隐藏账号、角色与状态；不存在时抛出 USER_NOT_FOUND。 */
    PublicUserVO getPublicProfile(Long userId);

    /** 仅更新非 null 的展示字段；拒绝空更新和空昵称，重复赋值不产生额外业务副作用。 */
    UserVO updateProfile(Long userId, UpdateProfileDTO dto);

}
