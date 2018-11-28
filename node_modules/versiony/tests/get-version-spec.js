/**
 * Specify behavior for the .getVersion() API interface
 */

const chai = require('chai')
const sinonChai = require('sinon-chai')
const sinon = require('sinon')

chai.use(sinonChai)
const expect = chai.expect

const versiony = require('../lib/index')

describe('.getVersion()', function () {
  let sandbox
  beforeEach(function () {
    sandbox = sinon.sandbox.create()
    versiony.version('1.2.3-alpha.4')
  })

  afterEach(function () {
    sandbox.restore()
    versiony.model.reset()
  })

  describe('when there are no pending changes', function () {
    let ret
    beforeEach(function () {
      ret = versiony.getVersion()
    })

    it('should return the value', function () {
      expect(ret).to.eql({
        major: 1,
        minor: 2,
        patch: 3,
        preRelease: 'alpha.4'
      })
    })
  })

  describe('when there are pending changes', function () {
    let ret
    beforeEach(function () {
      ret = versiony
        .major()
        .minor()
        .patch()
        .preRelease()
        .getVersion()
    })

    it('should return the updated value string', function () {
      expect(ret).to.eql({
        major: 2,
        minor: 3,
        patch: 4,
        preRelease: 'alpha.5'
      })
    })
  })
})
