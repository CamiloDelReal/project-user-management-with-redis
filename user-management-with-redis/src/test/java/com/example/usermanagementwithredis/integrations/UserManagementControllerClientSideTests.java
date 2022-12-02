package com.example.usermanagementwithredis.integrations;

import com.example.usermanagementwithredis.dtos.*;
import com.example.usermanagementwithredis.entities.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testng.annotations.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public class UserManagementControllerClientSideTests extends AbstractTestNGSpringContextTests {

    @Value("${security.token-type}")
    private String TOKEN_TYPE;

    @Container
    public static final GenericContainer<?> redisContainer;

    @Autowired
    private TestRestTemplate restTemplate;

    private String adminToken;
    private UserResponse userCreatedWithDefaultRole;
    private String userCreatedWithDefaultRolePassword;

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
    public void loginRoot_success() {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "root");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        LoginResponse loginResponse = response.getBody();

        assertNotNull(loginResponse.getEmail());
        assertEquals(loginRequest.getEmail(), loginResponse.getEmail());
        assertNotNull(loginResponse.getTokenType());
        assertEquals(this.TOKEN_TYPE, loginResponse.getTokenType());
        assertNotNull(loginResponse.getToken());

        this.adminToken = loginResponse.getToken();
    }

    @Test
    public void loginRoot_failByInvalidPassword() {
        LoginRequest loginRequest = new LoginRequest("root@gmail.com", "invalid");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void login_failByInvalidCredentials() {
        LoginRequest loginRequest = new LoginRequest("invalid@gmail.com", "12345");

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<LoginRequest> request = new HttpEntity<>(loginRequest, headers);

        ResponseEntity<LoginResponse> response = restTemplate.exchange("/users/login", HttpMethod.POST, request, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    public void createUserWithDefaultRole_success() {
        String testPassword = "qwerty";
        UserRequest userRequest = new UserRequest("John", "Doe", "johndoe@gmail.com", testPassword, null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserResponse userResponse = response.getBody();

        assertNotNull(userResponse.getId());
        assertNotEquals("", userResponse.getId());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(Role.GUEST)));

        this.userCreatedWithDefaultRole = userResponse;
        System.out.println("AppLogger - checking[createUserWithDefaultRole_success()] userCreatedWithDefaultRole = " + userCreatedWithDefaultRole);
        this.userCreatedWithDefaultRolePassword = testPassword;
    }

    @Test
    public void createUserWithAdminRole_failByNoAdminCredentials() {
        RoleResponse[] roles = restTemplate.getForObject("/users/roles", RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        UserRequest userRequest = new UserRequest("Jane", "Doe", "janedoe@gmail.com", "qwerty", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test(dependsOnMethods = {"loginRoot_success"})
    public void createUserWithAdminRole_success() {
        assertNotNull(this.adminToken);

        RoleResponse[] roles = restTemplate.getForObject("/users/roles", RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        UserRequest userRequest = new UserRequest("Kath", "Doe", "kathdoe@gmail.com", "qwerty", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken));
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        UserResponse userResponse = response.getBody();

        assertNotNull(userResponse.getId());
        assertNotEquals(0, userResponse.getId());
        assertEquals(userRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userRequest.getLastName(), userResponse.getLastName());
        assertEquals(userRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(Role.ADMINISTRATOR)));
    }

    @Test
    public void createUser_failByEmailDuplicity() {
        UserRequest userRequest = new UserRequest("Root2 First Name", "Root2 Last Name", "root@gmail.com", "qwerty", null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        HttpEntity<UserRequest> request = new HttpEntity<>(userRequest, headers);

        ResponseEntity<UserResponse> response = restTemplate.exchange("/users", HttpMethod.POST, request, UserResponse.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_success() {
        String testPassword = "12345";
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", testPassword, null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, editResponse.getStatusCode());
        assertNotNull(editResponse.getBody());

        UserResponse userResponse = editResponse.getBody();

        assertNotNull(userResponse.getId());
        assertEquals(this.userCreatedWithDefaultRole.getId(), userResponse.getId());
        assertEquals(userEditRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userEditRequest.getLastName(), userResponse.getLastName());
        assertEquals(userEditRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(this.userCreatedWithDefaultRole.getRoles().stream().findFirst().get().getName())));

        this.userCreatedWithDefaultRole = userResponse;
        System.out.println("AppLogger - checking[] userCreatedWithDefaultRole = " + userCreatedWithDefaultRole);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginCheckResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserWithUserCredentials_failByWrongId() {
        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Mia", "Doe", "miadoe@gmail.com", "asdfg", null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId() + 1), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUserToAdminWithUserCredentials_failByNoAdminCredentials() {
        RoleResponse[] roles = restTemplate.getForObject("/users/roles", RoleResponse[].class);
        assertNotEquals(0, roles.length);
        List<RoleResponse> adminFiltered = Arrays.stream(roles).filter(role -> role.getName().equals(Role.ADMINISTRATOR)).toList();
        assertNotEquals(0, adminFiltered.size());
        RoleResponse adminRole = adminFiltered.get(0);

        LoginRequest loginEditRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);
        UserRequest userEditRequest = new UserRequest("Beth", "Doe", "bethdoe@gmail.com", "poiuy", Set.of(new RoleRequest(Long.parseLong(adminRole.getId()))));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginEditRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = "createUserWithDefaultRole_success")
    public void editUser_failByNoUserCredentials() {
        UserRequest userEditRequest = new UserRequest("Anna", "Doe", "annadoe@gmail.com", "12345", null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, editResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {"loginRoot_success", "createUserWithDefaultRole_success"})
    public void editUserWithAdminCredentials_success() {
        String testPassword = "zxcvb";
        UserRequest userEditRequest = new UserRequest("Sarah", "Doe", "sarahdoe@gmail.com", testPassword, null);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, this.adminToken));

        HttpEntity<UserRequest> editRequest = new HttpEntity<>(userEditRequest, headers);

        ResponseEntity<UserResponse> editResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.PUT, editRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, editResponse.getStatusCode());
        assertNotNull(editResponse.getBody());

        UserResponse userResponse = editResponse.getBody();

        assertNotNull(userResponse.getId());
        assertNotEquals(0, userResponse.getId());
        assertEquals(userEditRequest.getFirstName(), userResponse.getFirstName());
        assertEquals(userEditRequest.getLastName(), userResponse.getLastName());
        assertEquals(userEditRequest.getEmail(), userResponse.getEmail());
        assertNotNull(userResponse.getRoles());
        assertEquals(1, userResponse.getRoles().size());
        assertTrue(userResponse.getRoles().stream().anyMatch(r -> r.getName().equals(this.userCreatedWithDefaultRole.getRoles().stream().findFirst().get().getName())));

        this.userCreatedWithDefaultRole = userResponse;
        System.out.println("AppLogger - checking[] userCreatedWithDefaultRole = " + userCreatedWithDefaultRole);
        this.userCreatedWithDefaultRolePassword = testPassword;

        LoginRequest loginCheckRequest = new LoginRequest(userEditRequest.getEmail(), userEditRequest.getPassword());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginCheckResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {"createUserWithDefaultRole_success"})
    public void deleteUser_failByNoCredentials() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<UserRequest> deleteRequest = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.DELETE, deleteRequest, UserResponse.class);

        assertEquals(HttpStatus.FORBIDDEN, deleteResponse.getStatusCode());
    }

    @Test(dependsOnMethods = {
            "createUserWithDefaultRole_success",
            "editUserToAdminWithUserCredentials_failByNoAdminCredentials",
            "editUserWithUserCredentials_failByWrongId",
            "editUserWithUserCredentials_success",
            "editUser_failByNoUserCredentials",
            "editUserWithAdminCredentials_success",
            "deleteUser_failByNoCredentials"})
    public void deleteUserWithUserCredentials_success() {
        LoginRequest loginDeleteAndCheckRequest = new LoginRequest(this.userCreatedWithDefaultRole.getEmail(), this.userCreatedWithDefaultRolePassword);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<LoginRequest> loginRequest = new HttpEntity<>(loginDeleteAndCheckRequest, headers);

        ResponseEntity<LoginResponse> loginEditResponse = restTemplate.exchange("/users/login", HttpMethod.POST, loginRequest, LoginResponse.class);

        assertEquals(HttpStatus.OK, loginEditResponse.getStatusCode());

        String token = loginEditResponse.getBody().getToken();

        headers.add(HttpHeaders.AUTHORIZATION, String.format("%s %s", this.TOKEN_TYPE, token));

        HttpEntity<UserRequest> deleteRequest = new HttpEntity<>(headers);

        ResponseEntity<UserResponse> deleteResponse = restTemplate.exchange(String.format("/users/%s", this.userCreatedWithDefaultRole.getId()), HttpMethod.DELETE, deleteRequest, UserResponse.class);

        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertNull(deleteResponse.getBody());

        headers.remove(HttpHeaders.AUTHORIZATION);

        HttpEntity<LoginRequest> checkRequest = new HttpEntity<>(loginDeleteAndCheckRequest, headers);

        ResponseEntity<LoginResponse> loginCheckResponse = restTemplate.exchange("/users/login", HttpMethod.POST, checkRequest, LoginResponse.class);

        assertEquals(HttpStatus.UNAUTHORIZED, loginCheckResponse.getStatusCode());

        this.userCreatedWithDefaultRole = null;
        this.userCreatedWithDefaultRolePassword = null;
    }

}

