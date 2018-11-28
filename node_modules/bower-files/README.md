# bower-files

[![NPM version](http://img.shields.io/npm/v/bower-files.svg?style=flat)](https://www.npmjs.org/package/bower-files)
[![Dependency Status](http://img.shields.io/david/ksmithut/bower-files.svg?style=flat)](https://david-dm.org/ksmithut/bower-files)
[![Dev Dependency Status](http://img.shields.io/david/dev/ksmithut/bower-files.svg?style=flat)](https://david-dm.org/ksmithut/bower-files#info=devDependencies&view=table)
[![Code Climate](http://img.shields.io/codeclimate/github/ksmithut/bower-files.svg?style=flat)](https://codeclimate.com/github/ksmithut/bower-files)
[![Build Status](http://img.shields.io/travis/ksmithut/bower-files/master.svg?style=flat)](https://travis-ci.org/ksmithut/bower-files)
[![Coverage Status](http://img.shields.io/codeclimate/coverage/github/ksmithut/bower-files.svg?style=flat)](https://codeclimate.com/github/ksmithut/bower-files)

Help you dynamically include your bower components into your build process.

**The Problem**

Bower is a great tool to bring in your front-end dependencies (and their
dependencies) to your project. But if you want them to be included in your build
process, you need to manually enter them in to your build process. If you add
or remove dependencies, you need to modify your build process configuration
files.

**The Solution**

`bower-files` aims to simplify your build process setup by dynamically getting
the library files for you to include in whatever build process you use. It
splits up the files by extension, and puts them in the order they need to be in,
in order to work correctly in the browser.

## 3.x

There are breaking changes in 3.x. A few features were requested, but with the
way that the code was organized, it was going to be pretty difficult and it
would make the codebase even more complicated. In the end, I refactored most
every piece to follow a more modular approach. I am much happier with the code
base than I was, but it's not perfect. I'm going to be slowly refactoring litte
pieces here and there, but the api should not change much from here.

For those of you who want the old 2.x api, just use it the same way, but adding
a `.old` before the function call.

```javascript
var lib = require('bower-files').old(options);
```

With that you get the benefit of using the new modular code, and it still passes
all of the old tests (which had 100% api and code coverage).
[2.x Docs](README-2.x.md)

## Installation

```bash
npm install bower-files --save-dev
```

## Usage

gulp example...

```javascript
var gulp   = require('gulp');
var concat = require('gulp-concat');
var uglify = require('gulp-uglify');
var lib    = require('bower-files')();

gulp.task('default', function () {
  gulp.src(lib.ext('js').files)
    .pipe(concat('lib.min.js'))
    .pipe(uglify())
    .pipe(gulp.dest('public/js'));
});
```

or a grunt example...

```javascript

var lib = require('bower-files')();

module.exports = function (grunt) {

  grunt.initConfig({
    pkg: grunt.file.readJSON('package.json'),
    uglify: {
      dist: {
        files: {
          'build/lib.min.js': lib.ext('js').files
        }
      }
    },
    cssmin: {
      dist: {
        files: {
          'build/lib.min.css': lib.ext('css').files
        }
      }
    }
  });

  grunt.loadNpmTasks('grunt-contrib-uglify');
  grunt.loadNpmTasks('grunt-contrib-cssmin');

  grunt.registerTask('default', ['uglify', 'cssmin']);

};
```

or via the CLI (`node-modules/.bin` needs to be in your `PATH`):

```sh
$ bower-files js css
```

*Other Solutions*

There are other modules that do this same thing, but in different ways:

- [`main-bower-files`](https://github.com/ck86/main-bower-files)
- [`gulp-bower-files`](https://www.npmjs.org/package/gulp-bower-files)

## Options

The following gives you an instance of your bower files.

```javascript
var lib = require('bower-files')(/* options */);
```

Those options are as follows:

#### `options.cwd` {String}

Default: `process.cwd()`

Your current working directory where your `bower.json` lives. This is also where
it will start looking at `.bowerrc`. **MUST** be an absolute path.

#### `options.json` {String}

Default: `'bower.json'`

The relative path to your `bower.json`. This is relative to your `options.cwd`.

#### `options.componentJson` {String}

Default: `'.bower.json'`

When you run `bower install jquery` it installs the `jquery` component into a
folder `bower_components/jquery/`. In that directory, there is a `.bower.json`
file put there by bower, which essentially is a copy of the other `bower.json`,
but it's what is officially used by bower. If for some reason you are using a
different package manager that uses a different file, then you can change it
with this option. Otherwise, leave it as is.

#### `options.overrides` {Object}

Default: `{}`

An overrides object to override specific package main files. Occasionally,
you'll find a bower component that has no defined `main` property. So here, you
pass in an object that looks like this:

```javascript
var lib = require('bower-files')({
  overrides: {
    jQuery: {
      main: './dist/jquery.min.js',
      dependencies: {}
    }
  }
});
```

Note that you can also add this POJO into `bower.json` in `JSON` form, but if
you specify an overrides directly when calling `bower-files`, it will override
the `bower.json` version.

#### `options.dir` {String}

Default: `'bower_components'`

The directory that your bower_components directory is set. Note that this module
will automatically read your `.bowerrc` file, so if you already have it being
set there, you don't need to set this option. It also follows the same
`.bowerrc` rules that [bower
follows](http://bower.io/docs/config/#placement--order)

#### `options.camelCase` {Boolean}

Default: `true`

When you get a dependency hash using the `.deps`, by default, it will return
the components in camelCase. So if you have `angular-route` as a dependency,
it will be returned in the dependency hash as `angularRoute`. To prevent this
from happening, pass false to this option. Example:

```javascript
var lib = require('bower-files')({camelCase: false}).deps;

lib['angular-route']; // instead of lib.angularRoute
```

## API

Getting the files and filtering through them can be a pain without this module.
This API is designed to be easy to understand. If you don't like it, PRs are
welcome.

First, you need your BowerFiles instance:

```javascript
var lib = require('bower-files')();
```

At this point, `bower-files` has gotten a list of all the components and their
dependencies. Now it's up to you to use the API to filter those files.

### Chainable Methods

The following methods are chainable. At the end, you will need to get the
`files` property to get the array of files.

```javascript
lib.files; // returns all of the files
```

You may also get the `deps` property, which will be an object hash with all of
the bower components as keys. Note that this is not guaranteed to be in the
correct order. If you need the correct order, use the next option.

```javascript
lib.deps;
/*
{
  jquery: [...],
  bootstrap: [...]
}
*/
```

You can also get an array of all of the dependencies with their names and files.

```javascript
lib.depsArray
/*
[
  {
    name: 'jquery',
    files: [...]
  },
  {
    name: 'bootstrap',
    files: [...]
  }
]
*/
```

#### `lib.self()`

By default the array of files you get only contain your dependencies defined in
the `dependencies` property in your `bower.json`. This allows you to also get
the files defined in the `main` property in your `bower.json`.

```javascript
lib.self().files;
lib.self().deps;
```

#### `lib.relative('/')`

Default: process.cwd()

Converts the file paths to be relative to the provided path, defaults to process.cwd()

```javascript
lib.relative(__dirname).files;
```

#### `lib.main(false)`

Default: true

This gives you the ability to remove main dependencies from the file list.

```javascript
lib.dev().main(false).files;
lib.dev().main(false).deps;
```

#### `lib.fileListProps(props, useOne)`

Default: ['main'], true

This lets you select which bower properties are used when generating a file list
for a component. For example, it seems that the bower standard for lists of
files used by tools like this one is to use the `files` property. You could use
`lib.fileListProps('files')` to utilize that property instead of the other one.
You could also select both: `lib.fileListProps(['files', 'main'])` and it would
remove the duplicate files that match both the main property definition and the
files property definition (it expands globs and uniquifies the lists)

The second option allows you to specify whether or not you want it to stop
trying to read additional file list properties when it finds one. For example,
you may specify `['files', 'main']`, and then `true` as the second option, and
all components with a `files` property would ignore the 'main' property.
If it's set to false, it would include all of the files in the 'main' property.

```javascript
lib.fileListProps('files').files;
lib.fileListProps(['files', 'main']).files;
```

#### `lib.ignoreListProps(props)`

Default []

This lets you select which bower properties are used when ignore globs of files.
This is to support the bower spec which allows you to use globs in the "files"
property, and "ignore" which would ignore groups of files that match the
"files" patterns.

```javascript
lib
  .fileListProps('files')
  .ignoreListProps('ignore')
  .files;
```

#### `lib.dev()`

This throws in your `devDependencies`. By default, they come before the normal
dependencies, but you can put them after by calling `lib.dev('after')`

```javascript
lib.dev().files;
lib.dev().deps;
```

#### `lib.ext('js')`

Gives you the files with the given extension(s). Accepts a string, array of
strings, or a boolean. The strings are extensions that you would like to filter
by (without the '.'). If you pass `true`, it will return an object whose
properties are all of the extensions.

```javascript
lib.ext('js').files; // Gets all .js files
lib.ext(['css', 'less']).files; // Gets all .css and .less files
lib.ext('css').ext('less').files; // Same as above
lib.ext(true).files; // Get an object like:
/*
{
  js: ['what.js', 'who.js'],
  css: ['when.css'],
  less: ['why.less']
}
*/
lib.ext('js').deps;
/*
{
  jquery: ['/path/to/jquery.js'],
  bootstrap: ['/path/to/bootstrap.js']
}
*/
lib.ext(true).deps;
/*
{
  jquery: { js: ['/path/to/jquery.js'] },
  bootstrap: {
    js: ['/path/to/bootstrap.js'],
    css: ['/path/to/bootstrap.css'],
    less: ['/path/to/bootstrap.less'],
    you get the picture...
  }
}
*/
```

#### `lib.match('!**/*.min.js')`

Allows you to glob match the files. Accepts a string, or array of strings. The
files have to match all of the given glob strings to make it through.

The matches are done relative to process.cwd(). So if you wanted to get all of
the bootstrap files by matching, you would use `'*/bootstrap/**'` as a pattern.

```javascript
// Gets all .js files that aren't .min.js files
lib.match('**/*.js').match('!**/*.min.js').files;
```

#### `lib.join({font: ['eot', 'woff', 'ttf', 'svg']})`

Allows you to join files of a certain extension into another extension. Accepts
an object as formed above and in the example below. It's only really useful if
you plan on using `.ext(true)` to split by extension, otherwise you can just use
`lib.ext(['eot', 'woff', 'ttf', 'svg']).files` to get the files you need.

```javascript
// Gets all the font files, from bootstrap for example
lib.join({font: ['eot', 'woff', 'ttf', 'svg']}).files
```

### Non-Chainable

There's really only one method, and it could be made chainable really easy, but
this method is really a catch all of all of the above methods.

#### `lib.filter({/* options */})`

The object given have all of the above options given through the chainable
methods. Below is a full example.

```javascript
lib.filter({
  self: true,
  dev: true,
  ext: 'js',
  match: ['!**/*.min.js'],
  join: {
    js: ['js', 'jsx']
  }
});
// chainable alternative
lib.self()
  .dev()
  .ext('js')
  .match('!**/*.min.js')
  .join({js: ['js', 'jsx']})
  .files;
```

The above returns all of the `.js` files, including the ones in your
`bower.json`, and the `devDependencies`, and they aren't `min.js` files, and it
joins all of the 'js' and 'jsx' files into the 'js' extension. That join could
be used in the extensions instead, but it's just there to show you how you
would do it.

## Questions

I know it's trying to solve for a lot of different use cases, so if you have any
questions about how to implement this in your specific setup, feel free to open
an issue. I usually get back to you pretty quickly, but usually no later than
24 hours, as long as I have access to email.

## Development

PRs welcome! Make sure you have an updated `npm` or the npm scripts won't work.
Tests that run fail if not all the tests pass, if you don't have 100% coverage,
or if the code doesn't pass jshint. I'd like to keep everything the same style,
so feel free to call me out on stuff. If you don't like the coding style, I'm
open for a discussion on it.

To run tests, run `npm test`.
