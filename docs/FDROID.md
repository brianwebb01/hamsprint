# Publishing HamSprint on F-Droid

F-Droid doesn't take uploaded APKs. It builds every app **from source** on its
own servers, and you get listed by opening a merge request (MR) against the
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) repository on GitLab. You
need a GitLab.com account for that. Nobody else can sign up for you: it requires
your identity, human verification, and accepting GitLab's terms.

The repository is already set up to pass F-Droid's checks:

- [x] FOSS license (GPL-3.0-or-later) and only FOSS dependencies (AndroidX, Kotlin, kotlinx).
      No Google Play Services, Firebase, analytics, or ads.
- [x] No permissions and no network access (no anti-features).
- [x] Builds with the standard Gradle wrapper and no prebuilt binaries in the source tree.
- [x] `versionCode` and `versionName` are literals in `app/build.gradle.kts`, so
      F-Droid's auto-update can read them. Releases are tagged `vX.Y.Z`.
- [x] The Google "dependency info" block is disabled, and release builds can be unsigned.
- [x] Store listing in the fastlane layout F-Droid reads automatically:
      `fastlane/metadata/android/en-US/` (title, descriptions, icon, feature
      graphic, screenshots, per-versionCode changelogs).
- [x] Ready-to-submit metadata file: [`docs/fdroid/org.brianwebb.hamsprint.yml`](fdroid/org.brianwebb.hamsprint.yml).

## Step by step (about 30 minutes of your time)

1. **Create a GitLab account** at <https://gitlab.com/users/sign_up> (or sign in
   with your GitHub account). Turn on 2FA.

2. **Fork fdroiddata.** Open <https://gitlab.com/fdroid/fdroiddata> and click
   **Fork** into your namespace.

3. **Add the metadata file.** In your fork, create a branch named
   `org.brianwebb.hamsprint`, then add this repo's
   `docs/fdroid/org.brianwebb.hamsprint.yml` as `metadata/org.brianwebb.hamsprint.yml`.
   You can do it in the GitLab web editor (**+ → New file**) or locally:

   ```sh
   git clone git@gitlab.com:<your-gitlab-user>/fdroiddata.git
   cd fdroiddata
   git checkout -b org.brianwebb.hamsprint
   curl -o metadata/org.brianwebb.hamsprint.yml \
     https://raw.githubusercontent.com/brianwebb01/hamsprint/main/docs/fdroid/org.brianwebb.hamsprint.yml
   git add metadata/org.brianwebb.hamsprint.yml
   git commit -m "New app: HamSprint"
   git push -u origin org.brianwebb.hamsprint
   ```

   Check the category name against the current list in
   [fdroiddata's `config/categories.yml`](https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/categories.yml)
   and change it if it's been renamed.

4. **Optional: test the build locally** with F-Droid's tooling (needs Docker):

   ```sh
   docker run --rm -v "$PWD":/repo registry.gitlab.com/fdroid/docker-executable-fdroidserver:master \
     build -v -l org.brianwebb.hamsprint
   ```

   Skip this and the CI pipeline on your MR runs the same build.

5. **Open the merge request** from your branch into `fdroid/fdroiddata:master`.
   Pick the **"App inclusion"** template and fill in the checklist. Every box
   above is already true, so you can link to this file.

6. **Respond to reviewers.** Volunteers review MRs, usually within days to a few
   weeks. Once it's merged, the app appears in the F-Droid client after the next
   index build (typically 1 to 3 days).

## After it's listed

Nothing more to do per release. F-Droid's `checkupdates` bot watches the
`vX.Y.Z` tags this repo's release workflow creates. It reads the version from
`app/build.gradle.kts` and builds the new version automatically, using the
fastlane changelog for that `versionCode`.

## Optional: ship your own signature on F-Droid (reproducible builds)

By default F-Droid signs the APK with *its* key. Android won't let someone
update between your GitHub APK and the F-Droid build without uninstalling,
because the signatures differ. To make both channels interchangeable, have
F-Droid verify that its build is byte-for-byte identical to your signed GitHub
APK and then distribute yours. Add this to the metadata file:

```yaml
Binaries: https://github.com/brianwebb01/hamsprint/releases/download/v%v/HamSprint-v%v.apk
AllowedAPKSigningKeys: 0ae424071e2f192ea9d014b431ef506764d68b5b99d8e6e7fd3d5a85771564d6
```

(That digest is the SHA-256 of HamSprint's release signing certificate.)
Reproducibility can need some back-and-forth with reviewers, so it's reasonable
to get listed first and add this in a follow-up MR.

## Your signing key: back it up

The release keystore lives **outside the repo** at
`~/.config/hamsprint-signing/` (`hamsprint-release.jks` plus `keystore.properties`
with its password), and is stored as GitHub Actions secrets for CI. Copy both
files into your password manager. If the key is lost, existing installs can
never receive another update signed by you.
