/**
 * Specify behavior for the .end() API interface
 */

const chai = require('chai')
const sinonChai = require('sinon-chai')
const sinon = require('sinon')

chai.use(sinonChai)
const expect = chai.expect

const versiony = require('../lib/index')
const logger = require('../lib/logger')
const utils = require('../lib/utils')

describe('.end()', function () {
  let sandbox, strip
  beforeEach(function () {
    strip = '---------------------------------------------'
    sandbox = sinon.sandbox.create()
    sandbox.stub(logger, 'log')
    sandbox.stub(utils, 'readJsonFile').returns({})
    sandbox.stub(utils, 'writeJsonFile')
    versiony.version('1.2.3-alpha.4')
  })

  afterEach(function () {
    sandbox.restore()
    versiony.model.reset()
  })

  describe('when called without params (and no files updated)', function () {
    let ret
    beforeEach(function () {
      sandbox.stub(versiony.model, 'reset')
      ret = versiony.end()
    })

    it('should log a strip', function () {
      expect(logger.log).to.have.been.calledWith(strip)
    })

    it('should log a done message', function () {
      expect(logger.log).to.have.been.calledWith('No file updated.')
    })

    it('should reset the model', function () {
      expect(versiony.model.reset).to.have.callCount(1)
    })

    it('return the correct info,', function () {
      expect(ret).to.eql({
        files: [],
        version: '1.2.3-alpha.4'
      })
    })
  })

  describe('when called without params (and some files updated)', function () {
    let ret
    beforeEach(function () {
      ret = versiony
        .to('_package.json')
        .to('_bower.json')
        .end()
    })

    it('should log a strip', function () {
      expect(logger.log).to.have.been.calledWith(strip)
    })

    it('should log a done message', function () {
      expect(logger.log).to.have.been.calledWith('Done. New version: 1.2.3-alpha.4')
    })

    it('should log a files updated message', function () {
      expect(logger.log).to.have.been.calledWith('Files updated:\n')
    })

    it('should log the first file updated', function () {
      expect(logger.log).to.have.been.calledWith('_package.json')
    })

    it('should log the second file updated', function () {
      expect(logger.log).to.have.been.calledWith('_bower.json')
    })

    it('return the correct info,', function () {
      expect(ret).to.eql({
        files: ['_package.json', '_bower.json'],
        version: '1.2.3-alpha.4'
      })
    })
  })

  describe('when called with quiet flag (and some files updated)', function () {
    let ret
    beforeEach(function () {
      ret = versiony
        .to('_package.json')
        .to('_bower.json')
        .end({quiet: true})
    })

    it('should not log anything', function () {
      expect(logger.log).to.have.callCount(0)
    })

    it('return the correct info,', function () {
      expect(ret).to.eql({
        files: ['_package.json', '_bower.json'],
        version: '1.2.3-alpha.4'
      })
    })
  })
})
