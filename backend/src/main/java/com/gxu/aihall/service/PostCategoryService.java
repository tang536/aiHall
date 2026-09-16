package com.gxu.aihall.service;

import com.gxu.aihall.common.BizException;
import com.gxu.aihall.entity.PostCategory;
import com.gxu.aihall.repository.PostCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论坛帖子分类服务
 */
@Service
public class PostCategoryService {

    private final PostCategoryRepository repository;

    public PostCategoryService(PostCategoryRepository repository) {
        this.repository = repository;
    }

    public List<PostCategory> listAll() {
        return repository.findAllByOrderBySortAscIdAsc();
    }

    public List<PostCategory> listEnabled() {
        return repository.findByStatusOrderBySortAscIdAsc(1);
    }

    @Transactional
    public PostCategory create(String code, String name, Integer sort) {
        if (code == null || code.trim().isEmpty()) {
            throw new BizException("分类编码不能为空");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new BizException("分类名称不能为空");
        }
        String finalCode = code.trim().toUpperCase();
        if (repository.existsByCode(finalCode)) {
            throw new BizException("分类编码已存在");
        }
        PostCategory cat = new PostCategory();
        cat.setCode(finalCode);
        cat.setName(name.trim());
        cat.setSort(sort != null ? sort : 0);
        cat.setStatus(1);
        cat.setCreateTime(LocalDateTime.now());
        cat.setUpdateTime(LocalDateTime.now());
        return repository.save(cat);
    }

    @Transactional
    public PostCategory update(Long id, String name, Integer sort, Integer status) {
        PostCategory cat = repository.findById(id)
                .orElseThrow(() -> new BizException("分类不存在"));
        if (name != null && !name.trim().isEmpty()) {
            cat.setName(name.trim());
        }
        if (sort != null) {
            cat.setSort(sort);
        }
        if (status != null) {
            cat.setStatus(status);
        }
        cat.setUpdateTime(LocalDateTime.now());
        return repository.save(cat);
    }

    @Transactional
    public void delete(Long id) {
        PostCategory cat = repository.findById(id)
                .orElseThrow(() -> new BizException("分类不存在"));
        if ("OTHER".equals(cat.getCode())) {
            throw new BizException("默认分类「其他」不能删除");
        }
        repository.delete(cat);
    }
}
