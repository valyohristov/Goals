package com.example.loaddist.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ld_projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "capacity_fte", nullable = false, precision = 5, scale = 3)
    private BigDecimal capacityFte;

    @Column(length = 32)
    private String color;

    public Project() {}

    public Project(String name, BigDecimal capacityFte, String color) {
        this.name = name;
        this.capacityFte = capacityFte;
        this.color = color;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getCapacityFte() { return capacityFte; }
    public void setCapacityFte(BigDecimal capacityFte) { this.capacityFte = capacityFte; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
