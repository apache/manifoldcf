/**
 * Utility module for working with json files
 */

const fs = require('fs')

module.exports = {

  /**
   * Get the version info out of a json object (or string version)
   * @param {String|Object} json - the source to get the version out of
   * @returns {Number[]} an array of major, minor, patch versions
   */
  getVersion (json) {
    if (typeof json === 'string') {
      json = {version: json}
    }

    if (!json.version) {
      return null
    }

    const parts = json.version.split('-')
    const versionArray = parts[0].split('.')

    const defaults = {
      major: 0,
      minor: 0,
      patch: 0
    }

    return Object.assign(defaults, {
      major: parseInt(versionArray[0]),
      minor: parseInt(versionArray[1]),
      patch: parseInt(versionArray[2]),
      preRelease: parts[1]
    })
  },

  /**
   * Read in a JSON file as a pojo
   * @param {String} filename - the name of the file to read
   * @returns {Object} the parsed JSON object
   */
  readJsonFile (filename) {
    return JSON.parse(fs.readFileSync(filename))
  },

  /**
   * Write out JSON data to a file
   * @param {String} filename - the filename to write to
   * @param {String|Object} contents - the JSON contents
   * @param {Number} [indent=2] - the number of spaces to indent
   * @param {Boolean} eofNewline - true to add a newline at the end of the file
   */
  writeJsonFile (filename, contents, indent = 2, eofNewline) {
    if (typeof contents !== 'string') {
      contents = JSON.stringify(contents, null, indent)
    }

    if (eofNewline) {
      contents += '\n'
    }

    fs.writeFileSync(filename, contents)
  }
}
