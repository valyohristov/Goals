package com.example.loaddist.repository;

import com.example.loaddist.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** Case-insensitive name order; Spring Data has no OrderBy...IgnoreCase support for plain String fields. */
    @Query("SELECT e FROM Employee e ORDER BY LOWER(e.name) ASC")
    List<Employee> findAllOrderByNameIgnoreCase();
}
