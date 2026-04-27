package com.capstone.interview.dto;

public record SessionEndRequest(
    String reason  // USER_STOP | TIME_OVER
) {}
