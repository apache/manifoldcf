/**
 * Utility module for logging messages to the console
 */

module.exports = {

  /**
   * Log an error message
   * @param {String} message - the message to log
   */
  error (message) {
    console.error(message)
  },

  /**
   * Log an informational message
   * @param {String} message - the message to log
   */
  log (message) {
    console.log(message)
  },

  /**
   * Log a warning message
   * @param {String} message - the message to log
   */
  warn (message) {
    console.warn(message)
  }
}
