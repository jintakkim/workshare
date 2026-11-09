package com.jangbokhuman.workshare.employee;

import com.jangbokhuman.workshare.calendar.WorkTime;
import com.jangbokhuman.workshare.config.AuthenticatedUser;
import com.jangbokhuman.workshare.config.WorkSettings;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeUserDetailsService implements UserDetailsService {
    private final EmployeeRepository employeeRepository;
    private final WorkSettings workSettings;
    private final WorkTime workTime;
    private final String adminPasswordHash;

    public EmployeeUserDetailsService(
            EmployeeRepository employeeRepository,
            WorkSettings workSettings,
            WorkTime workTime,
            PasswordEncoder passwordEncoder
    ) {
        this.employeeRepository = employeeRepository;
        this.workSettings = workSettings;
        this.workTime = workTime;
        this.adminPasswordHash = passwordEncoder.encode(workSettings.adminPassword());
    }

    @Override
    public UserDetails loadUserByUsername(String enteredName) {
        String name = LoginNames.normalize(enteredName);
        if (name.equals(LoginNames.normalize(workSettings.adminUsername()))) {
            return new AuthenticatedUser(null, name, adminPasswordHash, true, "ADMIN");
        }
        Employee employee = employeeRepository.findByName(name)
                .orElseThrow(() -> new UsernameNotFoundException("계정 없음"));
        return new AuthenticatedUser(
                employee.getId(),
                employee.getName(),
                employee.getPasswordHash(),
                !employee.isExcludedByAdminOn(workTime.getToday()),
                "STAFF"
        );
    }
}
