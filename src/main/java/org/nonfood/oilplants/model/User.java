package org.nonfood.oilplants.model;

import java.io.Serializable;

public class User implements Serializable {
    private long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String role;
    private boolean enabled;
    private String createdAt;
    public long getId(){return id;} public void setId(long id){this.id=id;}
    public String getUsername(){return username;} public void setUsername(String username){this.username=username;}
    public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String passwordHash){this.passwordHash=passwordHash;}
    public String getDisplayName(){return displayName;} public void setDisplayName(String displayName){this.displayName=displayName;}
    public String getEmail(){return email;} public void setEmail(String email){this.email=email;}
    public String getRole(){return role;} public void setRole(String role){this.role=role;}
    public boolean isEnabled(){return enabled;} public void setEnabled(boolean enabled){this.enabled=enabled;}
    public String getCreatedAt(){return createdAt;} public void setCreatedAt(String createdAt){this.createdAt=createdAt;}
    public boolean isAdmin(){return "ADMIN".equals(role);}
}
