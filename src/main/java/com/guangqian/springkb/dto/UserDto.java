package com.guangqian.springkb.dto;

import com.guangqian.springkb.entity.User;

/**
 * 对外暴露的用户信息（不含密码哈希）
 */
public record UserDto(long id, String username, String role) {

    public static UserDto from(User user) {
        return new UserDto(user.id(), user.username(), user.role());
    }
}
