package com.lms.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

/**
 * Represents a user account stored in MongoDB (collection: "users").
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String username;
    private String passwordHash;
    private Role   role;
    @Indexed
    private String createdAt;

    // ── Constructors ──────────────────────────────────────────────────────

    public User() {
        this.createdAt = Instant.now().toString();
    }

    public User(String username, String passwordHash, Role role) {
        this();
        this.username     = username;
        this.passwordHash = passwordHash;
        this.role         = role;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────

    public String getId()            { return id; }
    public void   setId(String id)   { this.id = id; }

    public String getUsername()                  { return username; }
    public void   setUsername(String username)   { this.username = username; }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash()                      { return passwordHash; }
    public void   setPasswordHash(String passwordHash)   { this.passwordHash = passwordHash; }

    public Role getRole()             { return role; }
    public void setRole(Role role)    { this.role = role; }

    public String getCreatedAt()                 { return createdAt; }
    public void   setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
