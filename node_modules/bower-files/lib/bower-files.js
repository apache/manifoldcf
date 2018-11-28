'use strict';

module.exports = BowerFiles;

var assert      = require('assert');
var path        = require('path');
var util        = require('util');
var BowerConfig = require('bower-config');
var isAbsolute  = require('is-absolute');
var assign      = require('object-assign');
var untildify   = require('untildify');
var Component   = require('./component');
var FileFilter  = require('./file-filter');

function BowerFiles(options) {
  if (!(this instanceof BowerFiles)) { return new BowerFiles(options); }

  options = normalizeOptions(options);

  this.component = new Component({
    dir: options.cwd,
    dependencyDir: options.dir,
    json: options.json,
    componentJson: options.componentJson,
    overrides: options.overrides,
    isRoot: true
  });

  this.cwd = options.cwd;
  // TODO deprectate
  this._fileFilterConfig = {
    camelCase: options.camelCase
  };

  Object.defineProperties(this, {
    _config: {
      get: util.deprecate(function () {
        return options;
      }, 'bowerFiles._config: Access to _config is deprecated')
    },
    _component: {
      get: util.deprecate(function () {
        return this.component;
      }.bind(this), 'bowerFiles._component: Access to _component is deprecated')
    }
  });

  FileFilter.call(this);
}
util.inherits(BowerFiles, FileFilter);


BowerFiles.old = util.deprecate(function (options) {
  options = assign({ext: true, join: {}}, options);
  return new BowerFiles(options)
    .self(Boolean(options.self))
    .dev(Boolean(options.dev))
    .ext(options.ext)
    .join(options.join)
    .files;
}, 'BowerFiles.old: This is an old api. Read the docs on the new api');


function normalizeOptions(options) {
  if (options && typeof options.camelCase !== 'undefined') {
    console.log('bowerFiles.options.camelCase: Use .camelCase(true).');
  }
  options = assign({
    cwd: process.cwd(),
    json: 'bower.json',
    overrides: {},
    componentJson: '.bower.json',
    camelCase: true,
    dir: null
  }, options);

  assert(
    isAbsolute(options.cwd || ''),
    'options.cwd must be an absolute path'
  );

  if (!options.dir) {
    var bowerrc = new BowerConfig(options.cwd).load().toObject();
    options.cwd = untildify(bowerrc.cwd);
    options.dir = untildify(bowerrc.directory);
  }
  options.dir = path.resolve(options.cwd, options.dir);

  return options;
}


