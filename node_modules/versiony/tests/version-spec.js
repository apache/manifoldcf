/**
 * Specify behavior for the .version() API interface
 */
const expect = require('chai').expect

const versiony = require('../lib/index')
const {createFileWithVersion, deleteFile, getVersion, itShouldBecome} = require('./utils')

describe('.version()', function () {
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

    describe('when calling .version(\'4.5.6\')', function () {
      let ret
      beforeEach(function () {
        ret = v.version('4.5.6')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '4.5.6')

      it('should return itself', function () {
        expect(ret).to.equal(versiony)
      })
    })

    describe('when calling .version(\'10.11.12-beta.13\')', function () {
      beforeEach(function () {
        v.version('10.11.12-beta.13')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '10.11.12-beta.13')
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

    describe('when calling .version(\'4.5.6\')', function () {
      beforeEach(function () {
        v.version('4.5.6')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '4.5.6')
    })

    describe('when calling .version(\'10.11.12-beta.13\')', function () {
      beforeEach(function () {
        v.version('10.11.12-beta.13')
        return getVersion(ctx)
      })

      itShouldBecome(ctx, '10.11.12-beta.13')
    })
  })
})
