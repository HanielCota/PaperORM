package com.github.paperorm.migration;

public record Migration(int version, String description, String sql) {}
