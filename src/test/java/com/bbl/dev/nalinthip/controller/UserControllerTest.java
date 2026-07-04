package com.bbl.dev.nalinthip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bbl.dev.nalinthip.dto.request.UserRequest;
import com.bbl.dev.nalinthip.dto.response.UserResponse;
import com.bbl.dev.nalinthip.service.UserService;

/**
 * Web-layer test for {@link UserController} using a standalone MockMvc setup.
 * Standalone mode wires only the controller under test plus the framework's
 * default converters/validator, so it needs no Spring context (fast) and no
 * slice-test module.
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
    }

    private UserResponse response(long id, String name) {
        UserResponse response = new UserResponse();
        response.setId(id);
        response.setName(name);
        response.setUsername("nalin");
        response.setEmail("nalin@example.com");
        return response;
    }

    /** A body that satisfies every bean-validation constraint on UserRequest. */
    private String validBody() {
        return """
                {
                  "name": "Nalinthip",
                  "username": "nalin",
                  "email": "nalin@example.com",
                  "phone": "081-234-5678",
                  "website": "nalin.dev"
                }
                """;
    }

    // ---------- GET /users ----------

    @Test
    void getUsers_returns200WithList() throws Exception {
        when(userService.getUsers()).thenReturn(List.of(response(1L, "Leanne"), response(2L, "Ervin")));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Leanne"));
    }

    // ---------- GET /users/{id} ----------

    @Test
    void getUserById_returns200WithUser() throws Exception {
        when(userService.getUserById("1")).thenReturn(response(1L, "Leanne"));

        mockMvc.perform(get("/users/{id}", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Leanne"));
    }

    // ---------- POST /users ----------

    @Test
    void createUser_withValidBody_returns201() throws Exception {
        when(userService.createUser(any(UserRequest.class))).thenReturn(response(1L, "Nalinthip"));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Nalinthip"));

        verify(userService).createUser(any(UserRequest.class));
    }

    @Test
    void createUser_withBlankName_returns400() throws Exception {
        String body = """
                {"name": "", "username": "nalin", "email": "nalin@example.com"}
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withInvalidEmail_returns400() throws Exception {
        String body = """
                {"name": "Nalinthip", "username": "nalin", "email": "not-an-email"}
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withTooShortUsername_returns400() throws Exception {
        String body = """
                {"name": "Nalinthip", "username": "ab", "email": "nalin@example.com"}
                """; // username min size is 3

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createUser_withInvalidPhoneCharacters_returns400() throws Exception {
        String body = """
                {"name": "Nalinthip", "username": "nalin", "email": "nalin@example.com", "phone": "abc!@#"}
                """;

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- PUT /users/{id} ----------

    @Test
    void updateUser_whenFound_returns200() throws Exception {
        when(userService.updateUser(eq("1"), any(UserRequest.class))).thenReturn(response(1L, "Updated"));

        mockMvc.perform(put("/users/{id}", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"));
    }

    @Test
    void updateUser_whenMissing_returns404() throws Exception {
        when(userService.updateUser(eq("999"), any(UserRequest.class))).thenReturn(null);

        mockMvc.perform(put("/users/{id}", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_withInvalidBody_returns400() throws Exception {
        String body = """
                {"name": "Nalinthip", "username": "nalin", "email": "bad"}
                """;

        mockMvc.perform(put("/users/{id}", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---------- DELETE /users/{id} ----------

    @Test
    void deleteUser_whenFound_returns204() throws Exception {
        when(userService.deleteUser("1")).thenReturn(true);

        mockMvc.perform(delete("/users/{id}", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteUser_whenMissing_returns404() throws Exception {
        when(userService.deleteUser("999")).thenReturn(false);

        mockMvc.perform(delete("/users/{id}", "999"))
                .andExpect(status().isNotFound());
    }
}
