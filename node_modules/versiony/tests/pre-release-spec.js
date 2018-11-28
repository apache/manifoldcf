/**
 * Specify behavior for the .preRelease() API interface
 */
const expect = require('chai').expect

const versiony = require('../lib/index')
const {createFileWithVersion, deleteFile, getVersion, itShouldBecome} = require('./utils')

describe('.preRelease()', function () {
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

    describe('when calling .preRelease()', function () {
      let ret
      beforeEach(function () {
        ret = v.preRelease()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3-beta.1')

      it('should return itself', function () {
        expect(ret).to.equal(v)
      })
    })

    describe('when calling .preRelease(\'alpha.13\')', function () {
      beforeEach(function () {
        v.preRelease('alpha.13')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3-alpha.13')
    })

    describe('when calling .preRelease(\'\')', function () {
      beforeEach(function () {
        v.preRelease('')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3')
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

    describe('when calling .preRelease()', function () {
      beforeEach(function () {
        v.preRelease()
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3-alpha.5')
    })

    describe('when calling .preRelease(\'alpha.13\')', function () {
      beforeEach(function () {
        v.preRelease('alpha.13')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3-alpha.13')
    })

    describe('when calling .preRelease(\'\')', function () {
      beforeEach(function () {
        v.preRelease('')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '1.2.3')
    })
  })
})
