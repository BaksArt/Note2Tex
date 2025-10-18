package com.baksart.Note2TexBack.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "\"user\"", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"email"}),
        @UniqueConstraint(columnNames = {"username"})
})
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false) String email;
    @Column(nullable=false) String username;
    @Column(nullable=false) String passwordHash;
    @Column(nullable=false) boolean emailVerified=false;
    @Column(nullable=false) Instant createdAt = Instant.now();

    public Long getId(){return id;}
    public String getEmail(){return email;}
    public void setEmail(String email){this.email=email;}
    public String getUsername(){return username;}
    public void setUsername(String username){this.username=username;}
    public String getPasswordHash(){return passwordHash;}
    public void setPasswordHash(String passwordHash){this.passwordHash=passwordHash;}
    public boolean isEmailVerified(){return emailVerified;}
    public void setEmailVerified(boolean emailVerified){this.emailVerified=emailVerified;}
    public Instant getCreatedAt(){return createdAt;}
    public void setCreatedAt(Instant createdAt){this.createdAt=createdAt;}
}
