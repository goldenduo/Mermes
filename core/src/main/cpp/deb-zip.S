/**
 * Deb packages zip embedding via assembly .incbin directive.
 * This embeds the deb packages zip files directly into the .rodata section.
 */

    .global deb_blob
    .global deb_blob_size

    .section .rodata

deb_blob:
#if defined __aarch64__
    .incbin "debs-aarch64.zip"
#elif defined __arm__
    .incbin "debs-arm.zip"
#elif defined __i686__
    .incbin "debs-i686.zip"
#elif defined __x86_64__
    .incbin "debs-x86_64.zip"
#else
#error "Unsupported architecture"
#endif

deb_blob_size:
    .int . - deb_blob
