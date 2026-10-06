package com.tickethub.userservice.config;

import com.tickethub.userservice.entity.Role;
import com.tickethub.userservice.entity.RoleName;
import com.tickethub.userservice.entity.User;
import com.tickethub.userservice.entity.UserStatus;
import com.tickethub.userservice.repository.RoleRepository;
import com.tickethub.userservice.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on startup:
 *  1. makes sure the CUSTOMER and ADMIN roles exist
 *  2. creates the first admin account if there is no admin yet
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email}") String adminEmail,
                           @Value("${app.admin.password}") String adminPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Role customer = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.CUSTOMER, "Regular user who books tickets")));
        Role admin = roleRepository.findByName(RoleName.ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN, "Administrator who manages users and roles")));

        if (!userRepository.existsAnyUserWithRole(RoleName.ADMIN) && !userRepository.existsByEmail(adminEmail)) {
            User user = new User();
            user.setFirstName("System");
            user.setLastName("Admin");
            user.setEmail(adminEmail);
            user.setPasswordHash(passwordEncoder.encode(adminPassword));
            user.setStatus(UserStatus.ACTIVE);
            user.addRole(admin);
            user.addRole(customer);
            userRepository.save(user);
            log.info("Created default admin account: {}", adminEmail);
        }
    }
}
