package com.julio.lifeorganizer.auth.security;

import com.julio.lifeorganizer.auth.domain.Role;

// Principal stored in SecurityContext after JWT validation. Services pull userId from this -
// never from request bodies - so ownership is JWT-bound (ADR-005).
public record AuthenticatedUser(Long id, String email, Role role) {
}
