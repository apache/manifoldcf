/**
 * Specify behavior for the .newMinor() API interface
 */
const expect = require('chai').expect

const versiony = require('../lib/index')
const {createFileWithVersion, deleteFile, getVersion, itShouldBecome} = require('./utils')

describe('.newMinor()', function () {
  describe('starting from 1.2.3', function () {
    const ctx = {}
    let filename, v
    beforeEach(function () {
      ctx.filename = filename = '_package.json'
      return createFileWithVersion(filename, '1.2.3')
        .then(() => {
          ctx.v = v = versiony.from(filename)
        })
    })

    afterEach(function () {
      return deleteFile(filename)
    })

    describe('when calling .newMinor()', function () {
      let ret
      beforeEach(function () {
        ret = v.newMinor()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.3.0')

      it('should return itself', function () {
        expect(ret).to.equal(v)
      })
    })
  })

  describe('starting from 1.2.3-alpha.4', function () {
    const ctx = {}
    let filename, v
    beforeEach(function () {
      ctx.filename = filename = '_package.json'
      return createFileWithVersion(filename, '1.2.3-alpha.4')
        .then(() => {
          ctx.v = v = versiony.from(filename).indent(' '.repeat(2))
        })
    })

    afterEach(function () {
      return deleteFile(filename)
    })

    describe('when calling .newMinor()', function () {
      beforeEach(function () {
        v.newMinor()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.3.0')
    })
  })
})
