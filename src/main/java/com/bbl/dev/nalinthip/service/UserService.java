package com.bbl.dev.nalinthip.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.bbl.dev.nalinthip.dto.model.User;
import com.bbl.dev.nalinthip.dto.request.UserRequest;
import com.bbl.dev.nalinthip.dto.response.UserResponse;
import com.bbl.dev.nalinthip.mapper.UserMapper;

import jakarta.validation.Valid;

@Service
public class UserService {

    // Locally created users get ids starting from here (keeps them clear of the
    // remote ids, which are 1..N).
    private static final long ID_START = 100;

    private final Map<Long, User> userStorage = new ConcurrentHashMap<>();

    // Highest id ever issued. Kept monotonic so a deleted id is never reused.
    // Guarded by synchronized(userStorage) in createUser.
    private long lastIssuedId = 0;

    // Remote users are pulled into userStorage once, then storage is the single
    // source of truth for both reads and id generation.
    private volatile boolean remoteLoaded = false;

    private final RestClient restClient;

    private final UserMapper userMapper;

    public UserService(@Value("${user.api.base-url}") String baseUrl, UserMapper userMapper) {
        this.restClient = RestClient.create(baseUrl);
        this.userMapper = userMapper;
    }

    public List<UserResponse> getUsers() {
        ensureRemoteLoaded();
        // Everything (remote + locally created) lives in userStorage, keyed by
        // id, so the result can never contain duplicate ids.
        return userMapper.toResponseList(new ArrayList<>(userStorage.values()));
    }

    public UserResponse getUserById(String userId) {
        User localUser = findLocalUser(userId);
        if (localUser != null) {
            return userMapper.toResponse(localUser);
        }

        UserResponse remoteUser = restClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .body(UserResponse.class);

        // Cache the fetched user so later reads and id generation see it.
        if (remoteUser != null && remoteUser.getId() != null) {
            userStorage.putIfAbsent(remoteUser.getId(), remoteUser);
        }
        return remoteUser;
    }

    public UserResponse createUser(@Valid UserRequest userRequest) {
        // Make sure remote ids are known so a new id can't collide with them.
        ensureRemoteLoaded();

        User user = userMapper.toUser(userRequest);
        synchronized (userStorage) {
            Long newUserId = nextUserId();
            user.setId(newUserId);
            userStorage.put(newUserId, user);
        }

        return userMapper.toResponse(user);
    }

    public UserResponse updateUser(String userId, @Valid UserRequest userRequest) {
        Long id = Long.parseLong(userId);
        User existing = userStorage.get(id);
        if (existing == null) {
            return null;
        }
        userMapper.updateUser(userRequest, existing);
        return userMapper.toResponse(existing);
    }

    public boolean deleteUser(String userId) {
        Long id = Long.parseLong(userId);
        return userStorage.remove(id) != null;
    }

    /** Fetch the remote users once and store them locally (keyed by id). */
    private synchronized void ensureRemoteLoaded() {
        if (remoteLoaded) {
            return;
        }
        try {
            List<UserResponse> remoteUsers = restClient.get()
                    .uri("/users")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserResponse>>() {});
            if (remoteUsers != null) {
                for (UserResponse remoteUser : remoteUsers) {
                    if (remoteUser.getId() != null) {
                        // Don't clobber a locally edited user with the same id.
                        userStorage.putIfAbsent(remoteUser.getId(), remoteUser);
                    }
                }
            }
            remoteLoaded = true;
        } catch (RestClientException e) {
            // Remote unavailable: leave remoteLoaded=false so it retries next
            // time, rather than blocking create/read entirely.
        }
    }

    /**
     * Next id for a locally created user: at least {@value #ID_START}, always
     * past the highest id ever issued and the highest id currently stored, so
     * ids never start below 100, never collide, and are never reused.
     */
    private Long nextUserId() {
        long maxInStorage = userStorage.keySet().stream()
                .max(Long::compareTo)
                .orElse(0L);
        lastIssuedId = Math.max(Math.max(lastIssuedId, maxInStorage) + 1, ID_START);
        return lastIssuedId;
    }

    private User findLocalUser(String userId) {
        try {
            return userStorage.get(Long.parseLong(userId));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
