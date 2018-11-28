js.augment
==========

A very light Javascript implementation of augment pattern.

## Node.js

### How to install ? 

```
npm install js.augment
```

### Example

```javascript
require('js.augment');

/**
 * Super class.
 * @class
 */ 
var Animal = Object.augment({
  /**
   * @constuctor
   * @memberof Animal
   * @param {string} name - The name of animal.
   */
  constructor: function(name) {
    if (typeof(name) === 'string') {
      this.name = name;
    } else {
      throw new Error('Argument <name> is not a string!');
    }
  },
  /**
   * Returns the name of animal.
   * @abstract
   * @memberof Animal
   */
  getName: function() {
    return this.name;
  },
  /** 
   * Returns class name.
   * @return {string} The class name.
   */
  getClassName: function() {
    return 'Animal';
  }
});


/**
 * Cat class.
 */
var Cat = Animal.augment({
  /**
   * @constuctor
   * @memberof Cat
   * @param {string} name - The name of cat.
   */
  constructor: function(name) {
    Animal.call(this, name); // Call super class.
  },
  /** 
   * Returns class name.
   * @return {string} The class name.
   */
  getClassName: function() {
    return 'Cat';
  }
});

/**
 * Entry point.
 */
var main = function() {
  cat = new Cat('le chat!!');
  console.log(cat.getName()); // print the name of cat. 
};

if (require.main === module) {
    main();
}

```

## Bower

### How to install ? 

<pre>
bower install js.augment
</pre>






