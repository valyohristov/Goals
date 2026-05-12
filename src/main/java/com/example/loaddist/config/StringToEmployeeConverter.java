package com.example.loaddist.config;

import com.example.loaddist.model.Employee;
import com.example.loaddist.repository.EmployeeRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToEmployeeConverter implements Converter<String, Employee> {

    private final EmployeeRepository employeeRepository;

    public StringToEmployeeConverter(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public Employee convert(@NonNull String source) {
        if (source.isBlank()) {
            return null;
        }
        try {
            return employeeRepository.findById(Long.valueOf(source)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
