package io.github.gmjneko.gcfeed.modules.user.controller;

import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.interceptor.JwtInterceptor;
import io.github.gmjneko.gcfeed.common.jwt.JwtPayload;
import io.github.gmjneko.gcfeed.modules.user.dto.LoginDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.RegisterDTO;
import io.github.gmjneko.gcfeed.modules.user.dto.UpdateProfileDTO;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import io.github.gmjneko.gcfeed.modules.user.vo.LoginVO;
import io.github.gmjneko.gcfeed.modules.user.vo.PublicUserVO;
import io.github.gmjneko.gcfeed.modules.user.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 暴露六个账号接口，处理请求校验、当前用户身份和统一响应包装。 */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return ApiResponse.success(userService.register(dto));
    }

    @PostMapping("/sessions")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return ApiResponse.success(userService.login(dto));
    }

    @DeleteMapping("/sessions/current")
    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(JwtInterceptor.PAYLOAD_ATTRIBUTE) JwtPayload payload) {
        userService.logout(payload);
    }

    @GetMapping("/users/me")
    @RequireAuth
    public ApiResponse<UserVO> me() {
        return ApiResponse.success(userService.getProfile(UserContext.requireUserId()));
    }

    @PatchMapping("/users/me")
    @RequireAuth
    public ApiResponse<UserVO> updateMe(@Valid @RequestBody UpdateProfileDTO dto) {
        return ApiResponse.success(userService.updateProfile(UserContext.requireUserId(), dto));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<PublicUserVO> get(@PathVariable Long userId) {
        return ApiResponse.success(userService.getPublicProfile(userId));
    }

}
