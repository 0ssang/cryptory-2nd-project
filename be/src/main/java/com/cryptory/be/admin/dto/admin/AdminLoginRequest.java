package com.cryptory.be.admin.dto.admin;

import lombok.Data;

@Data
public class AdminLoginRequest {
    private String username;
    private String password;
}
