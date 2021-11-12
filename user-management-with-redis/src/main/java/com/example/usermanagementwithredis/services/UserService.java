package com.example.usermanagementwithredis.services;

import com.example.usermanagementwithredis.dtos.*;
import com.example.usermanagementwithredis.entities.Role;
import com.example.usermanagementwithredis.entities.User;
import com.example.usermanagementwithredis.repositories.RoleRepository;
import com.example.usermanagementwithredis.repositories.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class UserService implements UserDetailsService {

    private final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationManager authenticationManager;
    private final ModelMapper mapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${security.token-key}")
    private String tokenKey;
    @Value("${security.token-type}")
    private String tokenType;
    @Value("${security.separator}")
    private String separator;
    @Value("${security.validity}")
    private Long validity;
    @Value("${security.authorities-key}")
    private String authoritiesKey;

    @Autowired
    public UserService(UserRepository userRepository, RoleRepository roleRepository, @Lazy AuthenticationManager authenticationManager, ModelMapper mapper, @Lazy BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authenticationManager = authenticationManager;
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }

    public LoginResponse login(LoginRequest loginRequest) {
        LoginResponse response = null;
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));
            User user = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            if(user != null) {
                String rolesClaims = user.getRoles().stream().map(role -> role.getName()).collect(Collectors.joining(separator));
                String subject = String.join(separator, String.valueOf(user.getId()), user.getEmail());
                Claims claims = Jwts.claims();
                claims.put(authoritiesKey, rolesClaims);
                long currentTime = System.currentTimeMillis();
                String token = Jwts.builder()
                        .setClaims(claims)
                        .setSubject(subject)
                        .setIssuedAt(new Date(currentTime))
                        .setExpiration(new Date(currentTime + validity))
                        .signWith(SignatureAlgorithm.HS256, tokenKey)
                        .compact();
                response = new LoginResponse(user.getEmail(), tokenType, token);
            }
        } catch (Exception ex) {
            logger.error("Exception captured", ex);
        }
        return response;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username).orElse(null);
        UserDetails userDetails = null;
        if(user != null) {
            List<GrantedAuthority> authorities = user.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).collect(Collectors.toList());
            userDetails = new org.springframework.security.core.userdetails.User(user.getEmail(), user.getProtectedPassword(), true, true, true, true, authorities);
        }
        return userDetails;
    }

    public List<UserResponse> getAllUsers() {
        Iterable<User> users = userRepository.findAll();
        List<UserResponse> response = null;
        if(users.iterator().hasNext()) {
            response = StreamSupport.stream(users.spliterator(), false).map(u -> mapper.map(u, UserResponse.class)).collect(Collectors.toList());
        } else {
            response = new ArrayList<>();
        }
        return response;
    }

    public boolean isEmailAvailable(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        return user == null;
    }

    public UserResponse createUser(UserRequest userRequest) {
        User user = mapper.map(userRequest, User.class);
        user.setProtectedPassword(passwordEncoder.encode(userRequest.getPassword()));
        if(userRequest.getRoles() == null || userRequest.getRoles().isEmpty()) {
            Role guestRole = roleRepository.findByName(Role.GUEST).orElse(null);
            user.setRoles(Set.of(guestRole));
        } else {
            Set<Role> roles = userRequest.getRoles().stream().map(it -> roleRepository.findById(it.getId()).orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet());
            user.setRoles(roles);
        }
        userRepository.save(user);
        UserResponse response = mapper.map(user, UserResponse.class);
        return response;
    }

    public boolean createUserRequestHasAdminRole(UserRequest userRequest) {
        Role adminRole = roleRepository.findByName(Role.ADMINISTRATOR).orElse(null);
        return userRequest.getRoles() != null && userRequest.getRoles().stream().anyMatch(it -> Objects.equals(it.getId(), adminRole.getId()));
    }

    public boolean createUserRequestHasAdminRole(User user) {
        Role adminRole = roleRepository.findByName(Role.ADMINISTRATOR).orElse(null);
        return user.getRoles() != null && user.getRoles().stream().anyMatch(it -> Objects.equals(it.getId(), adminRole.getId()));
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id).orElse(null);
        UserResponse response = null;
        if(user != null) {
            response = mapper.map(user, UserResponse.class);
        }
        return response;
    }

    public UserResponse editUser(Long id, UserRequest userRequest) {
        User user = userRepository.findById(id).orElse(null);
        UserResponse response = null;
        if(user != null) {
            user.setFirstName(userRequest.getFirstName());
            user.setLastName(userRequest.getLastName());
            user.setEmail(userRequest.getEmail());
            user.setProtectedPassword(passwordEncoder.encode(userRequest.getPassword()));
            Set<Role> roles = null;
            if(userRequest.getRoles() != null && !userRequest.getRoles().isEmpty()) {
                roles = userRequest.getRoles().stream().map(it -> roleRepository.findById(it.getId()).orElse(null)).filter(Objects::nonNull).collect(Collectors.toSet());
            }
            if(userRequest.getRoles() == null || (roles != null && roles.isEmpty())) {
                Role guestRole = roleRepository.findByName(Role.GUEST).orElse(null);
                roles = Set.of(guestRole);
            }
            user.setRoles(roles);
            userRepository.save(user);
            response = mapper.map(user, UserResponse.class);
        }
        return response;
    }

    public boolean deleteUser(Long id) {
        boolean success = false;
        User user = userRepository.findById(id).orElse(null);
        if(user != null) {
            userRepository.delete(user);
            success = true;
        }
        return success;
    }
}
