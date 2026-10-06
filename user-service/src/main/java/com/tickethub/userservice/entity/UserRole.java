package com.tickethub.userservice.entity;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Mapping entity for the User <-> Role many-to-many relationship.
 * Primary key is the pair (userId, roleId).
 */
@Entity
@Table(name = "user_roles")
public class UserRole extends Auditable {

    /** Composite primary key (userId + roleId). */
    @Embeddable
    public static class UserRoleId implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "role_id")
        private Long roleId;

        public UserRoleId() {
        }

        public UserRoleId(Long userId, Long roleId) {
            this.userId = userId;
            this.roleId = roleId;
        }

        public Long getUserId() { return userId; }
        public Long getRoleId() { return roleId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserRoleId that)) return false;
            return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, roleId);
        }
    }

    @EmbeddedId
    private UserRoleId id = new UserRoleId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @MapsId("roleId")
    @JoinColumn(name = "role_id")
    private Role role;

    public UserRole() {
    }

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
    }

    public UserRoleId getId() { return id; }
    public User getUser() { return user; }
    public Role getRole() { return role; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole that)) return false;
        return role != null && that.role != null && user != null && that.user != null
                && Objects.equals(role.getName(), that.role.getName())
                && Objects.equals(user.getEmail(), that.user.getEmail());
    }

    @Override
    public int hashCode() {
        return Objects.hash(role == null ? null : role.getName(), user == null ? null : user.getEmail());
    }
}
