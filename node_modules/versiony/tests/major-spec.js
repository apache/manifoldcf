/**
 * Specify behavior for the .major() API interface
 */

const expect = require('chai').expect

const versiony = require('../lib/index')
const {createFileWithVersion, deleteFile, getVersion, itShouldBecome} = require('./utils')

describe('.major()', function () {
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

    describe('when calling .major()', function () {
      let ret
      beforeEach(function () {
        ret = v.major()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '2.2.3')

      it('should return itself', function () {
        expect(ret).to.equal(v)
      })
    })

    describe('when calling .major(6)', function () {
      beforeEach(function () {
        v.major(6)
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '6.2.3')
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

    describe('when calling .major()', function () {
      beforeEach(function () {
        v.major()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '2.2.3-alpha.4')
    })

    describe('when calling .major(6)', function () {
      beforeEach(function () {
        v.major(6)
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '6.2.3-alpha.4')
    })
  })
})
