package com.finansportali.backend.repository;

import com.finansportali.backend.entity.NewsFeed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NewsFeedRepository extends JpaRepository<NewsFeed, Long> {

    List<NewsFeed> findByEnabledTrueOrderByCategoryAscSourceAsc();

    List<NewsFeed> findAllByOrderByCategoryAscSourceAsc();

    Optional<NewsFeed> findByUrl(String url);
}
