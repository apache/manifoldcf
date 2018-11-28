# require-bower-files

[![NPM version](https://img.shields.io/npm/v/require-bower-files.svg)](https://www.npmjs.com/package/require-bower-files)
[![Build Status](https://travis-ci.org/shinnn/require-bower-files.svg?branch=master)](https://travis-ci.org/shinnn/require-bower-files)
[![Build status](https://ci.appveyor.com/api/projects/status/p4agdotoyrks5qov?svg=true)](https://ci.appveyor.com/project/ShinnosukeWatanabe/require-bower-files)
[![Coverage Status](https://img.shields.io/coveralls/shinnn/require-bower-files.svg)](https://coveralls.io/r/shinnn/require-bower-files)
[![Dependency Status](https://img.shields.io/david/shinnn/require-bower-files.svg?label=deps)](https://david-dm.org/shinnn/require-bower-files)
[![devDependency Status](https://img.shields.io/david/dev/shinnn/require-bower-files.svg?label=devDeps)](https://david-dm.org/shinnn/require-bower-files#info=devDependencies)

A [Node](https://nodejs.org/) module to `require` [bower](http://bower.io/) components all at once

```json
{
  "dependencies": {
    "jquery": "~2.1.4",
    "lodash": "~3.8.0"
  }
}
```

```javascript
const requireBowerFiles = require('require-bower-files');

requireBowerFiles();
//=> [Function: jQuery, Function: Lodash]
```

## Installation

[Use npm](https://docs.npmjs.com/cli/install).

```
npm install require-bower-files
```

## API

```javascript
const requireBowerFiles = require('require-bower-files');
```

### requireBowerFiles([*options*])

*options*: `Object` (Directly passed to [bower-files constructor](https://github.com/ksmithut/bower-files#options) and [filter function](https://github.com/ksmithut/bower-files#libfilter-options-))  
Return: `Array`

It gets bower components paths with [bower-files](https://github.com/ksmithut/bower-files#options), [`require`](https://nodejs.org/api/globals.html#globals_require)s them and returns an array of the loaded modules.

`ext` option is `js` by default to filter out non-JS files.

## License

Copyright (c) 2014 - 2015 [Shinnosuke Watanabe](https://github.com/shinnn)

Licensed under [the MIT License](./LICENSE).
