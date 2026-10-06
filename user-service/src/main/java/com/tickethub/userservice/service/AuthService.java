package com.tickethub.userservice.service;

import com.tickethub.userservice.dto.AuthResponse;
import com.tickethub.userservice.dto.LoginRequest;
import com.tickethub.userservice.dto.RegisterRequest;
import com.tickethub.userservice.dto.UserResponse;
import com.tickethub.userservice.entity.Role;
import com.tickethub.userservice.entity.RoleName;
import com.tickethub.userservice.entity.User;
import com.tickethub.userservice.entity.UserStatus;
import com.tickethub.userservice.exception.ConflictException;
import com.tickethub.userservice.exception.ResourceNotFoundException;
import com.tickethub.userservice.repository.RoleRepository;
import com.tickethub.userservice.repository.UserRepository;
import com.tickethub.userservice.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Registration and login. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Anyone can register; they always get the CUSTOMER role (admins are created by other admins). */
    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        String phone = (req.phone() == null || req.phone().isBlank()) ? null : req.phone().trim();
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already registered");
        }

        Role customer = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new ResourceNotFoundException("CUSTOMER role is missing"));

        User user = new User();
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(customer);

        return UserResponse.from(userRepository.save(user));
    }

    /** Checks email + password, records last login time, and returns a JWT. */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = req.email().trim().toLowerCase();

        // Throws BadCredentialsException / DisabledException / LockedException when login must fail
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.password()));

        UserDetails details = (UserDetails) auth.getPrincipal();
        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLastLoginAt(LocalDateTime.now());

        String token = jwtService.generateToken(details,user.getId());
        return new AuthResponse(token, "Bearer", jwtService.getExpirationMs(), UserResponse.from(user));
    }
}
