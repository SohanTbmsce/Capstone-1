package com.hdfc.jwtauth.services;

import com.hdfc.jwtauth.entity.UserAccount;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InMemoryUserService {

    private final Map<String, UserAccount> users = new HashMap<>();

    public InMemoryUserService() {

        users.put("sachin",
                new UserAccount("sachin", "sachin123", Set.of("USER"), true));

        users.put("admin",
                new UserAccount("admin", "admin123", Set.of("USER", "ADMIN"), true));

        users.put("rahul",
                new UserAccount("rahul", "rahul123", Set.of("USER"), true));

        users.put("sahil",
                new UserAccount("sahil", "sahil123", Set.of("USER"), true));

        users.put("rohit",
                new UserAccount("rohit", "rohit123", Set.of("USER"), true));

        users.put("amit",
                new UserAccount("amit", "amit123", Set.of("USER"), true));

        users.put("priya",
                new UserAccount("priya", "priya123", Set.of("USER"), true));

        users.put("neha",
                new UserAccount("neha", "neha123", Set.of("USER"), true));

        users.put("arjun",
                new UserAccount("arjun", "arjun123", Set.of("USER"), false));

        users.put("vikas",
                new UserAccount("vikas", "vikas123", Set.of("USER"), false));
    }
    public UserAccount find(String username) {
        return users.get(username);
    }

    public Collection<UserAccount> getAllUsers() {
        return users.values();
    }
}