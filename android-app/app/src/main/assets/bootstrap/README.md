# MONNIXCODE bootstrap contract

This directory defines the runtime layout expected by the Kotlin installer. The current app ships the Web Workspace and creates the directory contract on first launch.

A complete native Linux environment cannot be reconstructed from the Android UI source alone. Before enabling a real shell, provide verified bootstrap archives built for the matching ABI and prefix using the Termux packages toolchain. Keep package signatures, checksums, licenses and the source repository together.

Expected runtime prefix:

```text
files/monnixcode-runtime/
└── usr/
    ├── bin/
    ├── etc/
    └── var/lib/
```

The app deliberately refuses to pretend that this layout is a working shell. Until verified archives are added, the app remains a WebView Workspace and the full Linux shell is available through the Termux app/source separately.
