package com.julio.lifeorganizer.auth.service;

import com.julio.lifeorganizer.auth.persistence.UserRepository;
import com.julio.lifeorganizer.auth.web.dto.UserResponse;
import com.julio.lifeorganizer.common.exception.UserNotFoundForTokenException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse findById(Long id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(UserNotFoundForTokenException::new);
    }
}
