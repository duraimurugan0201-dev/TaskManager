package com.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanager.domain.Role;
import com.taskmanager.domain.User;
import com.taskmanager.repository.UserRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskManagerApiIntegrationTest {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@12345";
    private static final String AUTHORIZATION = "Authorization";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getCollection("users").deleteMany(new Document());
        mongoTemplate.getCollection("tasks").deleteMany(new Document());

        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setName("Test Administrator");
        admin.setPhoneNumber("9000000000");
        admin.setRoles(EnumSet.of(Role.ADMIN));
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    // ------------------------------------------------------------------ auth

    @Test
    void loginReturnsJwtAndProfileWithoutPassword() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.data.user.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn();

        String token = read(result).path("data").path("accessToken").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(ADMIN_USERNAME, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void requestWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void requestWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/tasks").header(AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserEndpointReturnsOwnProfile() throws Exception {
        String token = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME));
    }

    // ------------------------------------------------------------ user admin

    @Test
    void adminCanCreateListAndFetchUsers() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        JsonNode created = createUser(adminToken, "john.doe", "Password@123");
        String userId = created.path("id").asText();
        assertThat(userId).isNotBlank();
        assertThat(created.path("roles").get(0).asText()).isEqualTo("USER");
        assertThat(created.has("password")).isFalse();

        mockMvc.perform(get("/api/admin/users").header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(get("/api/admin/users").param("search", "john")
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].username").value("john.doe"));

        mockMvc.perform(get("/api/admin/users/{id}", userId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test User"))
                .andExpect(jsonPath("$.data.phoneNumber").value("9876543210"));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        createUser(adminToken, "john.doe", "Password@123");

        mockMvc.perform(post("/api/admin/users")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "john.doe",
                                "password", "Password@456",
                                "name", "Someone Else",
                                "phoneNumber", "9876543211"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void createUserValidationErrorsAreReturnedPerField() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);

        mockMvc.perform(post("/api/admin/users")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ab",
                                "password", "short",
                                "name", "",
                                "phoneNumber", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists());
    }

    @Test
    void regularUserCannotUseAdminEndpoints() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        createUser(adminToken, "jane.doe", "Password@123");
        String userToken = login("jane.doe", "Password@123");

        mockMvc.perform(post("/api/admin/users")
                        .header(AUTHORIZATION, bearer(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "hacker",
                                "password", "Password@123",
                                "name", "Hacker",
                                "phoneNumber", "9000000001"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(get("/api/admin/users").header(AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disabledUserCannotLogin() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String userId = createUser(adminToken, "john.doe", "Password@123").path("id").asText();

        mockMvc.perform(put("/api/admin/users/{id}", userId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("john.doe", "Password@123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Account is disabled or locked"));
    }

    @Test
    void adminCannotDeleteThemselves() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String adminId = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow().getId();

        mockMvc.perform(delete("/api/admin/users/{id}", adminId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingUserCascadesTasks() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String userId = createUser(adminToken, "john.doe", "Password@123").path("id").asText();
        String userToken = login("john.doe", "Password@123");
        createTask(userToken, "Write report", "2030-01-15", "HIGH");

        mockMvc.perform(delete("/api/admin/users/{id}", userId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());

        assertThat(mongoTemplate.getCollection("tasks").countDocuments(
                new Document("userId", userId))).isZero();
    }

    // ----------------------------------------------------------------- tasks

    @Test
    void userTaskLifecycleWorksEndToEnd() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String userId = createUser(adminToken, "john.doe", "Password@123").path("id").asText();
        String token = login("john.doe", "Password@123");

        MvcResult created = createTask(token, "Morning workout", "2030-01-15", "HIGH");
        String taskId = read(created).path("data").path("id").asText();

        mockMvc.perform(get("/api/tasks").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Morning workout"))
                .andExpect(jsonPath("$.data.content[0].userId").value(userId))
                .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data.content[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.data.content[0].dueDate").value("2030-01-15"));

        mockMvc.perform(get("/api/tasks")
                        .param("date", "2030-01-15")
                        .param("status", "PENDING")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/tasks")
                        .param("from", "2030-01-01")
                        .param("to", "2030-01-31")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/tasks")
                        .param("date", "2030-01-16")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/tasks/{id}", taskId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Morning workout"));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());

        mockMvc.perform(put("/api/tasks/{id}", taskId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Evening workout",
                                "priority", "LOW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Evening workout"))
                .andExpect(jsonPath("$.data.priority").value("LOW"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").exists());

        mockMvc.perform(delete("/api/tasks/{id}", taskId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{id}", taskId).header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void taskDefaultsToTodayAndMediumPriority() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        createUser(adminToken, "john.doe", "Password@123");
        String token = login("john.doe", "Password@123");

        mockMvc.perform(post("/api/tasks")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Drink water\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.dueDate").value(LocalDate.now().toString()))
                .andExpect(jsonPath("$.data.priority").value("MEDIUM"));

        mockMvc.perform(get("/api/tasks/today").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("Drink water"));
    }

    @Test
    void usersCannotSeeOrModifyEachOthersTasks() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        createUser(adminToken, "john.doe", "Password@123");
        createUser(adminToken, "jane.doe", "Password@123");

        String johnToken = login("john.doe", "Password@123");
        String janeToken = login("jane.doe", "Password@123");

        String taskId = read(createTask(johnToken, "Private task", "2030-02-01", "MEDIUM"))
                .path("data").path("id").asText();

        mockMvc.perform(get("/api/tasks/{id}", taskId).header(AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tasks/{id}", taskId).header(AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks").header(AUTHORIZATION, bearer(janeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void adminCanFetchTasksOfAnyUser() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        String userId = createUser(adminToken, "john.doe", "Password@123").path("id").asText();
        String userToken = login("john.doe", "Password@123");
        createTask(userToken, "Prepare invoice", "2030-03-10", "LOW");

        mockMvc.perform(get("/api/admin/users/{id}/tasks", userId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].title").value("Prepare invoice"));

        mockMvc.perform(get("/api/admin/users/{id}/tasks", "000000000000000000000000")
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidTaskFiltersAndPayloadsReturn400() throws Exception {
        String adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        createUser(adminToken, "john.doe", "Password@123");
        String token = login("john.doe", "Password@123");

        mockMvc.perform(post("/api/tasks")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());

        mockMvc.perform(get("/api/tasks")
                        .param("from", "2030-02-01")
                        .param("to", "2030-01-01")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/tasks")
                        .param("status", "NOT_A_STATUS")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------------------- helpers

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).path("data").path("accessToken").asText();
    }

    private JsonNode createUser(String adminToken, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/users")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password,
                                "name", "Test User",
                                "phoneNumber", "9876543210",
                                "email", username + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).path("data");
    }

    private MvcResult createTask(String token, String title, String dueDate, String priority) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "description", "Created by integration test",
                                "dueDate", dueDate,
                                "priority", priority))))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String loginJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
