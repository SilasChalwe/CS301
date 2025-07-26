//package com.nextinnomind.aes_file_encryption.auth;
//
//import org.springframework.stereotype.Service;
//
//import java.util.Map;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Service
//public class UserService {
//
//    private final Map<String, String> users = new ConcurrentHashMap<>();
//
//    public boolean register(String username, String password) {
//        if (users.containsKey(username)) {
//            return false; // Username already exists
//        }
//        users.put(username, password);
//        return true;
//    }
//
//    public boolean authenticate(String username, String password) {
//        return users.containsKey(username) && users.get(username).equals(password);
//    }
//
//    public boolean exists(String username) {
//        return users.containsKey(username);
//    }
//
//    public void clear() {
//        users.clear(); // For testing purposes or admin reset
//    }
//}
