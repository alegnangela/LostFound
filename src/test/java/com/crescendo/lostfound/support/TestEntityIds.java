package com.crescendo.lostfound.support;

import java.lang.reflect.Field;

/**
 * Entities only ever get an id from JPA on persist, and deliberately expose no
 * setter. In slice tests (e.g. {@code @WebMvcTest}) that mock the service layer
 * and never touch a real database, we still need entities with realistic,
 * distinct ids to exercise code that groups or looks values up by id. Reflection
 * is the pragmatic way to simulate "this entity has been persisted" without
 * weakening the entity's production API just for tests.
 */
public final class TestEntityIds {

    private TestEntityIds() {
    }

    public static <T> T assignId(T entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not assign test id to " + entity.getClass(), e);
        }
    }
}
