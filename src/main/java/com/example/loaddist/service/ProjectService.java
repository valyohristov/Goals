package com.example.loaddist.service;

import com.example.loaddist.model.Project;
import com.example.loaddist.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public List<Project> getAll() {
        return projectRepository.findAll().stream().sorted((a, b) -> Long.compare(a.getId(), b.getId())).toList();
    }

    public Optional<Project> getById(Long id) {
        return projectRepository.findById(id);
    }

    @Transactional
    public Project save(Project project) {
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }
}
