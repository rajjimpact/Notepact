package com.notepact;

/**
 * Application launcher.
 * This separate Main class is needed when packaging with maven-shade-plugin
 * because JavaFX's Application class requires special module handling.
 */
public class Main {
    public static void main(String[] args) {
        App.main(args);
    }
}
