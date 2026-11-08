package com.jangbokhuman.workshare.activity;

import com.jangbokhuman.workshare.leave.Attendance;

public record Worker(Long id, String name, Attendance attendance) {}
