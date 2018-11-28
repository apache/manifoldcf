/**
 * Specify behavior for the .patch() API interface
 */
const expect = require('chai').expect

const versiony = require('../lib/index')
const {createFileWithVersion, deleteFile, getVersion, itShouldBecome} = require('./utils')

describe('.patch()', function () {
  afterEach(function () {
    versiony.model.reset()
  })

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

    describe('when calling .patch()', function () {
      let ret
      beforeEach(function () {
        ret = v.patch()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.4')

      it('should return itself', function () {
        expect(ret).to.equal(v)
      })
    })

    describe('when calling .patch(7)', function () {
      beforeEach(function () {
        v.patch(7)
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.7')
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

    describe('when calling .patch()', function () {
      beforeEach(function () {
        v.patch()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.4-alpha.4')
    })

    describe('when calling .patch(7)', function () {
      beforeEach(function () {
        v.patch(7)
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.7-alpha.4')
    })
  })
})
