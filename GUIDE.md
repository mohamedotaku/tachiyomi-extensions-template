## FYIs

I recommend **NOT** using the Android project view (top left in Android Studio), as it can get quite
uncomfortable. When working with tachiyomi extensions.
Unfortunately Android Studio really likes to switch to it unexpectedly.

I'll use `~/` to indicate the project root, not the user home, while I'll use `./` to indicate the
current extension's project directory.

# Guides

Both setup guides explain all the steps needed, the fast version can be used if you're confident
in your knowledge, have already done this and need a refresher, or to set things up quickly.

You can switch back and forth between the fast and in-depth guides by clicking on the `[ID*]` links,
so if you're curious about something you can just check the corresponding step,
which will provide more information.

* [Prerequisites](#prerequisites)
* [Fast Setup](#fast-setup)
* [In-Dept Setup](#in-dept-setup)
* [Update Guide](#update-guide)

## Prerequisites

You should optimally have a JDK 17 or 21 installed, I personally recommend
[Adoptium](https://adoptium.net/) but any decent JDK version 11 or later should work.
The workflows run on Java 21.

## Fast Setup

1. [[ID1](#step-1)] Use this repository as a template.
2. [[ID2](#step-2-4)] Use the task `:new:extension --path <path>` to create a skeletal extension
   in [extensions](./extensions). Sync gradle to see it.
    - e.g. `:new:extension --path foo/bar/baz/projectsuki` will create a skeletal extension in the
      directory `~/extensions/foo/bar/baz/projectsuki` with identifier `projectsuki`.
    - Extensions can be put anywhere recursively in the [extensions](./extensions) directory
    - Identifiers must be unique in the project.
    - See [settings.gradle.kts](./settings.gradle.kts) for more info
3. [[ID3](#step-2-4)] Modify the build.gradle.kts to your liking through
   `setupTachiyomiExtensionConfiguration`
    - You can add libs and multisrc through the configuration function
4. [[ID4](#step-2-4)] In the generated package, create a new class that extends HttpSource or
   ParsedHttpSource
    - Remember to rename the package
5. [[ID5](#step-5)] Create the necessary icons (ic_launcher.png) through whatever
   means ([recommended tool](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html)).
6. [[ID6](#step-6)] Implement your source logic how you would've in the original extension repo
    - Remember to modify the `./AndroidManifest.xml` file if you plan on using activities.
7. [[ID7](#step-7)] Run the command `:constructDebugRepo`: A fully built
   debug repository should be under `~/build/repo/debug`
    - You can serve this directory by using the `:serve:debugRepo` task and access it from the
      emulated device with `10.0.2.2:8080`.
        - By default it uses host `127.0.0.1`, you can change it with the `--host` option, but you
          will need to look up how that address maps inside the emulator.
        - By default it uses port `8080`, you can change it with the `--port` option
    - (_Currently doesn't work on account of tachiyomi not allowing non-https urls_)
8. [[ID8](#step-8)] If everything works, you can move onto the release stage by setting up some
   stuff on GitHub:
    1. Secrets:
        - **KEY_FILE_NAME**: file name where the keystore will be temporarily decoded, you can use
          an [UUID](https://www.uuidgenerator.net/) (only for this)
        - **KEY_STORE**: base64 encoded version of the `.keystore` generated with `keytool`
        - **KEY_STORE_ALIAS**: alias for the key provided at time of creation of the `.keystore`
        - **KEY_PASSWORD**, **KEY_STORE_PASSWORD**: depending on your means for generating
          the `.keystore` they might be the same or different.
          Provided at the time of creation of the `.keystore`
    2. Variables:
        - **DO_PUBLISH_REPO**: if `true`, the github workflow will use the `repo` branch as the
          release repository location. You can use `git switch --orphan repo`
          and `git commit --allow-empty -m "Empty repo"` to create a branch with no history
          (be careful about uncommitted changes to the `master` branch when you use switch).
        - Everything in the `repo` branch will be wiped with every release.
9. [[ID9](#step-9)] By default the workflow will only run when you request it from GitHub, you can
   change this by modifying [build_release.main.kts](.github/workflows/build_release.main.kts),
   _if you know what you're doing_ (you can use `:workflows:update` to update the .yaml files).
10. You should now have a `repo` branch containing all the necessary data.
    You can now point your users to
    `https://raw.githubusercontent.com/{you}/{your-repo}/repo/index.min.json`.
    - It must be the `min.json` version, the pretty version is provided for humans.

## In-Dept Setup

Some assumptions are made in this guide:

- You know what [gradle tasks](https://docs.gradle.org/current/userguide/tutorial_using_tasks.html)
  are and how to execute them.
- You know have a basic to moderate grasp over the [Kotlin](https://kotlinlang.org/) language.
- You have a basic understanding of how android apps work.

### Step 1

[Back to fast](#fast-setup)

You can either use this repository as a
[template](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).
or [fork](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/working-with-forks/fork-a-repo)
it, which _could_ make the updating process easier and/or faster
(as you could always get the latest version).

But that has the disadvantage that if you ever wanted to contribute to this template by opening a
PR, you would have to execute some git-gymnastics to get it done as GitHub limits
the number of forks against a single repository, to 1 (you could create an
[organization](https://docs.github.com/en/organizations/collaborating-with-groups-in-organizations/about-organizations)
strictly speaking).

You can check the [update guide](#update-guide) for information on how to update the template,
and decide for yourself.

-----

### Step 2-4

[Back to fast](#fast-setup)

You can use the `:new:extension --path <path>` gradle task:

This will create a skeletal extension under `path` which can either be a direct child
of [extensions](./extensions) or a sub directory: `--path foo/bar/projectsuki`
and `--path projectsuki` will both create an extension with identifier `projectsuki` but in
different subdirectories. The task will fail if the directory already exists, even if empty.

Extensions are automatically found and imported into gradle recursively
under [extensions](./extensions) (see [settings.gradle.kts](./settings.gradle.kts)).
A directory is considered an extension if it contains a `build.gradle` or `build.gradle.kts` file
inside of it.

Note that the **identifiers** have to be unique, not the locations.
In the `--path` example above the sync will fail.

You can inspect the task by checking the [build.gradle.kts](./utils/new/build.gradle.kts)
under `~/utils/new`.

After changes (deletion or additions of extensions) a gradle sync will be needed.

The task will set up a couple of things:

- A minimal `./AndroidManifest.xml`, which is needed by the android build process.
  You will need to modify this if you wish to provide some advanced functionalities to your app,
  like url activities. For a standard extension, you won't need to touch it.
- A `./build.gradle.kts` which will contain a call to `setupTachiyomiExtensionConfiguration`,
  defined in the [extension.kt](./build-src/conventions/src/main/kotlin/extension.kt) convention.
  You can read the documentation of the function to understand what every parameter does, but
  primarily you will need to change these parameters:
    1. `namespaceIdentifier`: This represents an identifier to group all your extensions together.
       I recommend using the same package prefix you would use when creating a JVM/Kotlin/Android
       library/app. (e.g. for me `dev.npgx.etc` => `npgx`)
    2. `extName`: This is the name of the extension that will appear to the user, both in the app
       name and in the extensions list under tachiyomi.
    3. `pkgNameSuffix`: Convention is to use a lowercase version of the `extName`.
       (e.g. `A Pair of 2+` => `apairof2plus`)
    4. `extClass`: Name of the class that represents your extension, it can be anything, but it's
       convention to have it closely represent your extension.
       (e.g. `A Pair of 2+` => `.APairOf2Plus`)
       You can use the `Factory` suffix if the class represents an extension factory.
       (multiple sources)
        - **NOTE:** The `.` prefix is needed and intentional, it's an artifact of the way tachiyomi
          handles extensions.
    5. `extVersionCode`: Represents the version of your extension, should be increased every time
       you want your users to update the extension (otherwise they won't be prompted with an update)
    6. `isNsfw`: Whether or not the extension manages
       [Not Safe For Work](https://www.dictionary.com/browse/nsfw) content.

The task will automatically create the `./src`, `./res` and `./src/eu/kanade/tachiyomi/extension`
directories:

- The `./src` directory is used for both java and kotlin code.
- The `./res` directory is used for images and resources.
- The `./src/eu/kanade/tachiyomi/extension` is just to facilitate the next step

To have your extension correctly packaged, you will need to create a class called
`{extClass}` (without the dot) under the package
`eu.kanade.tachiyomi.extension.{namespaceIdentifier}.{pkgNameSuffix}`.

So for example, if I wanted to create an extension called `Project Suki` I would configure the
extension as such:

- `namespaceIdentifier`: `npgx`
- `extName`: `Project Suki`
- `pkgNameSuffix`: `projectsuki`
- `extClass`: `.ProjectSuki`

I would then create a class called `ProjectSuki`
under `./src/eu/kanade/tachiyomi/extension/npgx/projectsuki`.

The class should extend:

- `HttpSource` if your source doesn't use a standard template structure,
  (like being hand-made through a CSS library such as [Bootstrap](https://getboostrap.com)).
- `ParsedHttpSource` if your source _does_ follow a template, but this template isn't
  [one of the known templates (`MultiSrc` class)](./build-src/conventions/src/main/kotlin/extension.kt).
- A multisrc template
  like [Madara](./multisrc/madara/src/main/kotlin/eu/kanade/tachiyomi/multisrc/madara/Madara.kt)
  if your source uses that. You can include the one or more multisrc templates
  into your extension by simply adding the `multisrc` parameter to
  the `setupTachiyomiExtensionConfiguration` function.

You can also add
[libraries (`TachiyomiLibrary` class)](./build-src/conventions/src/main/kotlin/extension.kt)
via the `libs` parameter.

-----

### Step 5

[Back to fast](#fast-setup)

Icons need to be created to follow this structure:

```doxygen
  res
  ├── mipmap-hdpi
  │   └── ic_launcher.png
  ├── mipmap-mdpi
  │   └── ic_launcher.png
  ├── mipmap-xhdpi
  │   └── ic_launcher.png
  ├── mipmap-xxhdpi
  │   └── ic_launcher.png
  └── mipmap-xxxhdpi
      └── ic_launcher.png
```

You can use the
[recommended tool](https://as280093.github.io/AndroidAssetStudio/icons-launcher.html)
to do this, but this isn't strictly necessary, as long as you follow the
[Android rules](https://developer.android.com/training/multiscreen/screendensities#mipmap).

If you don't provide any icon, the default will be used (not recommended):

![the default](./utils/default/res/mipmap-hdpi/ic_launcher.png)

-----

### Step 6

[Back to fast](#fast-setup)

**WIP**

-----

### Step 7

[Back to fast](#fast-setup)

Tachiyomi expects sources repo to follow this structure:

```doxygen
  apk
  ├── <ext1>.apk
  ├── <ext2>.apk
  └── <ext3>.apk
  
  icon
  ├── <ext1>.png
  ├── <ext1-package>.png
  ├── <ext2>.png
  ├── <ext2-package>.png
  ├── <ext3>.png
  └── <ext3-package>.png
  
  index.min.json

```

The provided `:construct<variant>Repo` tasks will do everything for you,
you can use the `:constructDebugRepo` to create local debug APKs.

I do not recommend providing release signing variables and secrets
on your local machine unless you have experience with the whole process.
The `debug` variants should suffice for the majority of cases and mirror
`release` variants exactly.

You can find the repos under the `~/build/repo` directory.

**NOTE:** as of now, tachiyomi doesn't allow for non-https urls (such as localhost),
and everything is locked down (Issues, PRs, Discord), so the following steps are for future
reference.

There is also the `:serve:debugRepo` task that creates a
static server using [Ktor](https://ktor.io) to serve the `~/build/repo/debug`
directory.

By default it will use host `127.0.0.1` and port `8080`, you can change both of them using
the `--host` and `--port` options.
When using host `127.0.0.1`, you can access the repo on your emulator through the
address `10.0.2.2:<port>`.

You can read more about how addresses are mapped on emulators
[here](https://developer.android.com/studio/run/emulator-networking.html).

-----

### Step 8

[Back to fast](#fast-setup)

If the debug repo and all your extensions work as intended, you can move onto the release variant.

You'll first need to either use or create a keystore.
To create a keystore, you'll need to use
the [keytool](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html)
executable, which should come bundled with most JDKs.

Prepare a new directory wherever you like, create a new file `pass`
and put inside of it the password you want to use
(you don't technically need to do this, but it's the safest way to hand over
passwords with special characters)

You technically need 2 passwords, one for the keystore and one for the key itself,
but with recent versions of keytool, both passwords will be forced to be the same.

Finally, execute the command below with `<alias>` replaced by the key alias you want to use:
A keystore can store multiple keys, an alias is a way to specify which key.
In this case we only care about one key.
You can add a key to a keystore by running the command below
with an already existing `.keystore`

```
keytool -genkeypair -alias "<alias>" -keyalg RSA -keysize 4096 -validity 10000 -keypass:file pass -storepass:file pass -keystore release.keystore -v
```

You will be prompted for some more information,
provide what you wish to provide (press enter for default).

If everything went well, you should have a `release.keystore` file.
Unfortunately, GitHub doesn't allow to upload files as secrets.
As such, we'll need to base64-encode it.

On Linux you can use this command:

```bash
openssl base64 -A -in release.keystore -out release.keystore.base64
```

While on Windows you can use:

```powershell
[convert]::ToBase64String((Get-Content -path ".\release.keystore" -Encoding byte)) | Out-File -FilePath ".\release.keystore.base64"
```

Now we can move onto the repository
[Secrets](https://docs.github.com/en/actions/security-guides/using-secrets-in-github-actions)
and [Variables](https://docs.github.com/en/actions/learn-github-actions/variables).

If you're curious about the difference between environment secrets and
repository secrets, you can look at [this SO answer](https://stackoverflow.com/a/65958690).

You will need to provide:

#### Secret: KEY_FILE_NAME

This represents the name of the temporary file where the keystore will be base64-decoded,
you can simply use an [UUID](https://www.uuidgenerator.net/) (but just for this!)

#### Secret: KEY_STORE

This will need to be the contents of the `release.keystore.base64` file, without **any** line
breaks, including at the end. It will be decoded during the workflow.

#### Secret: KEY_STORE_ALIAS

This is the `<alias>` you provided during keystore creation. It identifies the key we want to use.

#### Secrets: KEY_PASSWORD and KEY_STORE_PASSWORD

Possibly the same or different depending on your means of generating the keystore.
They represent the `-keypass` anb `-storepass` parameters (contents of `pass` file).

#### Variable: DO_PUBLISH_REPO

During the workflow, the release repository is uploaded using
[artifact-upload](https://github.com/actions/upload-artifact)
(only members can access it).
So if you would like to provide some other means for users to access your
repository, you can stop here.

If however you would like to use your github repository as means to access the repo,
then you will need to set `DO_PUBLISH_REPO` to `true` (any other string will fail).

#### !IMPORTANT! -> Repo Branch

**This only needs to be done if `DO_PUBLISH_REPO` is `true`**

If you would like to use your github repository as a repo, you will need to create a `repo`
branch, possibly an empty one to avoid bringing your master branch git history.

This can be done quite easily, with a couple of notes:

- You should commit all changes you don't want to lose as this command will bring you into the
  context of the new branch, a.k.a. **nothing**. You don't _need_ to push them, but I recommend
  doing so. (you can also just clone your repo in another directory and use that)

```bash
git switch --orphan repo
git commit --allow-empty -m "Repo branch"
git push -u origin repo
```

All files in this branch will be **wiped** with every release workflow run.
Use `git checkout master` to switch back to your master branch.
See [this SO answer](https://stackoverflow.com/a/34100189) for some more info.

Now that everything is set up, go into Actions -> `Construct Release`
and click on the Run workflow button (run from the master branch).

At the end of the workflow run you should have 2 primary things:

1. `release-repo` artifact inside of the run (will expire in 1 day).
2. A commit by `github-actions[bot]` in the `repo` branch with message `Update repo`

Now you can direct your users to:

```
https://raw.githubusercontent.com/{you}/{your-repo}/repo/index.min.json
```

The workflow will automatically attempt to purge
[jsdelivr](https://www.jsdelivr.com/)'s caches.
If for some reason your workflow fails at this step, the `repo` branch
should already be updated to the latest version.

-----

### Step 9

[Back to fast](#fast-setup)

By default the workflow only runs when requested to avoid unnecessary runs.
However, it can be configured to also run on push events quite easily, even though it's not
recommended, as it could cause inconsistencies between the installations of users
as tachiyomi doesn't prompt for an extension update unless the version code gets bumped.

To change it you can remove the commend at the beginning of the
[build_release](./.github/workflows/build_release.main.kts) file

Pushes that only contain updates to markdown files will get ignored (see `pathsIgnore`).

You can update the changes to the workflow files by running the `:workflows:update` task.
This will download a
[kotlin cli compiler](https://kotlinlang.org/docs/command-line.html#install-the-compiler)
matching the `kotlin_target` version.

## Update Guide

The process of updating the template should be fairly easy, but will require
a minimum amount of manual intervention. If you decided to go with the fork route,
you can read more about what to do [here](https://gist.github.com/Chaser324/ce0505fbed06b947d962).

If you chose the template route:

With every release a `stripped-template.zip` file should be present in the release assets.
(you can produce your own by running the `:strip:template` task).
The zip contains the full template repository matching the release commit,
filtered to include only some files (like `.kt` and `.kts`),
excluding directories like `.idea` and `.git`.

Ideally you should delete the files and directories that are both in `stripped-template.zip`
and in your repo (like `libs` and `multisrc`) and replace them with the ones in the
stripped-template.

You can clone the repo in another directory to have a clean slate and to also test if
everything works.
