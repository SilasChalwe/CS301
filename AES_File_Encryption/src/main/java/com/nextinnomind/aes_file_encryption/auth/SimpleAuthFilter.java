//package com.nextinnomind.aes_file_encryption.auth;
//
//import jakarta.servlet.*;
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//
//import java.io.IOException;
//
//public class SimpleAuthFilter implements Filter {
//
//    private final UserService userService;
//
//    public SimpleAuthFilter(UserService userService) {
//        this.userService = userService;
//    }
//
//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//            throws IOException, ServletException {
//
//        HttpServletRequest req = (HttpServletRequest) request;
//        HttpServletResponse res = (HttpServletResponse) response;
//
//        String path = req.getRequestURI();
//
//        // Allow public endpoints
//        if (path.startsWith("/api/register") ||
//                path.startsWith("/api/login") ||
//                path.startsWith("/api/ping") ||
//                path.startsWith("/api/device") ||
//                path.startsWith("/api/provision") ||
//                path.startsWith("/api/reset")) {
//            chain.doFilter(request, response);
//            return;
//        }
//
//        String username = req.getHeader("X-Auth-User");
//        if (username == null || !userService.exists(username)) {
//            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            res.getWriter().write("Unauthorized: Missing or invalid X-Auth-User header");
//            return;
//        }
//
//        chain.doFilter(request, response);
//    }
//}
