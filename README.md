# ANILOVE scaffold (dev)

This branch mirrors the file structure of `dev_2` at commit
`1ff5932f376176972f5e7c39a195a00d40117e51` so the team can paste code into matching files.

## Empty placeholders

- All 75 Java files, including application and test sources.
- All 8 XML files in `app/src/main/res/layout/`.
- The 3 `profile_avatar_*.xml` drawables.
- The 7 custom UI resource files: `values/auth.xml`, `community.xml`, `listing.xml`,
  `pet.xml`, `profile.xml`, `social.xml`, and `values-night/auth.xml`.

These files are completely empty, without package declarations, classes or XML stubs.

## Preserved content

- Gradle Wrapper, build configuration and dependency catalog.
- Android manifests, backup rules, default launcher icons, base themes, colors and app name.
- Firebase configuration and Firestore rules/indexes.
- Reference documentation from `dev_2`. It describes the implemented source branch,
  not implemented functionality in this empty scaffold.
- `File_Ideas/` and `image/`: the team's existing reference material.

## Usage

1. Open this project in Android Studio and configure your local Android SDK.
2. Paste Java and custom UI resources from the implemented project into matching paths.
3. Sync Gradle, then build and test.

The app cannot build or run while Java and XML placeholders remain empty.
Do not share generated `.gradle/`, `build/`, `node_modules/` or log files.
`local.properties` contains machine-specific SDK settings and stays outside Git.

The previous local `dev` revision is preserved in
`backup/dev-before-scaffold-20261013`.
