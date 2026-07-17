# Notepact Deployment and Native Bundling Plan

This plan describes how we will set up automated, native desktop packaging for both **Windows** and **macOS** using **GitHub Actions** (which is 100% free for public repositories).

---

## 💡 Suggested Deployment Platform: GitHub Actions
The best free platform for deploying desktop apps is **GitHub Actions Releases**:
- **Why?** Since macOS installers (`.dmg`) can only be built on macOS, and Windows installers (`.msi` / `.exe`) require Windows, GitHub Actions provides free cloud virtual environments (`macos-latest`, `windows-latest`) to build native installers for both platforms automatically.
- **Delivery:** Every time you push a git tag (e.g., `v1.0.0`), GitHub Actions will run, compile the code, package it, and upload the native installers directly to a **GitHub Release** page for users to download.

---

## Proposed Changes

We will introduce a GitHub Actions CI/CD workflow and update our Maven build configuration to support native bundling.

### 1. Build Configurations

#### [MODIFY] [pom.xml](file:///c:/Users/Rajvardhan/OneDrive/Desktop/Notepact/pom.xml)
- Enhance the Maven build settings to support `jlink` module compilation.
- Configure resource copy filters to ensure styling stylesheets (`.css` files) are correctly packed inside the JAR/Runtime.

#### [NEW] [build.yml](file:///c:/Users/Rajvardhan/OneDrive/Desktop/Notepact/.github/workflows/build.yml)
- Create a GitHub Actions workflow file that runs on both Windows and macOS runners.
- The workflow will perform the following steps:
  1. Set up JDK 21.
  2. Build and compile the project using Maven.
  3. Package the application into a fat executable JAR.
  4. Run `jpackage` on the respective OS runner to build:
     - **Windows:** An `.msi` native installer (requires WiX toolset, which is pre-installed on GitHub runners).
     - **macOS:** A `.dmg` disk image.
  5. Publish these native bundles to a draft GitHub release page.

---

## Verification Plan

### Automated Verification
- Commit the changes and push them to your repository on GitHub.
- Observe the **Actions** tab on GitHub to verify both the Windows and macOS builds pass.

### Manual Verification
- Access the generated draft release on your GitHub repository.
- Download and run `Notepact-1.0.0.msi` on Windows to test the installer.
- Download and open the `.dmg` file on macOS to test the drag-to-Applications flow.
