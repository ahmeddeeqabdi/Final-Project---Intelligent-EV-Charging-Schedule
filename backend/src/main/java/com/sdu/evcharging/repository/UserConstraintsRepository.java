package com.sdu.evcharging.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sdu.evcharging.domain.UserConstraints;

public interface UserConstraintsRepository extends JpaRepository<UserConstraints, Long> {

    Optional<UserConstraints> findByUserId(Long userId);
}
