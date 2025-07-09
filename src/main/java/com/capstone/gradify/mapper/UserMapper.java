package com.capstone.gradify.mapper;

import com.capstone.gradify.Entity.user.UserEntity;
import com.capstone.gradify.dto.response.LoginResponse;
import com.capstone.gradify.dto.response.UserResponse;
import com.microsoft.graph.requests.UserRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(source="userId", target="userId")
    UserResponse toUserResponse(UserEntity user);

    default LoginResponse toLoginResponse(UserEntity user, String token) {
        UserResponse userResponse = toUserResponse(user);
        return new LoginResponse(userResponse, token);
    }

    UserResponse toResponseDTO(UserEntity user);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastLogin", ignore = true)
    @Mapping(target = "password", ignore = true)  // Handle separately
    UserEntity toEntity(UserRequest dto);
}
