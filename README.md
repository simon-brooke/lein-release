# Leiningen Release Plug-in

## Do not use this, or fork it. For the present it is broken; I forked it because I was fed up with how broken lein-release was, and thought I'd have a go at fixing it; but upstream may not have been the correct repository to fork.

The release plug-in automatically manages your project's version string and deploys the built artifact for you.  Note that your project must follow the maven conventions for version strings in order for lein-release to operate: http://mojo.codehaus.org/versions-maven-plugin/version-rules.html

The plug-in performs the following steps:

1. Modify the project.clj to drop the "-SNAPSHOT" suffix
2. Add the project.clj to the SCM system
3. Commit the project.clj to the SCM system
4. Tag the project with `projectName-version`
5. If the project jar file does not exist, it builds it with `lein jar` and `lein pom`
6. Performs a Deploy (see the Deploy section below)
7. Increments the project minor version number and re-adds the "-SNAPSHOT" suffix
8. Add the project.clj to the SCM system
9. Commit the project.clj to the SCM system

# Usage

See "Leiningen: Installing Plugins":https://github.com/technomancy/leiningen/wiki/Upgrading#plugins

Add `[lein-release "1.0.5"]` to the `:user -> :plugins` section of your `$HOME/.lein/profiles.clj`:

```clojure
{:user {:plugins [[lein-release "1.0.5"]]}}
```

For Leiningen 1:

    lein plugin install lein-release/lein-release 1.0.5

To perform a release:

    lein release

# Configuration

The plug-in supports a `:lein-release` map in the project.clj

### :scm

```clojure
:lein-release {:scm :git}
```

This can be used to specify the SCM (version control) system.  The release plug-in attempts to auto-detect the version control system by inspecting the current working directory (eg, for the `.git` directory).  If this does not work for your project you can specify the SCM system explicitly.

### :deploy-via

```clojure
:lein-release {:deploy-via :clojars}
```

This can be used to explicitly specify the deployment strategy that will be used.  The currently supported values for this are:

* `:clojars`
* `:lein-deploy`
* `:lein-install`
* `:shell`

The release plugin attempts to detect whether to use `:lein-deploy` or `:lein-install` by inspecting the project.clj.  If a `:repositories` key is present in the project.clj `:lein-deploy` will be used.  Otherwise `:lein-install` will be used.  `:clojars` and `:shell` will only be used if it is explicitly specified in the project.clj.

If `:shell` is specified, the value of the `:shell` key should be an array of command line arguments:

```clojure
:lein-release {:deploy-via :shell
                    :shell ["s3cmd" "put" "target/*.jar" "s3://blueant.com/deploy"]}
```

### :build-uberjar

This triggers a `lein uberjar` to be run in addition to the `lein jar`.


## Example Configuration

```clojure
 (defproject org.clojars.relaynetwork/clj-avro "1.0.9-SNAPSHOT"
      :description "Avro Wrapper for Clojure"
      :lein-release {:deploy-via :clojars}
      :local-repo-classpath true
      :dependencies [[org.clojure/clojure                   "1.2.0"]
                     [org.apache.avro/avro                  "1.6.1"]
                     [org.clojure/clojure-contrib           "1.2.0"]
                     [org.clojars.kyleburton/clj-etl-utils  "1.0.41"]])
```

# Deploy

The deployment strategy is determined by the following:

* if `:deploy-via` is specified in the configuration, its value is used
* if the project.clj has a `:repositories` setting, then `:lein-deploy` is used
* otherwise `:lein-install` is used

Deployment to clojars is handled by shelling out and running:

pre. lein deploy clojars

Deployment via Leiningen is handled by shelling out (for a deploy or install respectively).

# Environment Variable: RELEASE_QUALIFIER

If set, this will be a suffix appended to the version of the released jar.  We have used this in the past to create custom releases of projects we don't control until patches are accepted or bugs are fixed (a fork), and in cases where we need to create either release candidates or incremental patches.

In the fork case, we would often add a `-rn` suffix:

```
 RELEASE_QUALIFIER=-rn lein release
```

In the latter case (release candidates or incremental patch):

```
RELEASE_QUALIFIER=-rc1 lein release
```
or:

```
RELEASE_QUALIFIER=.1 lein release
```

# Supported SCM Systems

Currently only `git` support is implemented.  Provisions have been made in the plug-in to support more SCM systems in the future.  Patches are welcome!

# Limitations

The plug-in uses simple heuristics (regexes!) to modify the version string in the project.clj.  If you have multiple lines (or comments) that look like a defproject it may not be able to succeed.  This approach was taken in order to not rewrite the entire project.clj file and thus loose things like formatting, indentation or comments.

# Changes

### 1.0.6 2014-10-22T17:50:44Z

Change `clojars` deploy-via to use `lein deploy clojars` instead of scp.

# Authors

Kyle Burton <kyle.burton@gmail.com>
Paul Santa Clara

# License

Copyright (C) Relay Network LLC

Distributed under the Eclipse Public License, the same as Clojure.
