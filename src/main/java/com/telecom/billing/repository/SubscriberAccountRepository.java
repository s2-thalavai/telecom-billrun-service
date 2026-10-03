package com.telecom.billing.repository;

import com.telecom.billing.entity.SubscriberAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubscriberAccountRepository extends JpaRepository<SubscriberAccount, String> {

    long countByActiveTrue();

    /** Keyset pagination: stable and fast for millions of rows; the last id is the checkpoint. */
    @Query("select a.accountId from SubscriberAccount a where a.active = true and a.accountId > :after order by a.accountId")
    List<String> findIdsAfter(@Param("after") String after, Pageable page);
}
