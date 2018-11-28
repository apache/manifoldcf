/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 9 Février

 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
'use strict;';

/**
 * Augment function.
 * @param {object} body - Provided body to augment object.
 */
Function.prototype.augment = function (body) {
    var fn;
    if (typeof(body) === 'object') {
        fn = function() {
            for (var attr in body) this[attr] = body[attr];
            if (!body.hasOwnProperty('constructor')) {
                this.constructor = function() {};
            };
        };
    } else {
        fn = body;
    };
    var base = this.prototype;
    var prototype = Object.create(base);
    fn.apply(prototype, Array.from(arguments, 1).concat(base));
    if (!Object.ownPropertyOf(prototype, 'constructor')) return prototype;
    var constructor = prototype.constructor;
    constructor.prototype = prototype;
    return constructor;
};

/**
 * Register augment function.
 */
(function funct() {
    var bind = funct.bind;
    var bindable = Function.bindable = bind.bind(bind);
    var callable = Function.callable = bindable(funct.call);
    Object.ownPropertyOf = callable(funct.hasOwnProperty);
    Array.from = callable([].slice);
}());