# is-symlink-sync

[![NPM version](https://img.shields.io/npm/v/is-symlink-sync.svg)](https://www.npmjs.com/package/is-symlink-sync)
[![Build Status](https://travis-ci.org/shinnn/is-symlink-sync.svg?branch=master)](https://travis-ci.org/shinnn/is-symlink-sync)
[![Build status](https://ci.appveyor.com/api/projects/status/1e8sfy6cs9dxrs5j?svg=true)](https://ci.appveyor.com/project/ShinnosukeWatanabe/is-symlink-sync)
[![Coverage Status](https://img.shields.io/coveralls/shinnn/is-symlink-sync.svg)](https://coveralls.io/r/shinnn/is-symlink-sync)
[![Dependency Status](https://img.shields.io/david/shinnn/is-symlink-sync.svg?label=deps)](https://david-dm.org/shinnn/is-symlink-sync)
[![devDependency Status](https://img.shields.io/david/shinnn/is-symlink-sync.svg?label=devDeps)](https://david-dm.org/shinnn/is-symlink-sync#info=devDependencies)

Synchronously check if a file is a [symbolic link](https://en.wikipedia.org/wiki/Symbolic_link)

```javascript
const isSymlinkSync = require('is-symlink-sync');

isSymlinkSync('path/to/symlink'); //=> true
isSymlinkSync('path/to/non-symlink'); //=> false
```

## Installation

[Use npm.](https://docs.npmjs.com/cli/install)

```
npm install is-symlink-sync
```

## API

```javascript
const isSymlinkSync = require('is-symlink-sync');
```

### isSymlinkSync(*filePath*)

*filePath*: `String`  
Return: `Boolean`

It returns `true` if the file exists and is a symbolic link, otherwise `false`.

Only when the argument is not a string, it throws an error.

```javascript
isSymlinkSync(123); // throws a type error
```

## License

Copyright (c) 2015 [Shinnosuke Watanabe](https://github.com/shinnn)

Licensed under [the MIT License](./LICENSE).
