package com.baksart.Note2TexBack.auth;

import com.baksart.Note2TexBack.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class VerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(nullable=false, unique=true) String token;
    @ManyToOne(optional=false) User user;
    @Column(nullable=false) Instant expiresAt;
    @Column(nullable=false) boolean used=false;
    public Long getId(){return id;}
    public String getToken(){return token;}
    public void setToken(String t){this.token=t;}
    public User getUser(){return user;}
    public void setUser(User u){this.user=u;}
    public Instant getExpiresAt(){return expiresAt;}
    public void setExpiresAt(Instant t){this.expiresAt=t;}
    public boolean isUsed(){return used;}
    public void setUsed(boolean used){this.used=used;}
}
