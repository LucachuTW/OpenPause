# Contributing to OpenPause

First off, thank you for considering contributing to OpenPause! It's people like you that make OpenPause a great tool for digital wellbeing.

## Where do I go from here?

If you've noticed a bug or have a feature request, make sure to check our [Issues](https://github.com/yourusername/OpenPause/issues) to see if someone else has already created a ticket. If not, go ahead and make one!

## Fork & create a branch

If this is something you think you can fix, then fork OpenPause and create a branch with a descriptive name.

A good branch name would be (where issue #325 is the ticket you're working on):

```sh
git checkout -b 325-add-dark-mode
```

## Get the test suite running

Make sure your changes don't break the build. Run the following command before submitting a Pull Request:

```sh
./gradlew test assembleDebug lintDebug
```

## Implement your fix or feature

At this point, you're ready to make your changes! Feel free to ask for help if you need it.

## Make a Pull Request

At this point, you should switch back to your master branch and make sure it's up to date with OpenPause's main branch:

```sh
git remote add upstream https://github.com/yourusername/OpenPause.git
git checkout main
git pull upstream main
```

Then update your feature branch from your local copy of main, and push it!

```sh
git checkout 325-add-dark-mode
git rebase main
git push --set-upstream origin 325-add-dark-mode
```

Finally, go to GitHub and create a Pull Request. Please use the provided PR template and fill it out completely.

## Code Style

- We use standard **Kotlin coding conventions**.
- UI should be built using **Jetpack Compose** and **Material Design 3**.
- Keep `ViewModels` clean; business logic belongs in the `domain` layer.
