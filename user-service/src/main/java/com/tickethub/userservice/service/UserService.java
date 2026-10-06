package com.tickethub.userservice.service;

import com.tickethub.userservice.dto.ChangePasswordRequest;
import com.tickethub.userservice.dto.UpdateProfileRequest;
import com.tickethub.userservice.dto.UserResponse;
import com.tickethub.userservice.entity.Role;
import com.tickethub.userservice.entity.RoleName;
import com.tickethub.userservice.entity.User;
import com.tickethub.userservice.entity.UserStatus;
import com.tickethub.userservice.exception.BadRequestException;
import com.tickethub.userservice.exception.ConflictException;
import com.tickethub.userservice.exception.ResourceNotFoundException;
import com.tickethub.userservice.repository.RoleRepository;
import com.tickethub.userservice.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Profile operations for the logged-in user and management operations for admins. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- For the logged-in user ----------

    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return UserResponse.from(findByEmail(email));
    }

    @Transactional
    public UserResponse updateMe(String email, UpdateProfileRequest req) {
        User user = findByEmail(email);
        String phone = (req.phone() == null || req.phone().isBlank()) ? null : req.phone().trim();
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new ConflictException("Phone number is already registered");
        }
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setPhone(phone);
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest req) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    // ---------- For admins ----------

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UserStatus status) {
        return userRepository.findAllWithRoles().stream()
                .filter(u -> status == null || u.getStatus() == status)
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return UserResponse.from(findById(id));
    }

    @Transactional
    public UserResponse updateStatus(Long id, UserStatus status, String actingEmail) {
        User user = findById(id);
        if (user.getEmail().equalsIgnoreCase(actingEmail) && status != UserStatus.ACTIVE) {
            throw new BadRequestException("You cannot deactivate or block your own account");
        }
        user.setStatus(status);
        return UserResponse.from(user);
    }

    /** Replaces the user's roles with exactly the given set. */
    @Transactional
    public UserResponse assignRoles(Long id, Set<RoleName> roleNames, String actingEmail) {
        User user = findById(id);
        if (user.getEmail().equalsIgnoreCase(actingEmail) && !roleNames.contains(RoleName.ADMIN)) {
            throw new BadRequestException("You cannot remove your own ADMIN role");
        }
        user.clearRoles();
        userRepository.flush(); // delete old mapping rows first so re-adding the same role does not clash
        for (RoleName name : roleNames) {
            Role role = roleRepository.findByName(name)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name));
            user.addRole(role);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse deleteUser(Long id, String actingEmail) {
        User user = findById(id);
        if (user.getEmail().equalsIgnoreCase(actingEmail)) {
            throw new BadRequestException("You cannot delete your own account");
        }
        user.setStatus(UserStatus.INACTIVE);
        return UserResponse.from(user);
    }

    // ---------- helpers ----------

    private User findByEmail(String email) {
        return userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private User findById(Long id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));
    }
}
