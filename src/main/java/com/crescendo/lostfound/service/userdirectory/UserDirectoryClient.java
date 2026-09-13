package com.crescendo.lostfound.service.userdirectory;

/**
 * Boundary to the external User Service that owns user profile data.
 * We only need a display name for a given user id, so the contract is
 * kept deliberately narrow rather than modelling the whole user profile.
 */
public interface UserDirectoryClient {

    /** Resolves the display name for a user id, e.g. for showing who claimed a lost item. */
    String getUserName(String userId);
}
