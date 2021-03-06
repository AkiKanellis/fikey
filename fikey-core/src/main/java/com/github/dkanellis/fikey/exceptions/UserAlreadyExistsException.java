package com.github.dkanellis.fikey.exceptions;

/**
 * @author Dimitris Kanellis
 */
public class UserAlreadyExistsException extends Exception {
    public UserAlreadyExistsException(String username) {
        super(String.format("User '%s' already exists in the database.", username));
    }
}
