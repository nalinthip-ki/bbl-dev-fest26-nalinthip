package com.bbl.dev.nalinthip.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.bbl.dev.nalinthip.dto.model.User;
import com.bbl.dev.nalinthip.dto.request.UserRequest;
import com.bbl.dev.nalinthip.dto.response.UserResponse;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    User toUser(UserRequest request);

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);

    void updateUser(UserRequest request, @MappingTarget User user);
}
