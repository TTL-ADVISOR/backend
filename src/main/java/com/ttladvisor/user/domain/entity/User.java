package com.ttladvisor.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 소문자로 정규화한 값. */
    @Column(nullable = false, length = 255, unique = true)
    private String email;

    /** BCrypt 해시. 소셜 로그인으로만 가입한 회원은 null. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(length = 100)
    private String company;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String name, String email, String passwordHash, String company, Instant createdAt) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.company = company;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getCompany() {
        return company;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
