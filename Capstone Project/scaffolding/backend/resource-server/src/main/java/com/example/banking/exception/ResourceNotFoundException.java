package com.example.banking.exception;

/** Thrown when a resource doesn't exist OR isn't visible to the caller (e.g., not owned). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, String id) {
        super(resource + " " + id + " not found");
    }
}
