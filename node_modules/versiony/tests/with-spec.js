/**
 * Specify behavior for the .with() API interface
 */

const chai = require('chai')
const sinonChai = require('sinon-chai')
const sinon = require('sinon')

chai.use(sinonChai)
const expect = chai.expect

const versiony = require('../lib/index')

describe('.with()', function () {
  let sandbox, ret
  beforeEach(function () {
    sandbox = sinon.sandbox.create()
    sandbox.stub(versiony, 'from').returnsThis()
    sandbox.stub(versiony, 'to').returnsThis()

    ret = versiony.with('_package.json')
  })

  afterEach(function () {
    sandbox.restore()
    versiony.model.reset()
  })

  it('should call .from() with the argument passed in', function () {
    expect(versiony.from).to.have.been.calledWith('_package.json')
  })

  it('should call .to() with no argument', function () {
    expect(versiony.to).to.have.been.calledWith()
  })

  it('return itself,', function () {
    expect(ret).to.equal(versiony)
  })
})
