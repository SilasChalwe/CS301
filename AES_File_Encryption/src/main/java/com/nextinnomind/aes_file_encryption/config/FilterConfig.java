//package com.nextinnomind.aes_file_encryption.config;
//
//import com.nextinnomind.aes_file_encryption.auth.SimpleAuthFilter;
//import com.nextinnomind.aes_file_encryption.auth.UserService;
//import org.springframework.boot.web.servlet.FilterRegistrationBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class FilterConfig {
//
//    @Bean
//    public FilterRegistrationBean<SimpleAuthFilter> authFilter(UserService userService) {
//        FilterRegistrationBean<SimpleAuthFilter> registrationBean = new FilterRegistrationBean<>();
//        registrationBean.setFilter(new SimpleAuthFilter(userService));
//        registrationBean.addUrlPatterns("/api/*");
//        registrationBean.setOrder(1); // Set order if you have multiple filters
//        return registrationBean;
//    }
//}
