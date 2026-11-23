package model;

/** A staff account at a clinic, for the clinic admin's staff list. {@code role} is their role at that clinic. */
public record StaffMember(int userId, String email, String displayName, StaffRole role) {}
