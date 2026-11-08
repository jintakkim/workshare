package com.jangbokhuman.workshare.activity;

import java.util.List;

public record TaskAllocation(List<Worker> workers, List<String> warnings) {}
