/*!
 * is-symlink-sync | MIT (c) Shinnosuke Watanabe
 * https://github.com/shinnn/is-symlink-sync
*/
'use strict';

var fs = require('graceful-fs');
var tryit = require('tryit');

module.exports = function isSymlinkSync(filePath) {
  if (typeof filePath !== 'string') {
    throw new TypeError(
      filePath +
      ' is not a string. Argument to is-symlink-sync must be a file path.'
    );
  }

  var result;
  var isSymbolicLink;

  tryit(function() {
    isSymbolicLink = fs.lstatSync(filePath).isSymbolicLink();
  }, function(err) {
    result = !err && isSymbolicLink;
  });

  return result;
};
