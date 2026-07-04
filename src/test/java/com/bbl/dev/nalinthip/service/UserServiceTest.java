package com.bbl.dev.nalinthip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.bbl.dev.nalinthip.dto.request.UserRequest;
import com.bbl.dev.nalinthip.dto.response.UserResponse;
import com.bbl.dev.nalinthip.mapper.UserMapper;
import com.bbl.dev.nalinthip.mapper.UserMapperImpl;

/**
 * Unit test for {@link UserService} using the real MapStruct mapper and a
 * {@link MockRestServiceServer} to stub the external user API.
 */
class UserServiceTest {

    private static final String BASE_URL = "https://api.example.com";

    private final UserMapper userMapper = new UserMapperImpl();

    private UserService userService;
    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        userService = new UserService(BASE_URL, userMapper);

        // Swap the internally-created RestClient for one bound to a mock server
        // so outbound HTTP calls are stubbed without hitting the network.
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        mockServer = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(userService, "restClient", builder.build());
    }

    /** Stub GET /users (called once, then cached by the service). */
    private void expectRemoteUsers(String json) {
        mockServer.expect(requestTo(BASE_URL + "/users"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    private UserRequest sampleRequest() {
        UserRequest request = new UserRequest();
        request.setName("Nalinthip");
        request.setUsername("nalin");
        request.setEmail("nalin@example.com");
        request.setPhone("081-234-5678");
        request.setWebsite("nalin.dev");
        return request;
    }

    // ---------- getUsers ----------

    @Test
    void getUsers_returnsRemoteUsers() {
        expectRemoteUsers("""
                [
                  {"id":1,"name":"Leanne","username":"bret","email":"leanne@example.com"},
                  {"id":2,"name":"Ervin","username":"antonette","email":"ervin@example.com"}
                ]
                """);

        List<UserResponse> users = userService.getUsers();

        assertThat(users).extracting(UserResponse::getName)
                .containsExactlyInAnyOrder("Leanne", "Ervin");
        mockServer.verify();
    }

    @Test
    void getUsers_fetchesRemoteOnlyOnceThenServesFromCache() {
        // Only one GET /users is stubbed; a second remote call would fail.
        expectRemoteUsers("""
                [{"id":1,"name":"Leanne","username":"bret","email":"leanne@example.com"}]
                """);

        userService.getUsers();
        List<UserResponse> secondCall = userService.getUsers();

        assertThat(secondCall).hasSize(1);
        mockServer.verify();
    }

    @Test
    void getUsers_includesLocallyCreatedUsersWithoutDuplicateIds() {
        expectRemoteUsers("""
                [{"id":1,"name":"Leanne","username":"bret","email":"leanne@example.com"}]
                """);

        userService.createUser(sampleRequest()); // gets id 100

        List<UserResponse> users = userService.getUsers();

        assertThat(users).extracting(UserResponse::getId)
                .containsExactlyInAnyOrder(1L, 100L)
                .doesNotHaveDuplicates();
        mockServer.verify();
    }

    // ---------- createUser: id generation ----------

    @Test
    void createUser_firstIdStartsAt100() {
        expectRemoteUsers("[]");

        UserResponse created = userService.createUser(sampleRequest());

        assertThat(created.getId()).isEqualTo(100L);
        assertThat(created.getName()).isEqualTo("Nalinthip");
    }

    @Test
    void createUser_generatesSequentialIdsFrom100() {
        expectRemoteUsers("[]");

        Long first = userService.createUser(sampleRequest()).getId();
        Long second = userService.createUser(sampleRequest()).getId();

        assertThat(first).isEqualTo(100L);
        assertThat(second).isEqualTo(101L);
    }

    @Test
    void createUser_idStaysClearOfRemoteIds() {
        // Even if a remote id is unusually high, the new id sits above it.
        expectRemoteUsers("""
                [{"id":250,"name":"Big","username":"biggy","email":"big@example.com"}]
                """);

        UserResponse created = userService.createUser(sampleRequest());

        assertThat(created.getId()).isEqualTo(251L);
    }

    @Test
    void createUser_doesNotReuseIdAfterDeletion() {
        expectRemoteUsers("[]");

        Long first = userService.createUser(sampleRequest()).getId();   // 100
        Long second = userService.createUser(sampleRequest()).getId();  // 101
        userService.deleteUser(String.valueOf(second));                 // remove 101

        Long third = userService.createUser(sampleRequest()).getId();

        assertThat(third).isEqualTo(102L);          // does not fall back to 101
        assertThat(third).isNotIn(first, second);   // never reuses a past id
    }

    // ---------- getUserById ----------

    @Test
    void getUserById_whenNotCached_fetchesRemoteThenCachesIt() {
        // GET /users/5 is stubbed exactly once; the second read must be cached.
        mockServer.expect(requestTo(BASE_URL + "/users/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":5,"name":"Chelsey","username":"kamren","email":"chelsey@example.com"}
                        """, MediaType.APPLICATION_JSON));

        UserResponse first = userService.getUserById("5");
        UserResponse second = userService.getUserById("5"); // served from cache

        assertThat(first.getName()).isEqualTo("Chelsey");
        assertThat(second.getName()).isEqualTo("Chelsey");
        mockServer.verify();
    }

    @Test
    void getUserById_whenLocallyCreated_returnsWithoutRemoteCall() {
        expectRemoteUsers("[]");
        userService.createUser(sampleRequest()); // id 100

        // No stub for GET /users/100, so any remote call would fail the test.
        UserResponse user = userService.getUserById("100");

        assertThat(user.getName()).isEqualTo("Nalinthip");
        mockServer.verify();
    }

    @Test
    void getUserById_withNonNumericId_fetchesFromRemote() {
        mockServer.expect(requestTo(BASE_URL + "/users/abc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":99,"name":"Remote","username":"remote","email":"remote@example.com"}
                        """, MediaType.APPLICATION_JSON));

        UserResponse user = userService.getUserById("abc");

        assertThat(user.getName()).isEqualTo("Remote");
        mockServer.verify();
    }

    // ---------- updateUser ----------

    @Test
    void updateUser_whenExists_appliesChanges() {
        expectRemoteUsers("[]");
        userService.createUser(sampleRequest()); // id 100

        UserRequest update = sampleRequest();
        update.setName("Updated Name");
        update.setEmail("updated@example.com");

        UserResponse result = userService.updateUser("100", update);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L); // id preserved
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getEmail()).isEqualTo("updated@example.com");
    }

    @Test
    void updateUser_whenMissing_returnsNull() {
        UserResponse result = userService.updateUser("999", sampleRequest());

        assertThat(result).isNull();
    }

    // ---------- deleteUser ----------

    @Test
    void deleteUser_whenExists_removesAndReturnsTrue() {
        expectRemoteUsers("[]");
        userService.createUser(sampleRequest()); // id 100

        assertThat(userService.deleteUser("100")).isTrue();
        assertThat(userService.deleteUser("100")).isFalse(); // already gone
    }

    @Test
    void deleteUser_whenMissing_returnsFalse() {
        assertThat(userService.deleteUser("42")).isFalse();
    }
}
