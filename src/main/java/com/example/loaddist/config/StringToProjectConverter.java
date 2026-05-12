package com.example.loaddist.config;

import com.example.loaddist.model.Project;
import com.example.loaddist.repository.ProjectRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class StringToProjectConverter implements Converter<String, Project> {

    private final ProjectRepository projectRepository;

    public StringToProjectConverter(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Override
    public Project convert(@NonNull String source) {
        if (source.isBlank()) {
            return null;
        }
        try {
            return projectRepository.findById(Long.valueOf(source)).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
