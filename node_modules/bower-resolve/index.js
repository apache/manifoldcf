var path = require('path');
var fs = require('fs');
var path = require('path')
var bower = require('bower');
var bowerModules;

function readBowerModules(cb) {
  bower.commands.list({map: true},{offline: module.exports.offline})
    .on('end', function (map) {
      bowerModules = map;
      cb(null, map);
    });
}

function bowerRequire(moduleName, options) {
  if (typeof bowerModules === 'undefined') throw new Error('You must call the #init method first');
  if (moduleName && moduleName in bowerModules.dependencies) {
    var module = bowerModules.dependencies[moduleName];
    if (module) {
      var mainModule;
      var pkgMeta = module.pkgMeta;
      if (pkgMeta && pkgMeta.main) {
        mainModule = Array.isArray(pkgMeta.main) ? pkgMeta.main.filter(function (file) { return /\.js$/.test(file); })[0] : pkgMeta.main;
      } else {
        // if 'main' wasn't specified by this component, let's try
        // guessing that the main file is moduleName.js
        mainModule = moduleName + '.js';
      }
      var fullModulePath = path.resolve(path.join(module.canonicalDir, mainModule));
      return path.join(process.cwd(), path.relative(path.dirname(moduleName), fullModulePath));
    }
  }
}

function fastReadBowerModules(moduleArg, opts,  cb){

    //Seems hacky, but just wrap the async method. It increases code reuse and simplifies the library
    //and should be fine since the algorithm is typically rarely used and already relatively quick.

    setTimeout(function(){
        cb(bowerResolveSync(moduleArg, opts));
    }, 0)

}


function bowerResolveSync(moduleArg, opts){
    var opts = opts || {},
        moduleName = moduleArg,
        fileExts = opts.extensions || ['js'], //if no extension on moduleArg, assume javascript
        bowerDirRelPath = 'bower_components',
        found = false,
        basePath = opts.basedir ? path.resolve(process.cwd(), opts.basedir) : process.cwd(),
        pathAsArr = basePath.split(/[\\\/]/),
        returnPath,
        basePath;

    if(moduleName.split(/[\\\/]/).length > 1){
        throw new Error('Bower resolve cannot resolve relative paths. Please pass a single filename with an optional extension')
    }


    //If extension type is on module is present, change moduleName and force that extension
    if(containsExtension(moduleName, fileExts)){
        moduleName = moduleName.split('.').slice(0, -1).join('.');
        fileExts = [moduleName.split('.').pop()];
    }

    //traverse upwards checking for existence of bower identifiers at each level. Break when found
    while(pathAsArr.length){
        basePath = pathAsArr.join(path.sep);
        var files = fs.readdirSync(basePath)
        if(files.indexOf('bower.json') !== -1
            || files.indexOf('.bowerrc') !== -1
            || files.indexOf('bower_components') !== -1)
        {
            found = true;
            if(files.indexOf('.bowerrc') !== -1){
                var temp = fs.readFileSync(basePath + "/" + '.bowerrc');
                if(temp) bowerDirRelPath = JSON.parse(temp).directory || bowerDirRelPath;
            }
            break;
        }
        pathAsArr.pop();
    }

    if(found){
        //This is a niche case. Any consuming module must expect * to bower resolve to an array, not a string
        if(moduleName === "*"){
            returnPath = [];
            var modules = fs.readdirSync([basePath, bowerDirRelPath].join('/'))
            modules.forEach(function(thisModuleName){
                returnPath.push(getModulePath(thisModuleName))
            })

        } else{
            returnPath = getModulePath(moduleName);
        }

        function getModulePath(thisModuleName){
            var moduleConfig = fs.readFileSync([basePath, bowerDirRelPath, thisModuleName, 'bower.json'].join('/')),
                relFilePath = thisModuleName + "." + fileExts[0];

            if(moduleConfig){
                moduleConfig = JSON.parse(moduleConfig).main;
                if(typeof moduleConfig == 'object'){
                    var temp;
                    for(var j = 0; j < fileExts.length; j++){
                        temp = arrFind(moduleConfig, new RegExp("." + fileExts[j] + "$"));
                        if(temp){
                            relFilePath = temp;
                            break;
                        }
                    }

                } else if(typeof moduleConfig === 'string'){
                    relFilePath = moduleConfig;
                }
            }
            return path.join(basePath, bowerDirRelPath, thisModuleName, relFilePath);
        }
    }

    return returnPath
}


function arrFind(arr, test){
    for(var i = 0; i < arr.length; i++){
        if(test.test(arr[i]))
            return arr[i];
    }
    return null;
}

function containsExtension (moduleName, fileExts) {
  var splittedName = moduleName.split('.');
  var lastChunk = moduleName.split('.')[splittedName.length -1 ];

  return arrFind(fileExts, new RegExp("." + lastChunk + "$"));
}


module.exports = bowerRequire;
module.exports.init = readBowerModules;
module.exports.fastRead = fastReadBowerModules;
module.exports.fastReadSync = bowerResolveSync;
module.exports.offline = false;
