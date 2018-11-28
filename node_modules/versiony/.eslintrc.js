module.exports = {
  extends: ['standard'],
  parser: 'babel-eslint',
  plugins: [
    'mocha'
  ],
  rules: {
    camelcase: 'error',
    complexity: ['error', 5],
    'max-len': ['error', 120],
    'mocha/handle-done-callback': 'error',
    'mocha/no-exclusive-tests': 'error',
    'mocha/no-global-tests': 'error',
    'mocha/no-pending-tests': 'error',
    'mocha/no-skipped-tests': 'error',
    'no-unused-expressions': 'error',
    'object-curly-spacing': ['error', 'never'],
    'valid-jsdoc': [
      'error',
      {
        prefer: {
          'virtual': 'abstract',
          'extends': 'augments',
          'constructor': 'class',
          'const': 'constant',
          'defaultvalue': 'default',
          'desc': 'description',
          'host': 'external',
          'fileoverview': 'file',
          'overview': 'file',
          'emits': 'fires',
          'func': 'function',
          'method': 'function',
          'var': 'member',
          'arg': 'param',
          'argument': 'param',
          'return': 'returns',
          'exception': 'throws'
        },
        requireReturn: false
      }
    ]
  }
}
