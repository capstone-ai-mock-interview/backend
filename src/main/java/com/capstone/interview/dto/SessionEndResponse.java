package com.capstone.interview.dto;

public record SessionEndResponse(
    boolean success,
    String message,
    SessionEndResponse.Data data
) {
    public record Data(String status) {}
}
