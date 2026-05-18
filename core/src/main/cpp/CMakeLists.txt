cmake_minimum_required(VERSION 3.22.1)
project("mermes-core")

# Bootstrap library - embeds bootstrap zip files
add_library(
    mermes-bootstrap
    SHARED
    bootstrap-zip.S
    bootstrap-jni.c
)

target_link_libraries(
    mermes-bootstrap
    log
    android
)

# Terminal library - PTY and process management
add_library(
    mermes-terminal
    SHARED
    terminal-jni.c
)

target_link_libraries(
    mermes-terminal
    log
    android
)

# Deb library - embeds deb packages
add_library(
    mermes-deb
    SHARED
    deb-jni.c
)

target_link_libraries(
    mermes-deb
    log
    android
)

# Utils library - file operations, permissions
add_library(
    mermes-utils
    SHARED
    utils-jni.c
)

target_link_libraries(
    mermes-utils
    log
    android
)
