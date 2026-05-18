/**
 * Bootstrap zip embedding via assembly .incbin directive.
 * This embeds the bootstrap zip files directly into the .rodata section.
 */

    .global blob
    .global blob_size

    .section .rodata

blob:
#if defined __aarch64__
    .incbin "bootstrap-aarch64.zip"
#elif defined __arm__
    .incbin "bootstrap-arm.zip"
#elif defined __i686__
    .incbin "bootstrap-i686.zip"
#elif defined __x86_64__
    .incbin "bootstrap-x86_64.zip"
#else
#error "Unsupported architecture"
#endif

blob_size:
    .int . - blob
