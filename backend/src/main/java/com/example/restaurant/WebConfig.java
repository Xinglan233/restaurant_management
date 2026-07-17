package com.example.restaurant;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final ApiAuthInterceptor apiAuthInterceptor;

    public WebConfig(ApiAuthInterceptor apiAuthInterceptor) {
        this.apiAuthInterceptor = apiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthInterceptor).addPathPatterns("/api/**");
    }
}
