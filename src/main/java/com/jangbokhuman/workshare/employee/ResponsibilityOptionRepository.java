package com.jangbokhuman.workshare.employee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ResponsibilityOptionRepository
        extends JpaRepository<ResponsibilityOption, String> {
    List<ResponsibilityOption> findAllByOrderByTitleAsc();

    List<ResponsibilityOption> findByTitleIn(Collection<String> titles);
}
