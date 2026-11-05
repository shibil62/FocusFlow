package com.focusflow.services;

import com.focusflow.app.AppContext;
import com.focusflow.models.Category;
import com.focusflow.repositories.CategoryRepository;

import java.util.ArrayList;
import java.util.List;

public class CategoryService extends Notifier {
    private final CategoryRepository repo;
    private final List<Category> cache = new ArrayList<>();

    public CategoryService(CategoryRepository repo) {
        this.repo = repo;
        cache.addAll(repo.findAll());
    }

    public void reload() {
        cache.clear();
        cache.addAll(repo.findAll());
        fire();
    }

    public List<Category> all() { return new ArrayList<>(cache); }

    public Category byId(Long id) {
        if (id == null) return null;
        for (Category c : cache) if (c.id == id) return c;
        return null;
    }

    public Category add(String name, String color) {
        Category c = new Category(0, name.trim(), color);
        c.sortOrder = cache.size();
        repo.insert(c);
        cache.add(c);
        fire();
        return c;
    }

    public void update(Category c) {
        repo.update(c);
        fire();
    }

    public void delete(long id) {
        repo.delete(id);
        cache.removeIf(c -> c.id == id);
        if (AppContext.tasks != null) AppContext.tasks.reload();
        fire();
    }
}
