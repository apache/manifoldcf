/**
 * Specify behavior for the .get() API interface
 */

const chai = require('chai')
const sinonChai = require('sinon-chai')
const sinon = require('sinon')

chai.use(sinonChai)
const expect = chai.expect

const versiony = require('../lib/index')

describe('.get()', function () {
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
      ret = versiony.get()
    })

    it('should return the value string', function () {
      expect(ret).to.equal('1.2.3-alpha.4')
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
        .get()
    })

    it('should return the updated value string', function () {
      expect(ret).to.equal('2.3.4-alpha.5')
    })
  })
})
