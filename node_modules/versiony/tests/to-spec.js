/**
 * Specify behavior for the .to() API interface
 */
const chai = require('chai')
const sinonChai = require('sinon-chai')
const sinon = require('sinon')

chai.use(sinonChai)
const expect = chai.expect

const versiony = require('../lib/index')
const utils = require('../lib/utils')

describe('.to()', function () {
  let sandbox
  beforeEach(function () {
    sandbox = sinon.sandbox.create()
    sandbox.stub(utils, 'readJsonFile').returns({foo: 'bar', version: '1.2.3'})
    sandbox.stub(utils, 'writeJsonFile')
  })

  afterEach(function () {
    versiony.model.reset()
    sandbox.restore()
  })

  describe('when eofNewLine and indent are set', function () {
    let ret
    beforeEach(function () {
      ret = versiony
        .eofNewline(true)
        .indent(4)
        .version('3.2.1')
        .to('_package.json')
    })

    it('should write out the new version', function () {
      expect(utils.writeJsonFile).to.have.been.calledWith(
        '_package.json',
        {foo: 'bar', version: '3.2.1'},
        4,
        true
      )
    })

    it('should return itself', function () {
      expect(ret).to.equal(versiony)
    })
  })
})
