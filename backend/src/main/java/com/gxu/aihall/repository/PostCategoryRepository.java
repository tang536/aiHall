package com.gxu.aihall.repository;

import com.gxu.aihall.entity.PostCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostCategoryRepository extends JpaRepository<PostCategory, Long> {

    List<PostCategory> findAllByOrderBySortAscIdAsc();

    List<PostCategory> findByStatusOrderBySortAscIdAsc(Integer status);

    Optional<PostCategory> findByCode(String code);

    boolean existsByCode(String code);
}
