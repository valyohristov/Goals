package com.example.loaddist.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ld_employees")
public class Employee {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "capacity_fte", nullable = false, precision = 5, scale = 3)
    private BigDecimal capacityFte;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Employee manager;

    @Column(name = "is_manager", nullable = false)
    private boolean isManager;

    public Employee() {}

    public Employee(String name, BigDecimal capacityFte) {
        this.name = name;
        this.capacityFte = capacityFte;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getCapacityFte() { return capacityFte; }
    public void setCapacityFte(BigDecimal capacityFte) { this.capacityFte = capacityFte; }

    public Employee getManager() { return manager; }
    public void setManager(Employee manager) { this.manager = manager; }

    public boolean isManager() { return isManager; }
    public void setIsManager(boolean manager) { isManager = manager; }
}
