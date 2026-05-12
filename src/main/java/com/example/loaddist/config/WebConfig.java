package com.example.loaddist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StringToEmployeeConverter stringToEmployeeConverter;
    private final StringToProjectConverter stringToProjectConverter;

    public WebConfig(StringToEmployeeConverter stringToEmployeeConverter,
                     StringToProjectConverter stringToProjectConverter) {
        this.stringToEmployeeConverter = stringToEmployeeConverter;
        this.stringToProjectConverter = stringToProjectConverter;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(stringToEmployeeConverter);
        registry.addConverter(stringToProjectConverter);
    }
}
