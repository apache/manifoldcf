/*!
 * require-bower-files | MIT (c) Shinnosuke Watanabe
 * https://github.com/shinnn/require-bower-files
*/
'use strict';

const bowerFiles = require('bower-files');

module.exports = function requireBowerFiles(options) {
  options = Object.assign({ext: 'js'}, options);
  return bowerFiles(options).filter(options).map(require);
};
