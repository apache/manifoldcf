'use strict';

module.exports = FileFilter;

var path = require('path');
var arrify = require('arrify');
var camelcase = require('camelcase');
var minimatch = require('minimatch');
var assign = require('object-assign');
var BowerFiles = require('./bower-files');

function FileFilter(bowerFiles, options) {
  if (this instanceof BowerFiles) { return; }
  this.bowerFiles = bowerFiles;
  this.options = assign({
    ext: [],
    match: [],
    join: {},
    camelCase: true,
    cwd: this.bowerFiles.cwd
  }, this.bowerFiles._fileFilterConfig, options);
}

FileFilter.prototype = {
  _filter: function getFilterInstance() {
    if (this instanceof BowerFiles) {
      return new FileFilter(this);
    }
    return new FileFilter(this.bowerFiles, this.options);
  },
  _boolOption: function (name, val, defaultVal) {
    var self = this._filter();
    self.options[name] = typeof val === 'boolean' ? val : defaultVal;
    return self;
  },
  join: function join(joinDef) {
    var self = this._filter();
    assign(self.options.join, joinDef);
    return self;
  },
  match: function match(val) {
    var self = this._filter();
    self.options.match = self.options.match.concat(arrify(val));
    return self;
  },
  ext: function ext(val) {
    var self = this._filter();
    if (val === false) { return self; }
    if (val === true) {
      self.options.ext = true;
    } else {
      self.options.ext = self.options.ext.concat(arrify(val));
    }
    return self;
  },
  relative: function relative(val) {
    var self = this._filter();
    self.options.relative = val ? val : self.options.cwd;
    return self;
  },
  dev: function dev(val) {
    var self = this._filter();
    if (val === 'after') {
      self.options.dev = 'after';
      return self;
    }
    return this._filter()._boolOption('dev', val, true);
  },
  self: function self(val) {
    return this._filter()._boolOption('self', val, true);
  },
  main: function main(val) {
    return this._filter()._boolOption('main', val, true);
  },
  camelCase: function camelCase(val) {
    return this._filter()._boolOption('camelCase', val, true);
  },
  fileListProps: function fileListProps(val, findFirst) {
    var self = this._filter();
    self.options.fileListProps = arrify(val);
    if (typeof findFirst !== 'undefined') {
      self.options.firstFileListProp = findFirst;
    }
    return self;
  },
  ignoreListProps: function ignoreListProps(val) {
    var self = this._filter();
    self.options.ignoreListProps = arrify(val);
    return self;
  },
  filter: function (options) {
    var self = this._filter();
    assign(self.options, options);
    return self.files;
  },
  getDependencies: function () {
    var self = this._filter();
    var options = self.options;
    return self.bowerFiles.component.getDependencies(clearUndefined({
      self: options.self,
      dev: options.dev,
      main: options.main
    }));
  }
};

Object.defineProperties(FileFilter.prototype, {
  files: {get: function () {
    var self = this._filter();
    var files = self.getDependencies().reduce(function (depFiles, component) {
      return depFiles.concat(component.files(self.options));
    }, []);
    return processFiles(files, self.options);
  }},
  deps: {get: function () {
    var self = this._filter();
    var options = self.options;
    return self.getDependencies().reduce(function (finalDeps, component) {
      var name = options.camelCase ? camelcase(component.name) : component.name;
      finalDeps[name] = processFiles(component.files(self.options), options);
      return finalDeps;
    }, {});
  }},
  depsArray: {get: function () {
    var self = this._filter();
    var options = self.options;
    return self.getDependencies().map(function (component) {
      return {
        name: options.camelCase ? camelcase(component.name) : component.name,
        files: processFiles(component.files(self.options), options)
      };
    });
  }}
});

function processFiles(files, options) {
  files = files
    .filter(unique())
    .filter(patternMatch(options.match, options.cwd));
  if (options.relative) {
    files = makeRelative(files, options.relative);
  }
  files = extensionSplit(files, options.ext, options.join);
  return files;
}

function unique() {
  return function(item, index, array) {
    return array.indexOf(item) === index;
  };
}

function patternMatch(patterns, cwd) {
  if (!patterns.length) { return function (val) { return true; }; }
  return function (file) {
    file = file.replace(cwd + path.sep, '');
    return arrify(patterns).every(function (pattern) {
      return minimatch(file, pattern);
    });
  };
}

function extensionSplit(files, exts, join) {
  if (!exts || exts.length === 0) { return files; }
  // Split files up by extension
  files = files.reduce(function (filesObj, file) {
    var ext = path.extname(file).substr(1);
    filesObj[ext] = filesObj[ext] || [];
    filesObj[ext].push(file);
    return filesObj;
  }, {});
  // Get things from the join option
  Object.keys(join).forEach(function (joinExt) {
    var extensions = join[joinExt];
    files[joinExt] = files[joinExt] || [];
    files[joinExt] = extensions.reduce(function (jointArr, ext) {
      if (!files[ext]) { return jointArr; }
      return jointArr.concat(files[ext]);
    }, files[joinExt]);
  });
  // if extension is an array of strings, get the extensions given
  if (exts.length) {
    files = arrify(exts).reduce(function (extArray, ext) {
      return extArray.concat(files[ext] || []);
    }, []);
  }
  return files;
}

function makeRelative(files, cwd) {
  return files.map(function(file) {
    return path.relative(cwd, file);
  });
}

function clearUndefined(obj) {
  Object.keys(obj).forEach(function (key) {
    if (typeof obj[key] === 'undefined') { delete obj[key]; }
  });
  return obj;
}
