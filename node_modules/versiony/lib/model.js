const {getVersion} = require('./utils')

/**
 * Empty the given array (without overriding the reference to it)
 * @param {*[]} arr - the array to empty
 */
function emptyArray (arr) {
  arr.splice(0, arr.length)
}

module.exports = function () {
  const increments = {
    major: null,
    minor: null,
    patch: null,
    preRelease: null,
    reset () {
      this.major = this.minor = this.patch = this.preRelease = null
    }
  }

  const parts = Object.assign({}, increments)

  const shouldSet = function (name, value) {
    if (value !== undefined) {
      parts[name] = value
      increments[name] = null
    } else {
      increments[name] = true
      parts[name] = null
    }
  }

  const prepareValue = function (name, version) {
    if (increments[name]) {
      if (name === 'preRelease') {
        // For a pre-release tag, we only support bumping the last section in a dot-delimited tag
        // if one doesn't exist, we default to start with "beta.0" (@job13er 2017-06-15)
        if (!version[name]) {
          version[name] = 'beta.0'
        }
        const parts = version[name].split('.')
        parts[parts.length - 1] = parseInt(parts[parts.length - 1], 10) + 1
        version[name] = parts.join('.')
      } else {
        version[name]++
      }
    }
    if (parts[name] !== null) {
      version[name] = parts[name]
    }
  }

  const files = []

  const model = {
    version: {major: 0, minor: 0, patch: 0, preRelease: null},
    parts,
    increments,
    reset () {
      emptyArray(files)
      parts.reset()
      increments.reset()
    },

    major (value) {
      shouldSet('major', value)
    },

    minor (value) {
      shouldSet('minor', value)
    },

    patch (value) {
      shouldSet('patch', value)
    },

    preRelease (value) {
      shouldSet('preRelease', value)
    },

    toString () {
      const versionString = `${this.version.major}.${this.version.minor}.${this.version.patch}`
      if (this.hasPreRelease()) {
        return `${versionString}-${this.version.preRelease}`
      }
      return versionString
    },

    file (filename) {
      if (!this.hasFile(filename)) {
        files.push(filename)
        return true
      }
    },

    hasFile (filename) {
      return !!~files.indexOf(filename)
    },

    hasPreRelease () {
      return Boolean(this.version.preRelease)
    },

    files () {
      return files
    },

    set (value) {
      if (typeof value === 'string') {
        value = getVersion(value)
      }

      parts.reset()

      this.version = value
    },

    get () {
      prepareValue('major', this.version)
      prepareValue('minor', this.version)
      prepareValue('patch', this.version)
      prepareValue('preRelease', this.version)

      return this.toString()
    }
  }

  return model
}
