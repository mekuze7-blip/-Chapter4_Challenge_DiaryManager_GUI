# Personal Diary Manager (JavaFX 25)

## Overview
A modern, secure, and feature-rich Personal Diary Manager built with Java 25 and JavaFX 25. This application demonstrates advanced file I/O, encryption, and modern GUI design.

## Features
*   **Security**: AES-128 encryption ensures diary entries are unreadable on disk.
*   **Rich Text Editor**: Write entries with formatting (bold, colors, etc.).
*   **Read Mode**: View entries in a clean, read-only web view with search highlighting.
*   **Search**: Real-time filtering and content highlighting.
*   **Printing**: Export entries to PDF or print them directly.
*   **Modern UI**: Responsive SplitPane layout with Light/Dark mode support.

## Requirements
*   JDK 21 or later (Tested with JDK 25)
*   JavaFX SDK 25

## How to Run

1.  **Configure Path**: Ensure you have the JavaFX SDK. Update the path in the commands below to match your installation (e.g., `C:\Users\hp\Desktop\openjfx-25.0.1_windows-x64_bin-sdk\javafx-sdk-25.0.1\lib`).

2.  **Compile**:
    ```cmd
    javac --module-path "path\to\javafx\lib" --add-modules javafx.controls,javafx.web DiaryManager.java
    ```

3.  **Run**:
    ```cmd
    java --module-path "path\to\javafx\lib" --add-modules javafx.controls,javafx.web DiaryManager
    ```

## Technical Highlights
*   **Virtual Threads**: Uses `Thread.ofVirtual()` for responsive background tasks.
*   **NIO.2**: Utilizes `Files.writeString` and `Files.readString` for clean I/O.
*   **Cryptography**: Implements `javax.crypto` for transparent encryption/decryption.
