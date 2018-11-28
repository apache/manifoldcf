[ci-img]: https://img.shields.io/travis/ciena-blueplanet/versiony.svg "Travis CI Build Status"
[ci-url]: https://travis-ci.org/ciena-blueplanet/versiony

[cov-img]: https://img.shields.io/coveralls/ciena-blueplanet/versiony.svg "Coveralls Code Coverage"
[cov-url]: https://coveralls.io/github/ciena-blueplanet/versiony

[npm-img]: https://img.shields.io/npm/v/versiony.svg "NPM Version"
[npm-url]: https://www.npmjs.com/package/versiony

# versiony <br> [![Travis][ci-img]][ci-url] [![Coveralls][cov-img]][cov-url] [![NPM][npm-img]][npm-url]

Node.js module to increment version number for your code/module

## Installation

```
npm install versiony
```

If you want to use the CLI, install

```
npm install -g versiony-cli
```

## Usage

Versiony can read code versions from json files containing either the keys "major", "minor", "patch"
```js
{
  "major": 0,
  "minor": 1,
  "patch": 1
}
```

or with a "version" key, just like package.json

```js
{
  "name": "versiony",
  "version": "0.1.1"
}
```

Example

version.json
```js
{
  "major": 0,
  "minor": 0,
  "patch": 1
}
```

test.js
```js
var versiony = require('./index')

versiony

  .minor()                // will cause the minor version to be bumped by 1
  .from('version.json')   // read the version from version.json
  .to()                   // write the version to the source file (package.json)
                          // with the minor part bumped by 1
  .to('bower.json')       // apply the same version
  .to('package.json')     // apply the same version
  .end()                  // display info on the stdout about modified files
```

The above code will cause the version 0.1.1 to be written to all 3 files, if all are found.

In the case `versiony` does not find a file that is specified in the `to()` call, it just skips it.

## Other examples

Set the patch version number to 4. That is, for a current version 1.0.2 will write 1.0.4

```js
versiony
  .from('package.json')
  .patch(4)
  .to()
```


Take the version in `version.json`. For this version, set the major version to 1, then write this to `package.json`
and `bower.json`. So, for `version.json` containing "4.5.6" the script below will write 1.5.6 to `package.json` and
`bower.json`. If you also want to update `version.json`, simply add a `.to('version.json')` call anywhere after the
`.major(1)`

```js
versiony
  .from('version.json')
  .major(1)
  .to('package.json')
  .to('bower.json')
  .end()
```

Copy the version from one file to another

```js
  versiony.from('version.json').to('package.json')
```

Release a new major version
```js
versiony
  .from('version.json')
  .major()
  .minor(0)
  .patch(0)
  .to()               // also write to the source file (the one specified in from() )
  .to('bower.json')
  .to('package.json')
  .end()
```

Which is equivalent to
```js
versiony
  .from('version.json')
  .newMajor()
  .to()
  .to('bower.json')
  .to('package.json')
  .end()
```

The flow in the above script is the following

 - take the version from version.json
 - apply the modifications (increment major, set minor and patch to 0)
 - write the new version to the source file (version.json)
 - write the new version to bower.json
 - write the new version to package.json


# API

Each of the methods below, except `get()` and `end()` return the `versiony` object.


## `major()`

Causes the current major version to be incremented by 1

## `major(value)`

Sets the current major version to have the specified value

## `minor()`

Causes the current minor version to be incremented by 1

## `minor(value)`

Sets the current minor version to have the specified value

## `patch()`

Causes the current patch version to be incremented by 1

## `patch(value)`

Sets the current patch version to have the specified value

## `preRelease()`

Causes the last section of the dot-delimited pre-release tag to be incremented by 1
(e.g. `1.2.3-beta.3` becomes `1.2.3-beta.4`)

## `preRelease(value)`

Sets the current pre-release version to have the specified value. If the value is falsy (but not `undefined`),
then any existing pre-release tag will be removed (e.g. `1.2.3-beta.3` becomes `1.2.3`)

> **NOTE**
Calling `major()` twice does not cause the increment to be applied twice. It is only applied once.
Same for `minor()`, `patch()`, and `preRelease()`

## `newMajor()`

Equivalent to calling `major().minor(0).patch(0).preRelease('')`

## `newMinor()`

Equivalent to calling `minor().patch(0).preRelease('')`

## `from(file)`

Sets the current version. This clears any values set using `major(value)`, `minor(value)` and `patch(value)`.
It does not clear increments set with `major()`, `minor()` and `patch()`

## `with(file)`

Same as `from(file)`, but also writes the version back to file, if previously increments have been used.

Example - increment the major version

```js
versiony
  .major()
  .with('package.json')
```

## `to(json_file)`

Causes the version to be written to the specified file. If the specified json file has a "version" key,
the version will be written to that key. If it has "major", "minor" and "patch" keys, the value will be written to those.

## `to()`

Writes the value to the source file (the file that was used with .from() or .with() ).
If no initial file specified, it simply returns.

## `version(v: String/Array)`

Sets the current version. This clears any value set using `major(value)`, `minor(value)`, `patch(value)`,
as well as their incrementive forms ( `major()`, `minor()`, `patch()` )

Example: sets version 4.0.0

```js
versiony
  .major()
  .version('4.0.0')
  .to('package.json')
```

is equivalent to

```js
  versiony
    .version('4.0.0')
    .to('package.json')
```

## `end()`

Clears any version and outputs the files that have been updated. Calling it is totally optional.
Returns an object with info about the version and the changed files.

```js
versiony.
  version('4.5.6')
  .major()
  .to('package.json')

var info = versiony.end()
console.log(info.version)
console.log(info.files)
```

### `get()`

Returns the current version.

```js
var v = versiony
          .version('1.0.0')
          .patch()
          .get()

console.log(v)  // '1.0.1'
```

# CLI

## Usage

Install with

```
npm install -g versiony-cli
```

Example: increment the minor version

```
versiony package.json --minor
```

The source file defaults to package.json, so you can easily skip it, if that's what you're using.

Example: set the major version to 3 (in package.json)

```
versiony --major=3
```

Example: set specific version
```
versiony --version=1.2.3
```

Example: update multiple files
```
versiony --patch --to=package.json,bower.json
```

Example: release new major update (bumps major and sets minor and patch to 0)
```
versiony --newmajor
```

Example: release new minor update (bumps minor and sets patch to 0)
```
versiony --newminor
```
