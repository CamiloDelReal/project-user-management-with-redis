package com.example.usermanagementwithredis.integrations;

import com.example.usermanagementwithredis.dtos.*;
import com.example.usermanagementwithredis.entities.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnableWebMvc
@AutoConfigureMockMvc
public class UserManagementControllerServerSideTests extends AbstractTestNGSpringContextTests {

    @Value("${security.token-type}")
    private String TOKEN_TYPE;

    @Container
    public static final GenericContainer<?> redisContainer;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private UserResponse userCreatedWithDefaultRole;
    private String userCreatedWithDefaultRolePassword;

    private String adminToken;

    static {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.0.5"))
                .withExposedPorts(6379)
                .withReuse(true);
        redisContainer.start();
    }

    @DynamicPropertySource
    public static void setDatasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redisContainer::getHost);
        registry.add("spring.redis.port", () -> redisContainer.getMappedPort(6379));
        registry.add("spring.redis.password", () -> "");
    }

    @Test
    public void loginRoot_success() throws Exception {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "root");

        MvcResult result = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.tokenType").exists())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        LoginResponse loginResponse = mapper.readValue(result.getResponse().getContentAsString(), LoginResponse.class);

        this.adminToken = loginResponse.getToken();
    }

    @Test
    public void loginRoot_failByInvalidPassword() throws Exception {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "invalid");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void login_failByInvalidCredentials() throws Exception {
        LoginRequest loginRequest = new LoginRequest("invalid@gmail.com", "12345");

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void createUserWithDefaultRole_success() throws Exception {
        String testPassword = "qwerty";
        UserRequest userRequest = new UserRequest("John", "Doe", "johndoe@gmail.com", testPassword, null);

        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").exists())
                .andExpect(jsonPath("$.lastName").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.roles").exists())
                .andReturn();

        UserResponse userResponse = mapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        assertNotNull(userResponse.getId());
        assertNotEquals(0, userResponse.getId());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(Role.GUEST)));

        this.userCreatedWithDefaultRole = userResponse;
        this.userCreatedWithDefaultRolePassword = testPassword;
    }

    @Test
    public void createUserWithAdminRole_failByNoAdminCredentials() throws Exception {
        MvcResult rolesResult = mockMvc.perform(get("/users/roles")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        RoleResponse[] roles = mapper.readValue(rolesResult.getResponse().getContentAsString(), RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        UserRequest userRequest = new UserRequest("Jane", "Doe", "janedoe@gmail.com", "qwerty", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {"loginRoot_success"})
    public void createUserWithAdminRole_success() throws Exception {
        assertNotNull(this.adminToken);

        MvcResult rolesResult = mockMvc.perform(get("/users/roles")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        RoleResponse[] roles = mapper.readValue(rolesResult.getResponse().getContentAsString(), RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        UserRequest userRequest = new UserRequest("Kath", "Doe", "kathdoe@gmail.com", "qwerty", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken))
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").exists())
                .andExpect(jsonPath("$.lastName").exists())
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.roles").exists())
                .andReturn();

        UserResponse userResponse = mapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);

        assertNotNull(userResponse.getId());
        assertNotEquals("", userResponse.getId());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(Role.ADMINISTRATOR)));
    }

    @Test
    public void createUser_failByEmailDuplicity() throws Exception {
        UserRequest userRequest = new UserRequest("Root2 First Name", "Root2 Last Name", "root@gmail.com", "qwerty", null);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userRequest)))
                .andExpect(status().isConflict());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_success() throws Exception {
        String testPassword = "12345";
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginEditResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();


        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", testPassword, null);

        String token = mapper.readValue(loginEditResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        MvcResult userEditResult = mockMvc.perform(put("/users/{id}", this.userCreatedWithDefaultRole.getId())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(this.userCreatedWithDefaultRole.getId()))
                .andExpect(jsonPath("$.firstName").value(userEditRequest.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(userEditRequest.getLastName()))
                .andExpect(jsonPath("$.email").value(userEditRequest.getEmail()))
                .andExpect(jsonPath("$.roles[0].name").value(Role.GUEST))
                .andReturn();

        this.userCreatedWithDefaultRole = mapper.readValue(userEditResult.getResponse().getContentAsString(), UserResponse.class);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginCheckRequest)))
                .andExpect(status().isOk());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_failByWrongId() throws Exception {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andReturn();

        UserRequest userEditRequest = new UserRequest("Mia", "Doe", "miadoe@gmail.com", "asdfg", null);

        String token = mapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        mockMvc.perform(put("/users/{id}", this.userCreatedWithDefaultRole.getId() + 1)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserToAdminWithUserCredentials_failByNoAdminCredentials() throws Exception {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginEditResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginEditRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = mapper.readValue(loginEditResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        MvcResult rolesResult = mockMvc.perform(get("/users/roles")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk())
                .andReturn();
        RoleResponse[] roles = mapper.readValue(rolesResult.getResponse().getContentAsString(), RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        UserRequest userEditRequest = new UserRequest("Beth", "Doe", "bethdoe@gmail.com", "poiuy", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        mockMvc.perform(put("/users/{id}", this.userCreatedWithDefaultRole.getId())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUser_failByNoUserCredentials() throws Exception {
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", "12345", null);

        mockMvc.perform(put("/users/{id}", this.userCreatedWithDefaultRole.getId())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {"loginRoot_success", "createUserWithDefaultRole_success"})
    public void editUserWithAdminCredentials_success() throws Exception {
        String testPassword = "zxcvb";
        UserRequest userEditRequest = new UserRequest("Sarah", "Doe", "sarahdoe@gmail.com", testPassword, null);

        MvcResult userEditResult = mockMvc.perform(put("/users/{id}", this.userCreatedWithDefaultRole.getId())
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken))
                        .content(mapper.writeValueAsString(userEditRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(this.userCreatedWithDefaultRole.getId()))
                .andExpect(jsonPath("$.firstName").value(userEditRequest.getFirstName()))
                .andExpect(jsonPath("$.lastName").value(userEditRequest.getLastName()))
                .andExpect(jsonPath("$.email").value(userEditRequest.getEmail()))
                .andExpect(jsonPath("$.roles[0].name").value(this.userCreatedWithDefaultRole.getRoles().stream().findFirst().get().getName()))
                .andReturn();

        this.userCreatedWithDefaultRole = mapper.readValue(userEditResult.getResponse().getContentAsString(), UserResponse.class);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsBytes(loginCheckRequest)))
                .andExpect(status().isOk());
    }

    @Test(dependsOnMethods = {"createUserWithDefaultRole_success"})
    public void deleteUser_failByNoCredentials() throws Exception {
        mockMvc.perform(delete("/users/{id}", this.userCreatedWithDefaultRole.getId()))
                .andExpect(status().isForbidden());
    }

    @Test(dependsOnMethods = {
            "createUserWithDefaultRole_success",
            "editUserToAdminWithUserCredentials_failByNoAdminCredentials",
            "editUserWithUserCredentials_failByWrongId",
            "editUserWithUserCredentials_success",
            "editUser_failByNoUserCredentials",
            "editUserWithAdminCredentials_success",
            "deleteUser_failByNoCredentials"})
    public void deleteUserWithUserCredentials_success() throws Exception {
        LoginRequest loginDeleteAndCheckRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        MvcResult loginDeleteResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginDeleteAndCheckRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = mapper.readValue(loginDeleteResult.getResponse().getContentAsString(), LoginResponse.class).getToken();

        mockMvc.perform(delete("/users/{id}", this.userCreatedWithDefaultRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON_VALUE)
                        .content(mapper.writeValueAsString(loginDeleteAndCheckRequest)))
                .andExpect(status().isUnauthorized());

        this.userCreatedWithDefaultRole = null;
        this.userCreatedWithDefaultRolePassword = null;
    }

}
