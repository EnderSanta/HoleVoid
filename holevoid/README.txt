HOLEVOID — Paper 1.21.11 plugin source project

STATUS
This archive contains Java source code and a build script. It does NOT contain a compiled or runtime-tested JAR. The current chat execution environment has Java 21 but no Maven and no network/DNS access to fetch Paper API dependencies, so I cannot honestly claim the JAR has been built here.

REQUIREMENTS
- Paper 1.21.11
- Java 21
- Maven 3.9 or newer
- Internet access on the computer doing the build (to download the Paper API and Maven dependencies)

BUILD ON WINDOWS
1. Install Java 21 and Maven 3.9+.
2. Extract this ZIP.
3. Double-click build.bat (or open a terminal in this folder and run: mvn -U clean package).
4. If successful, the JAR will be at target/holevoid-1.0.0.jar.
5. Copy that JAR into the Paper server's plugins folder and restart the server.

BUILD ON LINUX/macOS
Run: chmod +x build.sh && ./build.sh

INCLUDED FEATURES IN THIS INITIAL DRAFT
- Homes, warps, spawn, back, TPA
- Economy, /eco, /pay, paginated /baltop (10 entries/page; rank 219 is on page 22)
- HoleVoid / Anarchy sidebar and TAB header/footer
- /vanish and /svanish permissions
- /ah GUI, categories, pages, selling and buying listings

IMPORTANT
- This is an initial implementation draft and has not been compiled or runtime-tested in this environment. Build errors may need fixes before use.
- Test on a backup server before using it on a live server.
- Replace the Telegram placeholder in config.yml when you have the URL.
- If another plugin already owns a command, resolve command conflicts before launch.
