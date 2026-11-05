package com.focusflow.repositories;

import com.focusflow.database.Database;
import com.focusflow.models.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryRepository {
    public List<Category> findAll() {
        return Database.run(c -> {
            List<Category> list = new ArrayList<>();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id,name,color,sort_order FROM categories ORDER BY sort_order, id")) {
                while (rs.next()) {
                    Category cat = new Category(rs.getLong(1), rs.getString(2), rs.getString(3));
                    cat.sortOrder = rs.getInt(4);
                    list.add(cat);
                }
            }
            return list;
        });
    }

    public void insert(Category cat) {
        Database.tx(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO categories(name,color,sort_order) VALUES(?,?,?)")) {
                ps.setString(1, cat.name);
                ps.setString(2, cat.color);
                ps.setInt(3, cat.sortOrder);
                ps.executeUpdate();
            }
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                cat.id = rs.getLong(1);
            }
            return null;
        });
    }

    public void update(Category cat) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE categories SET name=?, color=?, sort_order=? WHERE id=?")) {
                ps.setString(1, cat.name);
                ps.setString(2, cat.color);
                ps.setInt(3, cat.sortOrder);
                ps.setLong(4, cat.id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public void delete(long id) {
        Database.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }
}
