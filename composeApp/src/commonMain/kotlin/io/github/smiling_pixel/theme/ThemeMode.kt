package io.github.smiling_pixel.theme

/**
 * Represents the different theme modes available in the application.
 *
 * This enum is used to determine which color palette (light or dark)
 * should be applied to the user interface.
 */
enum class ThemeMode {
    /**
     * Follows the system's default theme settings.
     * If the operating system is set to dark mode, the app will use the dark theme.
     * Otherwise, it will default to the light theme.
     */
    SYSTEM,

    /**
     * Forces the application to use the light theme, regardless of the system settings.
     */
    LIGHT,

    /**
     * Forces the application to use the dark theme, regardless of the system settings.
     */
    DARK,
}
