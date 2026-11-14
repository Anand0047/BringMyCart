package com.bringmycart.bmc.identity.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.bringmycart.bmc.identity.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmail(String email);
    boolean existsByEmail(String email);
}
