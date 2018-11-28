var expect = require('expect.js'),
    bowerResolve = require('..'),
    fs = require('fs'),
    path = require('path');

describe('bower-resolve', function() {


  it('should be able to resolve to a bower component', function(done) {
    this.timeout(5000);
    bowerResolve.init(function () {
      expect(bowerResolve('js-base64')).to.equal(path.join(process.cwd(), "vendor", "js-base64", "base64.js"));
      done();
    });
  });

  it('should be able to resolve to a bower component with dot in it\'s name', function(done) {
    this.timeout(5000);
    bowerResolve.init(function () {
      expect(bowerResolve('js.augment')).to.equal(path.join(process.cwd(), "vendor", "js.augment", "lib/augment.js"));
      done();
    });
  });

  it('should fast resolve a bower component', function(done) {

      this.timeout(5000);
      bowerResolve.fastRead("js-base64", {basedir: process.cwd() + "/test"}, function (pathName) {
          expect(pathName).to.equal(path.join(process.cwd(), "vendor", "js-base64", "base64.js"));
          done();
      });
  });
});
