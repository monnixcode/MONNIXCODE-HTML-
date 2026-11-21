# MONNIXCODE HTML Terminal

Standalone browser version of MONNIXCODE. This edition has **no Android project, Java, Gradle, Gradle wrapper, or build dependencies**. It is a static HTML/CSS/JavaScript terminal simulator that runs with a normal shell command.

## Run

```sh
chmod +x run.sh
./run.sh
```

Then open <http://localhost:8080>. To use another port:

```sh
./run.sh 3000
```

A one-line alternative is:

```sh
python3 -m http.server 8080
```

## Terminal themes

Click the theme button in the header to cycle through three visual modes:

- **Classic** — green terminal with subtle scanlines.
- **Cyberpunk** — neon magenta/cyan colors, grid background and glow.
- **Matrix** — high-contrast green terminal with dense scanlines.

The selected theme is saved in the browser and restored on the next visit.

## Included commands

- `nix help`
- `nix up`
- `nix in git curl`
- `nix rm <package>`
- `nix se <text>`
- `nix ls`
- `nix root` (shows the browser sandbox limitation)
- `clear`, `pwd`, `whoami`, `date`, `uname`, `neofetch`, `echo`, `ls`

Package state and command history are stored locally in the browser's `localStorage`. This is a safe browser demo: it does not execute arbitrary host shell commands.
